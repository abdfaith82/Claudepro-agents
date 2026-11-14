package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.data.model.CloudAgent
import com.example.data.model.OperationLog
import com.example.data.model.OrchestratedTask
import com.example.data.model.PerformanceReport
import com.example.data.model.TaskStatus
import com.example.export.ExportFormat
import com.example.export.ReportExporter
import com.example.ui.components.AgentLoadComparisonChart
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSecondary
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(
    agentsState: UiState<List<CloudAgent>>,
    tasksState: UiState<List<OrchestratedTask>>,
    logsState: UiState<List<OperationLog>>,
    reportsState: UiState<List<PerformanceReport>>,
    isGeneratingReport: Boolean,
    onGenerateReport: (periodLabel: String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val agents = (agentsState as? UiState.Success)?.data.orEmpty()
    val tasks = (tasksState as? UiState.Success)?.data.orEmpty()
    val logs = (logsState as? UiState.Success)?.data.orEmpty()
    val reports = (reportsState as? UiState.Success)?.data.orEmpty()

    var selectedPeriod by remember { mutableStateOf("يومي (24 ساعة)") }
    var selectedFormat by remember { mutableStateOf(ExportFormat.JSON) }

    val exportedText = remember(selectedFormat, agents, tasks, logs, reports) {
        ReportExporter.exportClusterSnapshot(
            format = selectedFormat,
            agents = agents,
            tasks = tasks,
            logs = logs,
            latestReport = reports.firstOrNull()
        )
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(selectedFormat.mimeType)
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(exportedText.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "تم حفظ التقرير بصيغة ${selectedFormat.label}", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "تعذر حفظ الملف", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val completedTasks = tasks.count { it.status == TaskStatus.COMPLETED }
    val successRate = if (tasks.isEmpty()) 100 else ((completedTasks * 100f) / tasks.size).toInt()
    val avgLatency = tasks.filter { it.latencyMs > 0 }
        .map { it.latencyMs }
        .average()
        .takeIf { !it.isNaN() }
        ?.toInt()
        ?: agents.map { it.avgLatencyMs }.average().takeIf { !it.isNaN() }?.toInt()
        ?: 0

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Performance KPI Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnalyticsStatCard("معدل النجاح", "$successRate%", EmeraldSecondary, Modifier.weight(1f))
                    AnalyticsStatCard("متوسط الكمون", "${avgLatency}ms", CyanPrimary, Modifier.weight(1f))
                    AnalyticsStatCard("المهام المحللة", "${tasks.size}", AmberWarning, Modifier.weight(1f))
                }
            }

            // 2. Agent Load & Latency Comparison Chart
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
                            Icon(Icons.Default.Analytics, contentDescription = null, tint = CyanPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تحليل توزيع الأعباء والاستجابة بين الوكلاء", style = MaterialTheme.typography.titleMedium)
                        }
                        AgentLoadComparisonChart(agents = agents)
                    }
                }
            }

            // 3. AI Periodic Report Generator
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EmeraldSecondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("استخراج تقرير أداء دوري بالذكاء الاصطناعي", style = MaterialTheme.typography.titleMedium)
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("يومي (24 ساعة)", "أسبوعي (7 أيام)", "شهري (30 يوماً)").forEach { period ->
                                FilterChip(
                                    selected = selectedPeriod == period,
                                    onClick = { selectedPeriod = period },
                                    label = { Text(period) }
                                )
                            }
                        }

                        Button(
                            onClick = { onGenerateReport(selectedPeriod) },
                            enabled = !isGeneratingReport,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSecondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("generate_report_button")
                        ) {
                            if (isGeneratingReport) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري تحليل الكتلة عبر Gemini...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("استخراج تقرير دوري دقيق")
                            }
                        }
                    }
                }
            }

            // 4. Multi-Format Export Card
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
                        Text("التصدير بصيغ متعددة", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExportFormat.entries.forEach { fmt ->
                                FilterChip(
                                    selected = selectedFormat == fmt,
                                    onClick = { selectedFormat = fmt },
                                    label = { Text(fmt.label) }
                                )
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = exportedText.take(650) + if (exportedText.length > 650) "\n..." else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(exportedText))
                                    Toast.makeText(context, "تم نسخ ${selectedFormat.label}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نسخ")
                            }
                            OutlinedButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "NexusMesh Report (${selectedFormat.label})")
                                        putExtra(Intent.EXTRA_TEXT, exportedText)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة التقرير"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مشاركة")
                            }
                            Button(
                                onClick = {
                                    createDocumentLauncher.launch("nexusmesh_report.${selectedFormat.extension}")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_save_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حفظ")
                            }
                        }
                    }
                }
            }

            // 5. Saved Periodic Reports List
            if (reports.isNotEmpty()) {
                item {
                    Text(
                        text = "التقارير الدورية المحفوظة (${reports.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(reports, key = { it.id }) { report ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                    text = report.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = EmeraldSecondary.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "الصحة: ${report.healthScore}/100",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${report.periodLabel} • مهام: ${report.totalTasksAnalyzed} • تحولات: ${report.totalFailoversAnalyzed} • ${report.avgLatencyMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanPrimary
                            )
                            Text(text = report.executiveSummary, style = MaterialTheme.typography.bodyMedium)
                            if (report.recommendations.isNotBlank()) {
                                Text(
                                    text = "التوصيات:\n${report.recommendations}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun AnalyticsStatCard(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, color = color)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
