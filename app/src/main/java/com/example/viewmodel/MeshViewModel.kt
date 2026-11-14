package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.MeshAiService
import com.example.data.manager.CloudSyncMetadata
import com.example.data.manager.FirestoreSyncManager
import com.example.data.model.AgentStatus
import com.example.data.model.CloudAgent
import com.example.data.model.ClusterSettings
import com.example.data.model.LoadBalanceStrategies
import com.example.data.model.LogEventTypes
import com.example.data.model.OperationLog
import com.example.data.model.OrchestratedTask
import com.example.data.model.PerformanceReport
import com.example.data.model.SystemAlert
import com.example.data.model.TaskStatus
import com.example.data.repository.MeshRepository
import com.example.network.WebhookDispatchResult
import com.example.network.WebhookDispatcher
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class LiveExecutionState(
    val isRunning: Boolean = false,
    val taskId: String = "",
    val activeAgentName: String = "",
    val failoverTriggered: Boolean = false,
    val failedAgentName: String = "",
    val streamedOutput: String = "",
    val statusLabel: String = "",
    val errorMessage: String? = null
)

data class InstantNotificationEvent(
    val id: Int,
    val title: String,
    val body: String
)

class MeshViewModel(
    private val repository: MeshRepository,
    private val syncManager: FirestoreSyncManager,
    private val currentUserId: String,
    private val currentUserEmail: String,
    private val aiService: MeshAiService = MeshAiService(),
    private val webhookDispatcher: WebhookDispatcher = WebhookDispatcher()
) : ViewModel() {

    private var roundRobinCursor = 0

    private val _syncMetadata = MutableStateFlow(CloudSyncMetadata())
    val syncMetadata: StateFlow<CloudSyncMetadata> = _syncMetadata.asStateFlow()

    val agentsState: StateFlow<UiState<List<CloudAgent>>> = syncManager.observeAgentsWithSyncStatus(currentUserId)
        .onEach { synced -> _syncMetadata.value = synced.syncMetadata }
        .map<_, UiState<List<CloudAgent>>> { synced -> UiState.Success(synced.agents) }
        .catch { error -> emit(UiState.Error(extractFirestoreMessage(error, "فشل مزامنة الوكلاء السحابيين"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val tasksState: StateFlow<UiState<List<OrchestratedTask>>> = repository.observeTasks(currentUserId)
        .map<List<OrchestratedTask>, UiState<List<OrchestratedTask>>> { UiState.Success(it) }
        .catch { error -> emit(UiState.Error(extractFirestoreMessage(error, "فشل مزامنة المهام"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val logsState: StateFlow<UiState<List<OperationLog>>> = repository.observeLogs(currentUserId)
        .map<List<OperationLog>, UiState<List<OperationLog>>> { UiState.Success(it) }
        .catch { error -> emit(UiState.Error(extractFirestoreMessage(error, "فشل تحميل سجل العمليات"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val alertsState: StateFlow<UiState<List<SystemAlert>>> = repository.observeAlerts(currentUserId)
        .map<List<SystemAlert>, UiState<List<SystemAlert>>> { UiState.Success(it) }
        .catch { error -> emit(UiState.Error(extractFirestoreMessage(error, "فشل مزامنة التنبيهات"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val settingsState: StateFlow<ClusterSettings> = repository.observeSettings(currentUserId)
        .map { it ?: ClusterSettings(userId = currentUserId, apiToken = generateToken()) }
        .catch { error ->
            Log.w(TAG, "Settings observation error", error)
            emit(ClusterSettings(userId = currentUserId, apiToken = generateToken()))
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            ClusterSettings(userId = currentUserId)
        )

    val reportsState: StateFlow<UiState<List<PerformanceReport>>> = repository.observeReports(currentUserId)
        .map<List<PerformanceReport>, UiState<List<PerformanceReport>>> { UiState.Success(it) }
        .catch { error -> emit(UiState.Error(extractFirestoreMessage(error, "فشل تحميل التقارير"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    private val _liveExecution = MutableStateFlow(LiveExecutionState())
    val liveExecution: StateFlow<LiveExecutionState> = _liveExecution.asStateFlow()

    private val _isGeneratingReport = MutableStateFlow(false)
    val isGeneratingReport: StateFlow<Boolean> = _isGeneratingReport.asStateFlow()

    private val _webhookResult = MutableStateFlow<WebhookDispatchResult?>(null)
    val webhookResult: StateFlow<WebhookDispatchResult?> = _webhookResult.asStateFlow()

    private val _isTestingWebhook = MutableStateFlow(false)
    val isTestingWebhook: StateFlow<Boolean> = _isTestingWebhook.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _notificationEvent = MutableSharedFlow<InstantNotificationEvent>(extraBufferCapacity = 8)
    val notificationEvent: SharedFlow<InstantNotificationEvent> = _notificationEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            runCatching {
                repository.ensureDefaultSettings(currentUserId, generateToken())
            }.onFailure { e ->
                Log.w(TAG, "Failed to initialize default cluster settings", e)
            }
        }
    }

    fun addCloudAgent(
        name: String,
        provider: String,
        googleAccountEmail: String,
        region: String,
        modelName: String,
        role: String,
        priority: Long,
        maxConcurrency: Long,
        autoFailoverEnabled: Boolean,
        endpointUrl: String
    ) {
        viewModelScope.launch {
            try {
                val agent = CloudAgent(
                    userId = currentUserId,
                    name = name.trim().take(60),
                    provider = provider,
                    googleAccountEmail = googleAccountEmail.trim().ifBlank { currentUserEmail }.take(120),
                    region = region.trim().ifBlank { "us-central1" }.take(40),
                    modelName = modelName.trim().ifBlank { "gemini-3.8-flash" }.take(60),
                    role = role.trim().ifBlank { "General Orchestrator" }.take(60),
                    status = AgentStatus.ACTIVE,
                    priority = priority.coerceIn(1L, 10L),
                    currentLoadPct = 10L,
                    maxConcurrency = maxConcurrency.coerceIn(1L, 100L),
                    autoFailoverEnabled = autoFailoverEnabled,
                    endpointUrl = endpointUrl.trim().take(250)
                )
                val id = syncManager.addCloudAccount(agent)
                repository.addLog(
                    OperationLog(
                        eventType = LogEventTypes.AGENT_CONNECTED,
                        agentId = id,
                        agentName = agent.name,
                        message = "تم ربط الوكيل ${agent.name} بحساب Google (${agent.googleAccountEmail}) وانضمامه للشبكة السحابية.",
                        metadataJson = JSONObject(
                            mapOf("provider" to agent.provider, "region" to agent.region, "priority" to agent.priority)
                        ).toString(),
                        latencyMs = 85L,
                        severity = "SUCCESS"
                    )
                )
                _snackbarMessage.tryEmit("تم ربط الوكيل ${agent.name} بالشبكة السحابية")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add cloud agent", e)
                _snackbarMessage.tryEmit(e.localizedMessage ?: "تعذر إضافة الوكيل")
            }
        }
    }

    fun updateAgentPriority(agent: CloudAgent, newPriority: Long) {
        viewModelScope.launch {
            try {
                val clamped = newPriority.coerceIn(1L, 10L)
                repository.updateAgentPriority(agent.id, clamped)
                repository.addLog(
                    OperationLog(
                        eventType = LogEventTypes.PRIORITY_UPDATED,
                        agentId = agent.id,
                        agentName = agent.name,
                        message = "تم تعديل أولوية المهام للوكيل ${agent.name} إلى $clamped/10.",
                        metadataJson = "{\"priority\":$clamped}",
                        severity = "INFO"
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("تعذر تحديث الأولوية")
            }
        }
    }

    fun updateAgentConfig(
        agentId: String,
        name: String,
        googleAccountEmail: String,
        priority: Long,
        maxConcurrency: Long,
        autoFailoverEnabled: Boolean,
        role: String,
        endpointUrl: String
    ) {
        viewModelScope.launch {
            try {
                syncManager.updateCloudConfiguration(
                    agentId = agentId,
                    name = name,
                    googleAccountEmail = googleAccountEmail,
                    priority = priority,
                    maxConcurrency = maxConcurrency,
                    autoFailoverEnabled = autoFailoverEnabled,
                    role = role,
                    endpointUrl = endpointUrl
                )
                repository.addLog(
                    OperationLog(
                        eventType = LogEventTypes.PRIORITY_UPDATED,
                        agentId = agentId,
                        agentName = name,
                        message = "تم تحديث إعدادات الوكيل $name وأولوية التنفيذ ($priority/10).",
                        severity = "INFO"
                    )
                )
                _snackbarMessage.tryEmit("تم حفظ إعدادات الوكيل $name")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.localizedMessage ?: "فشل تحديث الوكيل")
            }
        }
    }

    fun toggleAgentStatus(agent: CloudAgent) {
        viewModelScope.launch {
            try {
                val nextOnline = !(agent.status == AgentStatus.ACTIVE || agent.status == AgentStatus.DEGRADED)
                syncManager.setAgentOnlineStatus(agent.id, nextOnline)

                if (!nextOnline) {
                    val standby = findBackupAgent(excludingId = agent.id)
                    val failoverNote = if (standby != null) {
                        "تم تفعيل التحول التلقائي وإسناد الاستمرارية إلى ${standby.name}."
                    } else {
                        "تنبيه: لا يوجد وكيل احتياطي نشط حالياً."
                    }
                    repository.addLog(
                        OperationLog(
                            eventType = LogEventTypes.AGENT_DISCONNECTED,
                            agentId = agent.id,
                            agentName = agent.name,
                            message = "توقف الوكيل ${agent.name} عن الاستجابة. $failoverNote",
                            severity = "WARNING"
                        )
                    )
                    emitAlert(
                        title = "توقف الوكيل ${agent.name}",
                        description = failoverNote,
                        severity = if (standby != null) "WARNING" else "CRITICAL",
                        sourceAgentName = agent.name
                    )
                    dispatchWebhookIfConfigured(
                        eventType = "agent.offline",
                        payload = JSONObject().apply {
                            put("agentId", agent.id)
                            put("agentName", agent.name)
                            put("backupAgentName", standby?.name ?: "")
                        }
                    )
                } else {
                    repository.addLog(
                        OperationLog(
                            eventType = LogEventTypes.AGENT_CONNECTED,
                            agentId = agent.id,
                            agentName = agent.name,
                            message = "عاد الوكيل ${agent.name} للاتصال والعمل ضمن الشبكة السحابية.",
                            severity = "SUCCESS"
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("فشل تغيير حالة الوكيل")
            }
        }
    }

    fun deleteAgent(agent: CloudAgent) {
        viewModelScope.launch {
            try {
                syncManager.removeCloudAccount(agent.id)
                repository.addLog(
                    OperationLog(
                        eventType = LogEventTypes.AGENT_DISCONNECTED,
                        agentId = agent.id,
                        agentName = agent.name,
                        message = "تمت إزالة الوكيل ${agent.name} من الكتلة السحابية.",
                        severity = "INFO"
                    )
                )
                _snackbarMessage.tryEmit("تم حذف الوكيل ${agent.name}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("تعذر حذف الوكيل")
            }
        }
    }

    fun executeOrchestratedTask(
        title: String,
        prompt: String,
        category: String,
        simulatePrimaryFailure: Boolean
    ) {
        viewModelScope.launch {
            val currentAgents = (agentsState.value as? UiState.Success)?.data.orEmpty()
            val settings = settingsState.value
            val primaryAgent = selectBestAgent(currentAgents, settings.loadBalanceStrategy, emptySet())
            if (primaryAgent == null) {
                _snackbarMessage.tryEmit("يرجى إضافة وكيل سحابي نشط واحد على الأقل لتنفيذ المهام")
                return@launch
            }

            val taskId = UUID.randomUUID().toString()
            val startTime = System.currentTimeMillis()
            var activeAgent = primaryAgent
            var failoverCount = 0L
            var failoverFromName = ""
            var checkpointSummary = ""

            _liveExecution.value = LiveExecutionState(
                isRunning = true,
                taskId = taskId,
                activeAgentName = activeAgent.name,
                statusLabel = "جاري التنفيذ عبر ${activeAgent.name}..."
            )

            try {
                repository.createTask(
                    OrchestratedTask(
                        userId = currentUserId,
                        title = title.trim().take(120),
                        prompt = prompt.trim().take(4000),
                        category = category,
                        status = TaskStatus.RUNNING,
                        assignedAgentId = activeAgent.id,
                        assignedAgentName = activeAgent.name,
                        checkpointNote = "تم بدء التنفيذ على ${activeAgent.name}"
                    ),
                    customId = taskId
                )
                repository.updateAgentStatusAndLoad(
                    agentId = activeAgent.id,
                    status = AgentStatus.ACTIVE,
                    currentLoadPct = (activeAgent.currentLoadPct + 25L).coerceAtMost(95L)
                )
                repository.addLog(
                    OperationLog(
                        eventType = LogEventTypes.TASK_STARTED,
                        agentId = activeAgent.id,
                        agentName = activeAgent.name,
                        taskId = taskId,
                        message = "بدء تنفيذ المهمة '$title' على الوكيل ${activeAgent.name} (${activeAgent.googleAccountEmail}).",
                        severity = "INFO"
                    )
                )

                // Check if user requested mid-flight failover verification or primary agent goes offline
                if (simulatePrimaryFailure && settings.autoFailoverEnabled) {
                    val backupAgent = selectBestAgent(
                        currentAgents,
                        settings.loadBalanceStrategy,
                        setOf(primaryAgent.id)
                    )
                    if (backupAgent != null) {
                        delay(700L)
                        checkpointSummary = "تم حجز الموارد الأولية وفحص السياق بواسطة ${primaryAgent.name} قبل الانقطاع."
                        failoverFromName = primaryAgent.name
                        failoverCount = 1L

                        // Mark primary offline
                        repository.updateAgentStatusAndLoad(
                            agentId = primaryAgent.id,
                            status = AgentStatus.OFFLINE,
                            currentLoadPct = 0L
                        )

                        _liveExecution.value = _liveExecution.value.copy(
                            failoverTriggered = true,
                            failedAgentName = primaryAgent.name,
                            activeAgentName = backupAgent.name,
                            statusLabel = "انقطع ${primaryAgent.name}! تحول تلقائي إلى ${backupAgent.name}..."
                        )

                        repository.updateTaskProgress(
                            taskId = taskId,
                            status = TaskStatus.FAILING_OVER,
                            assignedAgentId = backupAgent.id,
                            assignedAgentName = backupAgent.name,
                            failoverFromAgentName = primaryAgent.name,
                            failoverCount = 1L,
                            checkpointNote = checkpointSummary
                        )

                        repository.addLog(
                            OperationLog(
                                eventType = LogEventTypes.FAILOVER_TRIGGERED,
                                agentId = backupAgent.id,
                                agentName = backupAgent.name,
                                taskId = taskId,
                                message = "تحول تلقائي فوري: توقف ${primaryAgent.name} وأكمل ${backupAgent.name} التنفيذ دون انقطاع.",
                                metadataJson = JSONObject(
                                    mapOf("from" to primaryAgent.name, "to" to backupAgent.name)
                                ).toString(),
                                latencyMs = 180L,
                                severity = "WARNING"
                            )
                        )

                        emitAlert(
                            title = "تحول تلقائي ناجح (Auto-Failover)",
                            description = "توقف ${primaryAgent.name} أثناء المهمة '$title'، وقام ${backupAgent.name} بالإكمال تلقائياً.",
                            severity = "WARNING",
                            sourceAgentName = primaryAgent.name
                        )

                        dispatchWebhookIfConfigured(
                            eventType = "task.failover",
                            payload = JSONObject().apply {
                                put("taskId", taskId)
                                put("fromAgent", primaryAgent.name)
                                put("toAgent", backupAgent.name)
                            }
                        )

                        activeAgent = backupAgent
                        repository.updateAgentStatusAndLoad(
                            agentId = activeAgent.id,
                            status = AgentStatus.ACTIVE,
                            currentLoadPct = (activeAgent.currentLoadPct + 22L).coerceAtMost(95L),
                            failoversHandledDelta = 1L
                        )
                    } else {
                        _snackbarMessage.tryEmit("لا يوجد وكيل احتياطي نشط للتحول التلقائي؛ سيُكمل الوكيل الأساسي.")
                    }
                }

                // Stream real Gemini AI response via Firebase AI Logic
                var finalOutput = ""
                aiService.executeAgentWorkloadStream(
                    agent = activeAgent,
                    taskTitle = title,
                    taskPrompt = prompt,
                    category = category,
                    failoverCheckpoint = checkpointSummary.ifBlank { null },
                    failedAgentName = failoverFromName.ifBlank { null }
                ).collect { accumulated ->
                    finalOutput = accumulated
                    _liveExecution.value = _liveExecution.value.copy(
                        streamedOutput = accumulated,
                        statusLabel = if (failoverCount > 0) {
                            "يكمل ${activeAgent.name} المعالجة بعد التحول التلقائي..."
                        } else {
                            "جاري التوليد السحابي عبر ${activeAgent.name}..."
                        }
                    )
                }

                val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                repository.updateTaskProgress(
                    taskId = taskId,
                    status = TaskStatus.COMPLETED,
                    assignedAgentId = activeAgent.id,
                    assignedAgentName = activeAgent.name,
                    failoverFromAgentName = failoverFromName,
                    failoverCount = failoverCount,
                    latencyMs = elapsedMs,
                    resultSummary = finalOutput,
                    checkpointNote = "اكتمل بنجاح على ${activeAgent.name}"
                )

                val newAvgLatency = ((activeAgent.avgLatencyMs + elapsedMs) / 2L).coerceAtLeast(50L)
                repository.updateAgentStatusAndLoad(
                    agentId = activeAgent.id,
                    status = AgentStatus.ACTIVE,
                    currentLoadPct = (activeAgent.currentLoadPct - 10L).coerceAtLeast(10L),
                    tasksCompletedDelta = 1L,
                    avgLatencyMs = newAvgLatency
                )

                repository.addLog(
                    OperationLog(
                        eventType = LogEventTypes.TASK_COMPLETED,
                        agentId = activeAgent.id,
                        agentName = activeAgent.name,
                        taskId = taskId,
                        message = "اكتملت المهمة '$title' بنجاح خلال ${elapsedMs}ms بواسطة ${activeAgent.name}.",
                        latencyMs = elapsedMs,
                        severity = "SUCCESS"
                    )
                )

                dispatchWebhookIfConfigured(
                    eventType = "task.completed",
                    payload = JSONObject().apply {
                        put("taskId", taskId)
                        put("agentName", activeAgent.name)
                        put("latencyMs", elapsedMs)
                        put("failoverCount", failoverCount)
                    }
                )

                _liveExecution.value = _liveExecution.value.copy(
                    isRunning = false,
                    statusLabel = "اكتملت المهمة بنجاح (${elapsedMs}ms)"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Orchestrated task failed", e)
                val msg = e.localizedMessage ?: "فشل تنفيذ المهمة السحابية"
                val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                runCatching {
                    repository.updateTaskProgress(
                        taskId = taskId,
                        status = TaskStatus.FAILED,
                        latencyMs = elapsedMs,
                        resultSummary = msg,
                        checkpointNote = "توقف التنفيذ بسبب خطأ"
                    )
                    repository.addLog(
                        OperationLog(
                            eventType = LogEventTypes.ERROR,
                            agentId = activeAgent.id,
                            agentName = activeAgent.name,
                            taskId = taskId,
                            message = "فشلت المهمة '$title': $msg",
                            latencyMs = elapsedMs,
                            severity = "CRITICAL"
                        )
                    )
                }
                _liveExecution.value = _liveExecution.value.copy(
                    isRunning = false,
                    statusLabel = "فشل التنفيذ",
                    errorMessage = msg
                )
            }
        }
    }

    fun generatePeriodicReport(periodLabel: String) {
        viewModelScope.launch {
            _isGeneratingReport.value = true
            try {
                val agents = (agentsState.value as? UiState.Success)?.data.orEmpty()
                val tasks = (tasksState.value as? UiState.Success)?.data.orEmpty()
                val logs = (logsState.value as? UiState.Success)?.data.orEmpty()

                val aiReport = aiService.generateClusterPerformanceReport(
                    agents = agents,
                    tasks = tasks,
                    logs = logs,
                    periodLabel = periodLabel
                )
                val failovers = tasks.sumOf { it.failoverCount }
                val avgLatency = tasks.filter { it.latencyMs > 0 }
                    .map { it.latencyMs }
                    .average()
                    .takeIf { !it.isNaN() }
                    ?.toLong()
                    ?: agents.map { it.avgLatencyMs }.average().takeIf { !it.isNaN() }?.toLong()
                    ?: 0L

                val report = PerformanceReport(
                    userId = currentUserId,
                    title = aiReport.title.take(120),
                    periodLabel = periodLabel.take(60),
                    executiveSummary = aiReport.executiveSummary.take(4000),
                    recommendations = aiReport.recommendations.take(3000),
                    healthScore = aiReport.healthScore.toLong().coerceIn(0L, 100L),
                    totalTasksAnalyzed = tasks.size.toLong(),
                    totalFailoversAnalyzed = failovers,
                    avgLatencyMs = avgLatency
                )
                repository.saveReport(report)
                repository.addLog(
                    OperationLog(
                        eventType = LogEventTypes.REPORT_GENERATED,
                        agentName = "AI SRE Auditor",
                        message = "تم استخراج تقرير الأداء الدوري (${periodLabel}) بدرجة صحة ${aiReport.healthScore}/100.",
                        severity = "SUCCESS"
                    )
                )
                _snackbarMessage.tryEmit("تم إنشاء وحفظ تقرير الأداء الدوري")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate report", e)
                _snackbarMessage.tryEmit(e.localizedMessage ?: "تعذر إنشاء التقرير التحليلي")
            } finally {
                _isGeneratingReport.value = false
            }
        }
    }

    fun acknowledgeAlert(alert: SystemAlert) {
        viewModelScope.launch {
            runCatching { repository.acknowledgeAlert(alert.id) }
        }
    }

    fun deleteAlert(alert: SystemAlert) {
        viewModelScope.launch {
            runCatching { repository.deleteAlert(alert.id) }
        }
    }

    fun updateClusterSettings(updated: ClusterSettings) {
        viewModelScope.launch {
            try {
                repository.updateSettings(updated)
                _snackbarMessage.tryEmit("تم حفظ إعدادات الكتلة والمزامنة")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("فشل حفظ الإعدادات")
            }
        }
    }

    fun rotateApiToken() {
        val current = settingsState.value
        updateClusterSettings(current.copy(apiToken = generateToken()))
    }

    fun testWebhookIntegration(webhookUrl: String, apiToken: String) {
        viewModelScope.launch {
            _isTestingWebhook.value = true
            try {
                val agents = (agentsState.value as? UiState.Success)?.data.orEmpty()
                val payload = JSONObject().apply {
                    put("activeAgents", agents.count { it.status == AgentStatus.ACTIVE })
                    put("totalAgents", agents.size)
                    put("strategy", settingsState.value.loadBalanceStrategy)
                    put("testPing", true)
                }
                val result = webhookDispatcher.dispatchEvent(
                    webhookUrl = webhookUrl,
                    apiToken = apiToken,
                    eventType = "mesh.ping",
                    payloadJson = payload
                )
                _webhookResult.value = result
                repository.addLog(
                    OperationLog(
                        eventType = LogEventTypes.WEBHOOK_DISPATCHED,
                        agentName = "API Gateway",
                        message = if (result.success) {
                            "نجح اختبار Webhook (${result.statusCode}) خلال ${result.latencyMs}ms."
                        } else {
                            "فشل اختبار Webhook: ${result.responseBody.take(120)}"
                        },
                        latencyMs = result.latencyMs,
                        severity = if (result.success) "SUCCESS" else "WARNING"
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isTestingWebhook.value = false
            }
        }
    }

    private suspend fun emitAlert(
        title: String,
        description: String,
        severity: String,
        sourceAgentName: String
    ) {
        val settings = settingsState.value
        repository.addAlert(
            SystemAlert(
                userId = currentUserId,
                title = title.take(120),
                description = description.take(500),
                severity = severity,
                sourceAgentName = sourceAgentName.take(80),
                isAcknowledged = false
            )
        )
        if (settings.instantAlertsEnabled) {
            _notificationEvent.tryEmit(
                InstantNotificationEvent(
                    id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                    title = title,
                    body = description
                )
            )
        }
    }

    private fun dispatchWebhookIfConfigured(eventType: String, payload: JSONObject) {
        val settings = settingsState.value
        if (settings.webhookUrl.isBlank()) return
        viewModelScope.launch {
            val result = webhookDispatcher.dispatchEvent(
                webhookUrl = settings.webhookUrl,
                apiToken = settings.apiToken,
                eventType = eventType,
                payloadJson = payload
            )
            runCatching {
                repository.addLog(
                    OperationLog(
                        eventType = LogEventTypes.WEBHOOK_DISPATCHED,
                        agentName = "Webhook Bridge",
                        message = "إرسال حدث $eventType -> HTTP ${result.statusCode} (${result.latencyMs}ms)",
                        latencyMs = result.latencyMs,
                        severity = if (result.success) "INFO" else "WARNING"
                    )
                )
            }
        }
    }

    private fun findBackupAgent(excludingId: String): CloudAgent? {
        val agents = (agentsState.value as? UiState.Success)?.data.orEmpty()
        return selectBestAgent(agents, settingsState.value.loadBalanceStrategy, setOf(excludingId))
    }

    private fun selectBestAgent(
        agents: List<CloudAgent>,
        strategy: String,
        excludedIds: Set<String>
    ): CloudAgent? {
        val eligible = agents.filter {
            it.id !in excludedIds &&
                (it.status == AgentStatus.ACTIVE || it.status == AgentStatus.DEGRADED) &&
                (excludedIds.isEmpty() || it.autoFailoverEnabled)
        }
        if (eligible.isEmpty()) return null

        return when (strategy) {
            LoadBalanceStrategies.LEAST_LOAD -> eligible.minByOrNull { it.currentLoadPct }
            LoadBalanceStrategies.ROUND_ROBIN -> {
                val sorted = eligible.sortedBy { it.name }
                val chosen = sorted[roundRobinCursor % sorted.size]
                roundRobinCursor++
                chosen
            }
            else -> eligible.sortedWith(
                compareByDescending<CloudAgent> { it.priority }
                    .thenBy { it.currentLoadPct }
            ).firstOrNull()
        }
    }

    private fun extractFirestoreMessage(error: Throwable, fallback: String): String {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, fallback, rootError)
        return rootError.message ?: fallback
    }

    private fun generateToken(): String =
        "nxm_${UUID.randomUUID().toString().replace("-", "").take(24)}"

    private companion object {
        const val TAG = "MeshViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
