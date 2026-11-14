package com.example.data.manager

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.AgentStatus
import com.example.data.model.CloudAgent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class CloudSyncMetadata(
    val isFromCache: Boolean = false,
    val hasPendingWrites: Boolean = false,
    val lastSyncEpochMs: Long = 0L
)

data class SyncedAgentsSnapshot(
    val agents: List<CloudAgent> = emptyList(),
    val syncMetadata: CloudSyncMetadata = CloudSyncMetadata()
)

/**
 * Manages real-time cloud agent configurations and multi-device synchronization
 * using the provisioned Cloud Firestore enterprise database.
 */
class FirestoreSyncManager(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before syncing cloud agents.")
    }

    private fun agentsCollection(uid: String) =
        db.collection("users").document(uid).collection("agents")

    /**
     * Observes cloud agent configurations in real-time across devices, including
     * cache/pending-write sync metadata so the UI reflects exact multi-device sync state.
     */
    fun observeAgentsWithSyncStatus(uid: String): Flow<SyncedAgentsSnapshot> {
        val path = "users/$uid/agents"
        return agentsCollection(uid)
            .orderBy("priority", Query.Direction.DESCENDING)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                val agents = snapshot.toObjects(
                    CloudAgent::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                SyncedAgentsSnapshot(
                    agents = agents,
                    syncMetadata = CloudSyncMetadata(
                        isFromCache = snapshot.metadata.isFromCache,
                        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                        lastSyncEpochMs = System.currentTimeMillis()
                    )
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(toLog, OperationType.LIST, path, auth)
                }
                throw error
            }
    }

    /**
     * Stores a new cloud agent configuration and links it to a Google account.
     */
    suspend fun addCloudAccount(agent: CloudAgent, customId: String? = null): String {
        val uid = requireUserId()
        val docId = customId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val docRef = agentsCollection(uid).document(docId)
        val payload = agent.copy(
            id = "",
            userId = uid,
            createdAt = null,
            updatedAt = null
        )
        try {
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    /**
     * Updates an existing cloud agent configuration and syncs across devices.
     */
    suspend fun updateCloudConfiguration(
        agentId: String,
        name: String,
        googleAccountEmail: String,
        priority: Long,
        maxConcurrency: Long,
        autoFailoverEnabled: Boolean,
        role: String,
        endpointUrl: String
    ) {
        val uid = requireUserId()
        require(agentId.isNotBlank()) { "Agent ID must not be blank" }
        val docRef = agentsCollection(uid).document(agentId)
        val updates = mapOf(
            "name" to name.trim().take(60),
            "googleAccountEmail" to googleAccountEmail.trim().take(120),
            "priority" to priority.coerceIn(1L, 10L),
            "maxConcurrency" to maxConcurrency.coerceIn(1L, 100L),
            "autoFailoverEnabled" to autoFailoverEnabled,
            "role" to role.trim().take(60),
            "endpointUrl" to endpointUrl.trim().take(250),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    /**
     * Updates the online/offline status of a connected cloud agent in real-time.
     */
    suspend fun setAgentOnlineStatus(agentId: String, isOnline: Boolean) {
        val uid = requireUserId()
        require(agentId.isNotBlank()) { "Agent ID must not be blank" }
        val docRef = agentsCollection(uid).document(agentId)
        val updates = mapOf(
            "status" to if (isOnline) AgentStatus.ACTIVE else AgentStatus.OFFLINE,
            "currentLoadPct" to if (isOnline) 14L else 0L,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    /**
     * Removes a connected cloud account configuration from Firestore.
     */
    suspend fun removeCloudAccount(agentId: String) {
        val uid = requireUserId()
        require(agentId.isNotBlank()) { "Agent ID must not be blank" }
        val docRef = agentsCollection(uid).document(agentId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }
}
