package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AgentProviders
import com.example.data.model.AgentStatus
import com.example.data.model.CloudAgent
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSecondary
import com.example.viewmodel.UiState
import kotlin.math.roundToLong

@Composable
fun AgentsScreen(
    agentsState: UiState<List<CloudAgent>>,
    currentUserEmail: String,
    onAddAgent: (
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
    ) -> Unit,
    onUpdatePriority: (CloudAgent, Long) -> Unit,
    onUpdateConfig: (
        agentId: String,
        name: String,
        googleAccountEmail: String,
        priority: Long,
        maxConcurrency: Long,
        autoFailoverEnabled: Boolean,
        role: String,
        endpointUrl: String
    ) -> Unit,
    onToggleStatus: (CloudAgent) -> Unit,
    onDeleteAgent: (CloudAgent) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAgent by remember { mutableStateOf<CloudAgent?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (agentsState) {
            is UiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is UiState.Error -> {
                Text(
                    text = agentsState.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
            is UiState.Success -> {
                val agents = agentsState.data
                if (agents.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .widthIn(max = 460.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "لا توجد حسابات سحابية مربوطة بعد",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "أضف حسابين أو أكثر من حسابات الكلاود المرتبطة بحساب Google ليقوم كل وكيل بالربط والإكمال التلقائي عند توقف الآخر.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("empty_add_agent_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إضافة وكيل سحابي الآن")
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 840.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 92.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "اضبط أولوية كل وكيل بشكل مستقل (1-10) أو أوقف أي وكيل لاختبار التحول التلقائي الفوري بين حسابات Google المرتبطة.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            items(agents, key = { it.id }) { agent ->
                                CloudAgentCard(
                                    agent = agent,
                                    onPriorityChange = { newPriority -> onUpdatePriority(agent, newPriority) },
                                    onToggleStatus = { onToggleStatus(agent) },
                                    onEdit = { editingAgent = agent },
                                    onDelete = { onDeleteAgent(agent) }
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = CyanPrimary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_agent_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "إضافة وكيل سحابي")
        }
    }

    if (showAddDialog) {
        AgentEditorDialog(
            initialAgent = null,
            defaultEmail = currentUserEmail,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, provider, email, region, model, role, priority, concurrency, autoFailover, endpoint ->
                onAddAgent(name, provider, email, region, model, role, priority, concurrency, autoFailover, endpoint)
                showAddDialog = false
            }
        )
    }

    if (editingAgent != null) {
        val target = editingAgent!!
        AgentEditorDialog(
            initialAgent = target,
            defaultEmail = currentUserEmail,
            onDismiss = { editingAgent = null },
            onConfirm = { name, _, email, _, _, role, priority, concurrency, autoFailover, endpoint ->
                onUpdateConfig(target.id, name, email, priority, concurrency, autoFailover, role, endpoint)
                editingAgent = null
            }
        )
    }
}

@Composable
private fun CloudAgentCard(
    agent: CloudAgent,
    onPriorityChange: (Long) -> Unit,
    onToggleStatus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (agent.status) {
        AgentStatus.ACTIVE -> EmeraldSecondary
        AgentStatus.DEGRADED, AgentStatus.RECOVERING -> AmberWarning
        else -> CoralError
    }
    var sliderPriority by remember(agent.id, agent.priority) {
        mutableFloatStateOf(agent.priority.toFloat())
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agent_card_${agent.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${AgentProviders.displayName(agent.provider)} • ${agent.region} • ${agent.modelName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPrimary
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = AgentStatus.labelAr(agent.status),
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "حساب Google المرتبط: ${agent.googleAccountEmail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "الحمل الحسابي: ${agent.currentLoadPct}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "مهام: ${agent.tasksCompleted} | تحولات: ${agent.failoversHandled} | ${agent.avgLatencyMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { (agent.currentLoadPct.coerceIn(0L, 100L) / 100f) },
                    color = statusColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "أولوية المهام المستقلة:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${sliderPriority.roundToLong()} / 10",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPrimary
                    )
                }
                Slider(
                    value = sliderPriority,
                    onValueChange = { sliderPriority = it },
                    onValueChangeFinished = {
                        val next = sliderPriority.roundToLong()
                        if (next != agent.priority) {
                            onPriorityChange(next)
                        }
                    },
                    valueRange = 1f..10f,
                    steps = 8
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onToggleStatus,
                    modifier = Modifier.testTag("toggle_agent_status_${agent.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (agent.status == AgentStatus.ACTIVE) CoralError else EmeraldSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (agent.status == AgentStatus.ACTIVE) "إيقاف (اختبار التحول)" else "إعادة الاتصال"
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل إعدادات الوكيل")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "حذف الوكيل",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentEditorDialog(
    initialAgent: CloudAgent?,
    defaultEmail: String,
    onDismiss: () -> Unit,
    onConfirm: (
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
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialAgent?.name ?: "") }
    var provider by remember { mutableStateOf(initialAgent?.provider ?: AgentProviders.VERTEX_AI) }
    var googleEmail by remember { mutableStateOf(initialAgent?.googleAccountEmail ?: defaultEmail) }
    var region by remember { mutableStateOf(initialAgent?.region ?: "us-central1") }
    var modelName by remember { mutableStateOf(initialAgent?.modelName ?: "gemini-3.8-flash") }
    var role by remember { mutableStateOf(initialAgent?.role ?: "Primary Reasoning") }
    var priority by remember { mutableFloatStateOf((initialAgent?.priority ?: 8L).toFloat()) }
    var concurrency by remember { mutableFloatStateOf((initialAgent?.maxConcurrency ?: 10L).toFloat()) }
    var autoFailover by remember { mutableStateOf(initialAgent?.autoFailoverEnabled ?: true) }
    var endpointUrl by remember { mutableStateOf(initialAgent?.endpointUrl ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialAgent == null) "إضافة وربط وكيل سحابي" else "تعديل إعدادات الوكيل")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الوكيل (مثال: Vertex Primary US)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agent_name_input")
                )
                OutlinedTextField(
                    value = googleEmail,
                    onValueChange = { googleEmail = it },
                    label = { Text("حساب Google المرتبط للوكيل") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agent_email_input")
                )
                if (initialAgent == null) {
                    Text("مزود السحابة (Cloud Provider):", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AgentProviders.all.forEach { p ->
                            FilterChip(
                                selected = provider == p,
                                onClick = { provider = p },
                                label = { Text(AgentProviders.displayName(p)) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = region,
                            onValueChange = { region = it },
                            label = { Text("المنطقة") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            label = { Text("النموذج") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("تخصص الوكيل") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endpointUrl,
                    onValueChange = { endpointUrl = it },
                    label = { Text("رابط API الاختياري للوكيل") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Text("أولوية المهام: ${priority.roundToLong()}/10", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = priority,
                        onValueChange = { priority = it },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }
                Column {
                    Text("الحد الأقصى للتزامن: ${concurrency.roundToLong()} مهمة", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = concurrency,
                        onValueChange = { concurrency = it },
                        valueRange = 1f..50f
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("مشاركة في التحول التلقائي", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = autoFailover, onCheckedChange = { autoFailover = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val safeName = name.ifBlank { "Cloud Node ${(100..999).random()}" }
                    onConfirm(
                        safeName,
                        provider,
                        googleEmail.ifBlank { defaultEmail },
                        region,
                        modelName,
                        role,
                        priority.roundToLong(),
                        concurrency.roundToLong(),
                        autoFailover,
                        endpointUrl
                    )
                },
                modifier = Modifier.testTag("confirm_save_agent_button")
            ) {
                Text("حفظ وربط")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
