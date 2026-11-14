package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AgentStatus
import com.example.data.model.CloudAgent
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun MeshTopologyCanvas(
    agents: List<CloudAgent>,
    activeFailoverAgentName: String = "",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "meshPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsePhase"
    )

    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) * 0.34f

            // Outer mesh ring
            drawCircle(
                color = outlineColor.copy(alpha = 0.45f),
                radius = radius,
                center = center,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), pulse * 22f)
                )
            )

            val displayed = agents.take(8)
            val nodePositions = displayed.mapIndexed { index, _ ->
                val angle = (2.0 * PI * index / displayed.size.coerceAtLeast(1)) - PI / 2.0
                Offset(
                    x = center.x + (radius * cos(angle)).toFloat(),
                    y = center.y + (radius * sin(angle)).toFloat()
                )
            }

            // Failover mesh links between adjacent active nodes
            if (nodePositions.size >= 2) {
                for (i in nodePositions.indices) {
                    val next = (i + 1) % nodePositions.size
                    val a1 = displayed[i]
                    val a2 = displayed[next]
                    val linkActive = a1.status == AgentStatus.ACTIVE && a2.status == AgentStatus.ACTIVE
                    drawLine(
                        color = if (linkActive) CyanPrimary.copy(alpha = 0.35f) else CoralError.copy(alpha = 0.25f),
                        start = nodePositions[i],
                        end = nodePositions[next],
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )
                }
            }

            // Spokes from central Orchestrator Hub to each agent
            displayed.forEachIndexed { idx, agent ->
                val pos = nodePositions[idx]
                val nodeColor = when (agent.status) {
                    AgentStatus.ACTIVE -> EmeraldSecondary
                    AgentStatus.DEGRADED, AgentStatus.RECOVERING -> AmberWarning
                    else -> CoralError
                }

                drawLine(
                    color = nodeColor.copy(alpha = if (agent.status == AgentStatus.ACTIVE) 0.7f else 0.3f),
                    start = center,
                    end = pos,
                    strokeWidth = if (agent.name == activeFailoverAgentName) 3.5.dp.toPx() else 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Moving data packet dot on active links
                if (agent.status == AgentStatus.ACTIVE) {
                    val packetPos = Offset(
                        x = center.x + (pos.x - center.x) * pulse,
                        y = center.y + (pos.y - center.y) * pulse
                    )
                    drawCircle(
                        color = CyanPrimary,
                        radius = 3.dp.toPx(),
                        center = packetPos
                    )
                }

                // Agent node outer ring (priority weight indicator)
                val nodeRadius = (13f + agent.priority * 0.8f).dp.toPx()
                drawCircle(
                    color = surfaceColor,
                    radius = nodeRadius,
                    center = pos
                )
                drawCircle(
                    color = nodeColor,
                    radius = nodeRadius,
                    center = pos,
                    style = Stroke(width = 2.8.dp.toPx())
                )
                drawCircle(
                    color = nodeColor.copy(alpha = 0.28f),
                    radius = nodeRadius * (agent.currentLoadPct.coerceIn(10, 100) / 100f),
                    center = pos
                )
            }

            // Central Hub
            drawCircle(
                color = CyanPrimary.copy(alpha = 0.18f),
                radius = 28.dp.toPx() + (pulse * 6.dp.toPx()),
                center = center
            )
            drawCircle(
                color = surfaceColor,
                radius = 22.dp.toPx(),
                center = center
            )
            drawCircle(
                color = CyanPrimary,
                radius = 22.dp.toPx(),
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Hub label
        Text(
            text = "CORE",
            style = MaterialTheme.typography.labelSmall,
            color = CyanPrimary,
            modifier = Modifier.align(Alignment.Center)
        )

        if (agents.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "أضف وكيلين أو أكثر لتفعيل الربط التلقائي",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AgentLoadComparisonChart(
    agents: List<CloudAgent>,
    modifier: Modifier = Modifier
) {
    if (agents.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "لا توجد بيانات وكلاء للرسم البياني بعد",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        agents.take(6).forEach { agent ->
            val barColor = when (agent.status) {
                AgentStatus.ACTIVE -> if (agent.currentLoadPct >= 80) AmberWarning else CyanPrimary
                AgentStatus.DEGRADED -> AmberWarning
                else -> CoralError
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${agent.currentLoadPct}% | ${agent.avgLatencyMs}ms | أولوية ${agent.priority}",
                        style = MaterialTheme.typography.labelSmall,
                        color = barColor
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                ) {
                    drawRoundRect(
                        color = trackColor,
                        size = size,
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    val fraction = (agent.currentLoadPct.coerceIn(2L, 100L)) / 100f
                    drawRoundRect(
                        color = barColor,
                        size = Size(width = size.width * fraction, height = size.height),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }
        }
    }
}
