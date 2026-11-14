package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.ClusterSettings
import com.example.data.model.CloudAgent
import com.example.data.model.OperationLog
import com.example.data.model.OrchestratedTask
import com.example.data.model.PerformanceReport
import com.example.data.model.SystemAlert
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MeshRepository(
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
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    fun observeAgents(uid: String): Flow<List<CloudAgent>> {
        val path = "users/$uid/agents"
        return userDoc(uid).collection("agents")
            .orderBy("priority", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudAgent::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    fun observeTasks(uid: String): Flow<List<OrchestratedTask>> {
        val path = "users/$uid/tasks"
        return userDoc(uid).collection("tasks")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    OrchestratedTask::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    fun observeLogs(uid: String): Flow<List<OperationLog>> {
        val path = "users/$uid/logs"
        return userDoc(uid).collection("logs")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    OperationLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    fun observeAlerts(uid: String): Flow<List<SystemAlert>> {
        val path = "users/$uid/alerts"
        return userDoc(uid).collection("alerts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SystemAlert::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    fun observeSettings(uid: String): Flow<ClusterSettings?> {
        val path = "users/$uid/settings/cluster"
        return userDoc(uid).collection("settings").document("cluster")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        ClusterSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else null
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.GET, path, uid)
                throw error
            }
    }

    fun observeReports(uid: String): Flow<List<PerformanceReport>> {
        val path = "users/$uid/reports"
        return userDoc(uid).collection("reports")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    PerformanceReport::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun createAgent(agent: CloudAgent, customId: String? = null): String {
        val uid = requireUserId()
        val docId = customId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val docRef = userDoc(uid).collection("agents").document(docId)
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

    suspend fun updateAgentStatusAndLoad(
        agentId: String,
        status: String,
        currentLoadPct: Long? = null,
        tasksCompletedDelta: Long? = null,
        failoversHandledDelta: Long? = null,
        avgLatencyMs: Long? = null
    ) {
        val uid = requireUserId()
        require(agentId.isNotBlank())
        val docRef = userDoc(uid).collection("agents").document(agentId)
        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (currentLoadPct != null) updates["currentLoadPct"] = currentLoadPct.coerceIn(0L, 100L)
        if (tasksCompletedDelta != null && tasksCompletedDelta != 0L) {
            updates["tasksCompleted"] = FieldValue.increment(tasksCompletedDelta)
        }
        if (failoversHandledDelta != null && failoversHandledDelta != 0L) {
            updates["failoversHandled"] = FieldValue.increment(failoversHandledDelta)
        }
        if (avgLatencyMs != null) updates["avgLatencyMs"] = avgLatencyMs.coerceAtLeast(0L)

        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun updateAgentConfig(
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
        require(agentId.isNotBlank())
        val docRef = userDoc(uid).collection("agents").document(agentId)
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

    suspend fun updateAgentPriority(agentId: String, priority: Long) {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("agents").document(agentId)
        try {
            docRef.update(
                mapOf(
                    "priority" to priority.coerceIn(1L, 10L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun deleteAgent(agentId: String) {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("agents").document(agentId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    suspend fun createTask(task: OrchestratedTask, customId: String? = null): String {
        val uid = requireUserId()
        val docId = customId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val docRef = userDoc(uid).collection("tasks").document(docId)
        val payload = task.copy(
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

    suspend fun updateTaskProgress(
        taskId: String,
        status: String,
        assignedAgentId: String? = null,
        assignedAgentName: String? = null,
        failoverFromAgentName: String? = null,
        failoverCount: Long? = null,
        latencyMs: Long? = null,
        resultSummary: String? = null,
        checkpointNote: String? = null
    ) {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("tasks").document(taskId)
        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (assignedAgentId != null) updates["assignedAgentId"] = assignedAgentId
        if (assignedAgentName != null) updates["assignedAgentName"] = assignedAgentName
        if (failoverFromAgentName != null) updates["failoverFromAgentName"] = failoverFromAgentName
        if (failoverCount != null) updates["failoverCount"] = failoverCount
        if (latencyMs != null) updates["latencyMs"] = latencyMs
        if (resultSummary != null) updates["resultSummary"] = resultSummary.take(8000)
        if (checkpointNote != null) updates["checkpointNote"] = checkpointNote.take(500)

        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun addLog(log: OperationLog, customId: String? = null): String {
        val uid = requireUserId()
        val docId = customId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val docRef = userDoc(uid).collection("logs").document(docId)
        val payload = log.copy(
            id = "",
            userId = uid,
            createdAt = null
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

    suspend fun addAlert(alert: SystemAlert, customId: String? = null): String {
        val uid = requireUserId()
        val docId = customId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val docRef = userDoc(uid).collection("alerts").document(docId)
        val payload = alert.copy(
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

    suspend fun acknowledgeAlert(alertId: String) {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("alerts").document(alertId)
        try {
            docRef.update(
                mapOf(
                    "isAcknowledged" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun deleteAlert(alertId: String) {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("alerts").document(alertId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    suspend fun ensureDefaultSettings(uid: String, defaultToken: String): ClusterSettings {
        val docRef = userDoc(uid).collection("settings").document("cluster")
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                return existing.toObject(
                    ClusterSettings::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: ClusterSettings(userId = uid, apiToken = defaultToken)
            }
            val initial = ClusterSettings(
                id = "",
                userId = uid,
                apiToken = defaultToken,
                createdAt = null,
                updatedAt = null
            )
            docRef.set(initial).await()
            return initial.copy(id = "cluster", createdAt = Timestamp.now(), updatedAt = Timestamp.now())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
        }
    }

    suspend fun updateSettings(settings: ClusterSettings) {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("settings").document("cluster")
        val updates = mapOf(
            "loadBalanceStrategy" to settings.loadBalanceStrategy,
            "autoFailoverEnabled" to settings.autoFailoverEnabled,
            "heartbeatIntervalSec" to settings.heartbeatIntervalSec.coerceIn(5L, 300L),
            "failoverTimeoutMs" to settings.failoverTimeoutMs.coerceIn(500L, 30000L),
            "highLoadThresholdPct" to settings.highLoadThresholdPct.coerceIn(50L, 99L),
            "instantAlertsEnabled" to settings.instantAlertsEnabled,
            "webhookUrl" to settings.webhookUrl.trim().take(300),
            "apiToken" to settings.apiToken.trim().take(128),
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

    suspend fun saveReport(report: PerformanceReport, customId: String? = null): String {
        val uid = requireUserId()
        val docId = customId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val docRef = userDoc(uid).collection("reports").document(docId)
        val payload = report.copy(
            id = "",
            userId = uid,
            createdAt = null
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

    suspend fun deleteReport(reportId: String) {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("reports").document(reportId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        opType: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, opType, path, auth)
        }
    }
}
