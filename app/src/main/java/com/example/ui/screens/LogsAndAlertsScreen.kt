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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.LogEventTypes
import com.example.data.model.OperationLog
import com.example.data.model.SystemAlert
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSecondary
import com.example.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogsAndAlertsScreen(
    logsState: UiState<List<OperationLog>>,
    alertsState: UiState<List<SystemAlert>>,
    initialTab: Int = 0,
    onAcknowledgeAlert: (SystemAlert) -> Unit,
    onDeleteAlert: (SystemAlert) -> Unit
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var eventFilter by remember { mutableStateOf("ALL") }

    val alerts = (alertsState as? UiState.Success)?.data.orEmpty()
    val unreadAlertsCount = alerts.count { !it.isAcknowledged }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 840.dp)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("سجل العمليات التفصيلي") },
                icon = { Icon(Icons.Default.Terminal, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(if (unreadAlertsCount > 0) "التنبيهات ($unreadAlertsCount)" else "التنبيهات الفورية")
                },
                icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) }
            )
        }

        if (selectedTab == 0) {
            when (logsState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(logsState.message, color = MaterialTheme.colorScheme.error)
                }
                is UiState.Success -> {
                    val filteredLogs = logsState.data.filter { log ->
                        val matchesQuery = searchQuery.isBlank() ||
                            log.message.contains(searchQuery, ignoreCase = true) ||
                            log.agentName.contains(searchQuery, ignoreCase = true) ||
                            log.eventType.contains(searchQuery, ignoreCase = true)
                        val matchesFilter = when (eventFilter) {
                            "FAILOVER" -> log.eventType == LogEventTypes.FAILOVER_TRIGGERED || log.eventType == LogEventTypes.AGENT_DISCONNECTED
                            "TASKS" -> log.eventType == LogEventTypes.TASK_STARTED || log.eventType == LogEventTypes.TASK_COMPLETED
                            "ERRORS" -> log.severity == "CRITICAL" || log.severity == "WARNING"
                            else -> true
                        }
                        matchesQuery && matchesFilter
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                label = { Text("بحث في السجلات أو اسم الوكيل...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "ALL" to "الكل",
                                    "FAILOVER" to "التحول التلقائي",
                                    "TASKS" to "المهام",
                                    "ERRORS" to "تحذيرات وأخطاء"
                                ).forEach { (key, label) ->
                                    FilterChip(
                                        selected = eventFilter == key,
                                        onClick = { eventFilter = key },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                        if (filteredLogs.isEmpty()) {
                            item {
                                Text(
                                    text = "لا توجد سجلات مطابقة للبحث الحالي.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 24.dp)
                                )
                            }
                        } else {
                            items(filteredLogs, key = { it.id }) { log ->
                                LogEntryCard(log)
                            }
                        }
                    }
                }
            }
        } else {
            when (alertsState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(alertsState.message, color = MaterialTheme.colorScheme.error)
                }
                is UiState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (alerts.isEmpty()) {
                            item {
                                Text(
                                    text = "لا توجد تنبيهات نشطة. تعمل كافة الوكلاء بكفاءة قصوى.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EmeraldSecondary,
                                    modifier = Modifier.padding(top = 24.dp)
                                )
                            }
                        } else {
                            items(alerts, key = { it.id }) { alert ->
                                AlertCard(
                                    alert = alert,
                                    onAcknowledge = { onAcknowledgeAlert(alert) },
                                    onDelete = { onDeleteAlert(alert) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(log: OperationLog) {
    val severityColor = when (log.severity) {
        "SUCCESS" -> EmeraldSecondary
        "WARNING" -> AmberWarning
        "CRITICAL" -> CoralError
        else -> CyanPrimary
    }
    val formattedTime = remember(log.createdAt) {
        val date = log.createdAt?.toDate() ?: Date()
        SimpleDateFormat("HH:mm:ss • dd/MM", Locale.US).format(date)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = severityColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = log.eventType,
                        style = MaterialTheme.typography.labelSmall,
                        color = severityColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = "$formattedTime ${if (log.latencyMs > 0) "| ${log.latencyMs}ms" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (log.agentName.isNotBlank()) {
                Text(
                    text = "الوكيل: ${log.agentName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: SystemAlert,
    onAcknowledge: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = when (alert.severity) {
        "CRITICAL" -> CoralError
        "WARNING" -> AmberWarning
        else -> CyanPrimary
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (alert.isAcknowledged) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف التنبيه",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = alert.description,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (alert.sourceAgentName.isNotBlank()) "المصدر: ${alert.sourceAgentName}" else "النظام المركزي",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!alert.isAcknowledged) {
                    OutlinedButton(onClick = onAcknowledge) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تأكيد القراءة")
                    }
                } else {
                    Text(
                        text = "تم التأكيد",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldSecondary
                    )
                }
            }
        }
    }
}
