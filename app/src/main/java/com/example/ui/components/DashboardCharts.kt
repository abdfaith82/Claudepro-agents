package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AgentStatus
import com.example.data.model.CloudAgent
import com.example.data.model.OrchestratedTask
import com.example.data.model.TaskStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSecondary
import com.example.ui.theme.IndigoTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val ChartPalette = listOf(
    CyanPrimary,
    EmeraldSecondary,
    IndigoTertiary,
    AmberWarning,
    CoralError,
    Color(0xFF38BDF8)
)

@Composable
fun WorkloadDistributionCard(
    agents: List<CloudAgent>,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DonutLarge, contentDescription = null, tint = CyanPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "توزيع الأعباء السحابية عبر الوكلاء",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (agents.isEmpty()) {
                Text(
                    text = "أضف وكلاء سحابيين لعرض مخطط توزيع الأعباء الحية.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val totalWeight = agents.sumOf {
                    if (it.status == AgentStatus.OFFLINE) 0L else (it.currentLoadPct + it.tasksCompleted * 10L).coerceAtLeast(5L)
                }.coerceAtLeast(1L)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(132.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        Canvas(modifier = Modifier.size(132.dp)) {
                            val strokeWidth = 18.dp.toPx()
                            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                            drawArc(
                                color = trackColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth)
                            )

                            var startAngle = -90f
                            agents.forEachIndexed { index, agent ->
                                val weight = if (agent.status == AgentStatus.OFFLINE) 0L
                                else (agent.currentLoadPct + agent.tasksCompleted * 10L).coerceAtLeast(5L)
                                if (weight > 0L) {
                                    val sweep = (weight.toFloat() / totalWeight.toFloat()) * 360f
                                    drawArc(
                                        color = ChartPalette[index % ChartPalette.size],
                                        startAngle = startAngle,
                                        sweepAngle = (sweep - 3f).coerceAtLeast(2f),
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                    startAngle += sweep
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${agents.count { it.status == AgentStatus.ACTIVE }}/${agents.size}",
                                style = MaterialTheme.typography.titleMedium,
                                color = CyanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "متصل",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        agents.take(5).forEachIndexed { index, agent ->
                            val color = if (agent.status == AgentStatus.OFFLINE) {
                                CoralError.copy(alpha = 0.5f)
                            } else {
                                ChartPalette[index % ChartPalette.size]
                            }
                            val sharePct = if (agent.status == AgentStatus.OFFLINE) 0 else {
                                val w = (agent.currentLoadPct + agent.tasksCompleted * 10L).coerceAtLeast(5L)
                                ((w.toFloat() / totalWeight.toFloat()) * 100f).roundToInt()
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = agent.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = if (agent.status == AgentStatus.OFFLINE) "متوقف" else "$sharePct% (${agent.currentLoadPct}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = color
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class TimeSeriesPoint(
    val label: String,
    val latencyMs: Float,
    val throughputScore: Float,
    val agentName: String
)

@Composable
fun PerformanceOverTimeCard(
    agents: List<CloudAgent>,
    tasks: List<OrchestratedTask>,
    modifier: Modifier = Modifier
) {
    var showLatency by remember { mutableStateOf(true) }
    var selectedIndex by remember { mutableIntStateOf(-1) }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }

    val points = remember(agents, tasks) {
        if (tasks.isNotEmpty()) {
            tasks.take(12).reversed().mapIndexed { idx, task ->
                val dateLabel = task.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "T${idx + 1}"
                val latency = task.latencyMs.coerceAtLeast(45L).toFloat()
                val score = when (task.status) {
                    TaskStatus.COMPLETED -> if (task.failoverCount > 0) 92f else 100f
                    TaskStatus.FAILING_OVER -> 80f
                    TaskStatus.FAILED -> 35f
                    else -> 88f
                }
                TimeSeriesPoint(
                    label = dateLabel,
                    latencyMs = latency,
                    throughputScore = score,
                    agentName = task.assignedAgentName.ifBlank { "Orchestrator" }
                )
            }
        } else if (agents.isNotEmpty()) {
            agents.mapIndexed { idx, a ->
                TimeSeriesPoint(
                    label = "N${idx + 1}",
                    latencyMs = a.avgLatencyMs.coerceAtLeast(50L).toFloat(),
                    throughputScore = if (a.status == AgentStatus.ACTIVE) (100f - a.currentLoadPct * 0.2f) else 0f,
                    agentName = a.name
                )
            }
        } else {
            emptyList()
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
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
                    Icon(Icons.Default.Timeline, contentDescription = null, tint = EmeraldSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مؤشرات الأداء عبر الزمن",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = showLatency,
                    onClick = { showLatency = true; selectedIndex = -1 },
                    label = { Text("زمن الاستجابة (ms)") }
                )
                FilterChip(
                    selected = !showLatency,
                    onClick = { showLatency = false; selectedIndex = -1 },
                    label = { Text("كفاءة الاستمرارية (%)") }
                )
            }

            if (points.isEmpty()) {
                Text(
                    text = "نفّذ مهمة واحدة أو أضف وكلاء لعرض المنحنى الزمني للأداء.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val values = points.map { if (showLatency) it.latencyMs else it.throughputScore }
                val maxVal = (values.maxOrNull() ?: 100f).coerceAtLeast(10f) * 1.15f
                val minVal = if (showLatency) 0f else 0f
                val lineColor = if (showLatency) CyanPrimary else EmeraldSecondary
                val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                if (points.isNotEmpty()) {
                                    val stepX = if (points.size == 1) 0f else size.width / (points.size - 1).toFloat()
                                    val closest = if (stepX <= 0f) 0 else {
                                        (offset.x / stepX).roundToInt().coerceIn(0, points.lastIndex)
                                    }
                                    selectedIndex = closest
                                }
                            }
                        }
                ) {
                    val padTop = 14.dp.toPx()
                    val padBottom = 18.dp.toPx()
                    val chartH = (size.height - padTop - padBottom).coerceAtLeast(10f)

                    // Horizontal dashed reference grid lines
                    for (i in 0..3) {
                        val y = padTop + (chartH / 3f) * i
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )
                    }

                    val coords = points.mapIndexed { idx, _ ->
                        val x = if (points.size == 1) size.width / 2f
                        else (idx.toFloat() / (points.size - 1).toFloat()) * size.width
                        val norm = ((values[idx] - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                        val y = padTop + chartH * (1f - norm)
                        Offset(x, y)
                    }

                    if (coords.size >= 2) {
                        val linePath = Path().apply {
                            moveTo(coords.first().x, coords.first().y)
                            for (i in 1 until coords.size) {
                                val prev = coords[i - 1]
                                val curr = coords[i]
                                val midX = (prev.x + curr.x) / 2f
                                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                            }
                        }

                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(coords.last().x, padTop + chartH)
                            lineTo(coords.first().x, padTop + chartH)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                listOf(lineColor.copy(alpha = 0.32f), Color.Transparent)
                            )
                        )
                        drawPath(
                            path = linePath,
                            color = lineColor,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    coords.forEachIndexed { idx, pt ->
                        val isSelected = idx == selectedIndex
                        drawCircle(
                            color = if (isSelected) AmberWarning else lineColor,
                            radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                            center = pt
                        )
                    }
                }

                // X-axis labels row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = points.first().label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (points.size > 2) {
                        Text(
                            text = points[points.size / 2].label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = points.last().label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val inspected = points.getOrNull(selectedIndex) ?: points.last()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${inspected.agentName} (${inspected.label}) • الكمون: ${inspected.latencyMs.roundToInt()}ms • الكفاءة: ${inspected.throughputScore.roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
