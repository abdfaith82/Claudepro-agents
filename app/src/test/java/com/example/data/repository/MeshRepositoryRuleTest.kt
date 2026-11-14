package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AgentProviders
import com.example.data.model.AgentStatus
import com.example.data.model.CloudAgent
import com.example.data.model.LogEventTypes
import com.example.data.model.OperationLog
import com.example.data.model.OrchestratedTask
import com.example.data.model.TaskCategories
import com.example.data.model.TaskStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MeshRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveAgent_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = MeshRepository(firestore, auth)
        val agentId = "agent_${UUID.randomUUID().toString().replace("-", "")}"

        val createdId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createAgent(
                CloudAgent(
                    name = "Vertex Primary",
                    provider = AgentProviders.VERTEX_AI,
                    googleAccountEmail = ALICE_EMAIL,
                    region = "us-central1",
                    modelName = "gemini-3.8-flash",
                    role = "Primary Orchestrator",
                    status = AgentStatus.ACTIVE,
                    priority = 9L
                ),
                customId = agentId
            )
        }
        assertEquals(agentId, createdId)

        val agents = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAgents(uid).first { list -> list.any { it.id == agentId } }
        }
        assertTrue(agents.any { it.id == agentId && it.name == "Vertex Primary" })

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateAgentPriority(agentId, 10L)
            repository.updateAgentStatusAndLoad(agentId, AgentStatus.OFFLINE, currentLoadPct = 0L)
            repository.deleteAgent(agentId)
        }
    }

    @Test
    fun createTaskAndAppendLog_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = MeshRepository(firestore, auth)
        val taskId = "task_${UUID.randomUUID().toString().replace("-", "")}"
        val logId = "log_${UUID.randomUUID().toString().replace("-", "")}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTask(
                OrchestratedTask(
                    title = "Audit Mesh Failover",
                    prompt = "Verify failover latency",
                    category = TaskCategories.ORCHESTRATION,
                    status = TaskStatus.RUNNING,
                    assignedAgentId = "agent_1",
                    assignedAgentName = "Vertex Primary"
                ),
                customId = taskId
            )
            repository.updateTaskProgress(
                taskId = taskId,
                status = TaskStatus.COMPLETED,
                latencyMs = 210L,
                resultSummary = "Completed cleanly"
            )
            repository.addLog(
                OperationLog(
                    eventType = LogEventTypes.TASK_COMPLETED,
                    agentId = "agent_1",
                    agentName = "Vertex Primary",
                    taskId = taskId,
                    message = "Task completed after check",
                    latencyMs = 210L,
                    severity = "SUCCESS"
                ),
                customId = logId
            )
        }

        val logs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeLogs(uid).first { list -> list.any { it.id == logId } }
        }
        assertTrue(logs.any { it.id == logId })
    }

    @Test
    fun observeAgents_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = MeshRepository(firestore, auth)
        val agentId = "agent_${UUID.randomUUID().toString().replace("-", "")}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createAgent(
                CloudAgent(
                    name = "Alice Private Node",
                    provider = AgentProviders.GOOGLE_CLOUD,
                    googleAccountEmail = ALICE_EMAIL
                ),
                customId = agentId
            )
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = MeshRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeAgents(aliceUid).first()
            }
        } catch (e: Exception) {
            caught = e
        }
        assertNotNull("Expected PERMISSION_DENIED exception", caught)
        val fsEx = caught!!.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caught", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
    }

    @Test
    fun observeAgents_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = MeshRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeAgents("some_user_123").first()
            }
        } catch (e: Exception) {
            caught = e
        }
        assertNotNull("Expected PERMISSION_DENIED exception", caught)
        val fsEx = caught!!.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caught", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice.mesh@test.com"
        const val BOB_EMAIL = "bob.mesh@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
