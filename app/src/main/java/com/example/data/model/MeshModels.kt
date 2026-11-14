package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

object AgentProviders {
    const val GOOGLE_CLOUD = "GOOGLE_CLOUD"
    const val VERTEX_AI = "VERTEX_AI"
    const val AWS_BEDROCK = "AWS_BEDROCK"
    const val AZURE_AI = "AZURE_AI"
    const val CUSTOM_ENDPOINT = "CUSTOM_ENDPOINT"

    val all = listOf(GOOGLE_CLOUD, VERTEX_AI, AWS_BEDROCK, AZURE_AI, CUSTOM_ENDPOINT)

    fun displayName(provider: String): String = when (provider) {
        GOOGLE_CLOUD -> "Google Cloud AI"
        VERTEX_AI -> "Vertex AI Mesh"
        AWS_BEDROCK -> "AWS Cloud Bridge"
        AZURE_AI -> "Azure AI Foundry"
        CUSTOM_ENDPOINT -> "Custom REST Node"
        else -> provider
    }
}

object AgentStatus {
    const val ACTIVE = "ACTIVE"
    const val DEGRADED = "DEGRADED"
    const val OFFLINE = "OFFLINE"
    const val RECOVERING = "RECOVERING"

    fun labelAr(status: String): String = when (status) {
        ACTIVE -> "متصل ونشط"
        DEGRADED -> "أداء منخفض"
        OFFLINE -> "متوقف"
        RECOVERING -> "جاري التعافي"
        else -> status
    }
}

object TaskCategories {
    const val ORCHESTRATION = "ORCHESTRATION"
    const val ANALYSIS = "ANALYSIS"
    const val CODE_GEN = "CODE_GEN"
    const val SECURITY_AUDIT = "SECURITY_AUDIT"
    const val REPORTING = "REPORTING"

    val all = listOf(ORCHESTRATION, ANALYSIS, CODE_GEN, SECURITY_AUDIT, REPORTING)

    fun labelAr(category: String): String = when (category) {
        ORCHESTRATION -> "تنسيق سحابي"
        ANALYSIS -> "تحليل بيانات"
        CODE_GEN -> "هندسة برمجية"
        SECURITY_AUDIT -> "تدقيق أمني"
        REPORTING -> "تقرير أداء"
        else -> category
    }
}

object TaskStatus {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val FAILING_OVER = "FAILING_OVER"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"

    fun labelAr(status: String): String = when (status) {
        QUEUED -> "في الانتظار"
        RUNNING -> "قيد التنفيذ"
        FAILING_OVER -> "تحول تلقائي"
        COMPLETED -> "مكتمل"
        FAILED -> "فشل"
        else -> status
    }
}

object LogEventTypes {
    const val TASK_STARTED = "TASK_STARTED"
    const val TASK_COMPLETED = "TASK_COMPLETED"
    const val FAILOVER_TRIGGERED = "FAILOVER_TRIGGERED"
    const val AGENT_CONNECTED = "AGENT_CONNECTED"
    const val AGENT_DISCONNECTED = "AGENT_DISCONNECTED"
    const val PRIORITY_UPDATED = "PRIORITY_UPDATED"
    const val WEBHOOK_DISPATCHED = "WEBHOOK_DISPATCHED"
    const val REPORT_GENERATED = "REPORT_GENERATED"
    const val ERROR = "ERROR"
}

object LoadBalanceStrategies {
    const val PRIORITY_FIRST = "PRIORITY_FIRST"
    const val LEAST_LOAD = "LEAST_LOAD"
    const val ROUND_ROBIN = "ROUND_ROBIN"

    val all = listOf(PRIORITY_FIRST, LEAST_LOAD, ROUND_ROBIN)

    fun labelAr(strategy: String): String = when (strategy) {
        PRIORITY_FIRST -> "الأولوية القصوى أولاً"
        LEAST_LOAD -> "أقل حمل حسابي"
        ROUND_ROBIN -> "توزيع دائري متوازن"
        else -> strategy
    }
}

@IgnoreExtraProperties
data class CloudAgent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val provider: String = AgentProviders.VERTEX_AI,
    val googleAccountEmail: String = "",
    val region: String = "us-central1",
    val modelName: String = "gemini-3.8-flash",
    val role: String = "Primary Reasoning",
    val status: String = AgentStatus.ACTIVE,
    val priority: Long = 5L,
    val currentLoadPct: Long = 15L,
    val maxConcurrency: Long = 10L,
    val tasksCompleted: Long = 0L,
    val failoversHandled: Long = 0L,
    val avgLatencyMs: Long = 140L,
    val autoFailoverEnabled: Boolean = true,
    val endpointUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class OrchestratedTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val prompt: String = "",
    val category: String = TaskCategories.ORCHESTRATION,
    val status: String = TaskStatus.QUEUED,
    val assignedAgentId: String = "",
    val assignedAgentName: String = "",
    val failoverFromAgentName: String = "",
    val failoverCount: Long = 0L,
    val latencyMs: Long = 0L,
    val resultSummary: String = "",
    val checkpointNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class OperationLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val eventType: String = LogEventTypes.TASK_STARTED,
    val agentId: String = "",
    val agentName: String = "",
    val taskId: String = "",
    val message: String = "",
    val metadataJson: String = "{}",
    val latencyMs: Long = 0L,
    val severity: String = "INFO",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SystemAlert(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val severity: String = "INFO",
    val sourceAgentName: String = "",
    val isAcknowledged: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ClusterSettings(
    @DocumentId val id: String = "cluster",
    val userId: String = "",
    val loadBalanceStrategy: String = LoadBalanceStrategies.PRIORITY_FIRST,
    val autoFailoverEnabled: Boolean = true,
    val heartbeatIntervalSec: Long = 15L,
    val failoverTimeoutMs: Long = 3000L,
    val highLoadThresholdPct: Long = 85L,
    val instantAlertsEnabled: Boolean = true,
    val webhookUrl: String = "",
    val apiToken: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PerformanceReport(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val periodLabel: String = "",
    val executiveSummary: String = "",
    val recommendations: String = "",
    val healthScore: Long = 100L,
    val totalTasksAnalyzed: Long = 0L,
    val totalFailoversAnalyzed: Long = 0L,
    val avgLatencyMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)
