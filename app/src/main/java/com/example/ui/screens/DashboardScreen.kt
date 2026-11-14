package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.manager.CloudSyncMetadata
import com.example.data.model.AgentStatus
import com.example.data.model.CloudAgent
import com.example.data.model.ClusterSettings
import com.example.data.model.LoadBalanceStrategies
import com.example.data.model.OrchestratedTask
import com.example.data.model.TaskCategories
import com.example.data.model.TaskStatus
import com.example.ui.components.MeshTopologyCanvas
import com.example.ui.components.PerformanceOverTimeCard
import com.example.ui.components.WorkloadDistributionCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSecondary
import com.example.viewmodel.LiveExecutionState
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    agentsState: UiState<List<CloudAgent>>,
    tasksState: UiState<List<OrchestratedTask>>,
    syncMetadata: CloudSyncMetadata,
    settings: ClusterSettings,
    liveExecution: LiveExecutionState,
    onNavigateToAgents: () -> Unit,
    onRunWorkload: (title: String, prompt: String, category: String, simulateFailover: Boolean) -> Unit,
    onChangeStrategy: (String) -> Unit
) {
    val agents = (agentsState as? UiState.Success)?.data.orEmpty()
    val tasks = (tasksState as? UiState.Success)?.data.orEmpty()

    val activeCount = agents.count { it.status == AgentStatus.ACTIVE }
    val avgLoad = if (agents.isEmpty()) 0 else agents.map { it.currentLoadPct }.average().toInt()
    val totalFailovers = tasks.sumOf { it.failoverCount } + agents.sumOf { it.failoversHandled }

    var taskTitle by remember { mutableStateOf("") }
    var taskPrompt by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(TaskCategories.ORCHESTRATION) }
    var simulateFailover by remember { mutableStateOf(false) }

    val syncStatusText = when {
        syncMetadata.hasPendingWrites -> "جاري المزامنة السحابية..."
        syncMetadata.isFromCache -> "وضع الكاش المحلي (متعدد الأجهزة)"
        else -> "متزامن سحابياً عبر الأجهزة"
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Command Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(146.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_mesh_hero),
                            contentDescription = "خلفية لوحة التحكم السحابية",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.25f),
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = EmeraldSecondary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (syncMetadata.hasPendingWrites) AmberWarning else EmeraldSecondary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = syncStatusText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (syncMetadata.hasPendingWrites) AmberWarning else EmeraldSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = LoadBalanceStrategies.labelAr(settings.loadBalanceStrategy),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanPrimary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                KpiChip("الوكلاء النشطون", "$activeCount / ${agents.size}", CyanPrimary)
                                KpiChip("متوسط الحمل", "$avgLoad%", EmeraldSecondary)
                                KpiChip("تحولات تلقائية", "$totalFailovers", AmberWarning)
                            }
                        }
                    }
                }
            }

            // 2. Central Workload Distribution & Performance Over Time Charts
            item {
                WorkloadDistributionCard(agents = agents)
            }
            item {
                PerformanceOverTimeCard(agents = agents, tasks = tasks)
            }

            // 2. Mesh Topology Visualizer + Load Strategy
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Hub, contentDescription = null, tint = CyanPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("طوبولوجيا الربط السحابي الحية", style = MaterialTheme.typography.titleMedium)
                            }
                            OutlinedButton(
                                onClick = onNavigateToAgents,
                                modifier = Modifier.testTag("manage_agents_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إدارة الوكلاء")
                            }
                        }

                        MeshTopologyCanvas(
                            agents = agents,
                            activeFailoverAgentName = liveExecution.activeAgentName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                        )

                        Text(
                            text = "توزيع الأعباء الذكي:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LoadBalanceStrategies.all.forEach { strat ->
                                FilterChip(
                                    selected = settings.loadBalanceStrategy == strat,
                                    onClick = { onChangeStrategy(strat) },
                                    label = { Text(LoadBalanceStrategies.labelAr(strat)) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Live AI Workload Orchestrator & Failover Continuity Runner
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = CyanPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تشغيل مهمة ذكاء اصطناعي منسقة", style = MaterialTheme.typography.titleMedium)
                        }

                        OutlinedTextField(
                            value = taskTitle,
                            onValueChange = { taskTitle = it },
                            label = { Text("عنوان المهمة (مثال: تحليل أعطال الخادم)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("task_title_input")
                        )

                        OutlinedTextField(
                            value = taskPrompt,
                            onValueChange = { taskPrompt = it },
                            label = { Text("الأمر المطلوب تنفيذه عبر الوكلاء") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("task_prompt_input")
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TaskCategories.all.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(TaskCategories.labelAr(cat)) }
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "اختبار التحول التلقائي أثناء التنفيذ",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "يحاكي توقف الوكيل الأول ليكمل الوكيل التالي العمل تلقائياً",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = simulateFailover,
                                    onCheckedChange = { simulateFailover = it },
                                    modifier = Modifier.testTag("simulate_failover_switch")
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val finalTitle = taskTitle.ifBlank { "تحليل وتشخيص شبكة الوكلاء" }
                                val finalPrompt = taskPrompt.ifBlank {
                                    "حلّل كفاءة توزيع المهام بين حسابات السحابة واقترح خطة لضمان عدم التوقف."
                                }
                                onRunWorkload(finalTitle, finalPrompt, selectedCategory, simulateFailover)
                            },
                            enabled = !liveExecution.isRunning && activeCount > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("run_task_button")
                        ) {
                            if (liveExecution.isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري التنفيذ المنسق...")
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (activeCount == 0) "أضف وكيلاً نشطاً أولاً" else "إرسال وتشغيل على الوكلاء"
                                )
                            }
                        }

                        AnimatedVisibility(visible = liveExecution.statusLabel.isNotBlank() || liveExecution.streamedOutput.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (liveExecution.failoverTriggered) Icons.Default.SwapHoriz else Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = if (liveExecution.failoverTriggered) AmberWarning else EmeraldSecondary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = liveExecution.statusLabel,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (liveExecution.failoverTriggered) AmberWarning else CyanPrimary
                                        )
                                    }
                                    if (liveExecution.isRunning) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                    if (liveExecution.errorMessage != null) {
                                        Text(
                                            text = liveExecution.errorMessage,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = CoralError
                                        )
                                    } else if (liveExecution.streamedOutput.isNotBlank()) {
                                        Text(
                                            text = liveExecution.streamedOutput,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Recent Tasks History
            item {
                Text(
                    text = "أحدث المهام المنسقة (${tasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (tasks.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "لم يتم تشغيل أي مهام بعد. أرسل مهمة من الأعلى لاختبار التنسيق الذكي والتحول التلقائي.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(tasks.take(10), key = { it.id }) { task ->
                    TaskSummaryCard(task)
                }
            }
        }
    }
}

@Composable
private fun KpiChip(label: String, value: String, accent: Color) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = accent,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TaskSummaryCard(task: OrchestratedTask) {
    val statusColor = when (task.status) {
        TaskStatus.COMPLETED -> EmeraldSecondary
        TaskStatus.FAILING_OVER -> AmberWarning
        TaskStatus.FAILED -> CoralError
        else -> CyanPrimary
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (task.status == TaskStatus.COMPLETED) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = TaskStatus.labelAr(task.status),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                }
            }
            Text(
                text = "الوكيل المنفذ: ${task.assignedAgentName} • التصنيف: ${TaskCategories.labelAr(task.category)} • ${task.latencyMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (task.failoverCount > 0 && task.failoverFromAgentName.isNotBlank()) {
                Text(
                    text = "تحول تلقائي من ${task.failoverFromAgentName} إلى ${task.assignedAgentName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberWarning
                )
            }
            if (task.resultSummary.isNotBlank()) {
                Text(
                    text = task.resultSummary.take(220) + if (task.resultSummary.length > 220) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
