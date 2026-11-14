package com.example.export

import com.example.data.model.CloudAgent
import com.example.data.model.OperationLog
import com.example.data.model.OrchestratedTask
import com.example.data.model.PerformanceReport
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    JSON("json", "application/json", "JSON"),
    CSV("csv", "text/csv", "CSV"),
    MARKDOWN("md", "text/markdown", "Markdown")
}

object ReportExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    fun exportClusterSnapshot(
        format: ExportFormat,
        agents: List<CloudAgent>,
        tasks: List<OrchestratedTask>,
        logs: List<OperationLog>,
        latestReport: PerformanceReport?
    ): String = when (format) {
        ExportFormat.JSON -> buildJson(agents, tasks, logs, latestReport)
        ExportFormat.CSV -> buildCsv(agents, tasks, logs)
        ExportFormat.MARKDOWN -> buildMarkdown(agents, tasks, logs, latestReport)
    }

    private fun buildJson(
        agents: List<CloudAgent>,
        tasks: List<OrchestratedTask>,
        logs: List<OperationLog>,
        latestReport: PerformanceReport?
    ): String {
        val root = JSONObject()
        root.put("exportedAt", isoFormat.format(Date()))
        root.put("platform", "NexusMesh Cloud AI Orchestrator")

        val agentsArr = JSONArray()
        agents.forEach { a ->
            agentsArr.put(JSONObject().apply {
                put("id", a.id)
                put("name", a.name)
                put("provider", a.provider)
                put("googleAccount", a.googleAccountEmail)
                put("region", a.region)
                put("status", a.status)
                put("priority", a.priority)
                put("currentLoadPct", a.currentLoadPct)
                put("tasksCompleted", a.tasksCompleted)
                put("failoversHandled", a.failoversHandled)
                put("avgLatencyMs", a.avgLatencyMs)
            })
        }
        root.put("agents", agentsArr)

        val tasksArr = JSONArray()
        tasks.forEach { t ->
            tasksArr.put(JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("category", t.category)
                put("status", t.status)
                put("assignedAgent", t.assignedAgentName)
                put("failoverFrom", t.failoverFromAgentName)
                put("failoverCount", t.failoverCount)
                put("latencyMs", t.latencyMs)
            })
        }
        root.put("tasks", tasksArr)

        val logsArr = JSONArray()
        logs.take(50).forEach { l ->
            logsArr.put(JSONObject().apply {
                put("eventType", l.eventType)
                put("severity", l.severity)
                put("agentName", l.agentName)
                put("message", l.message)
                put("latencyMs", l.latencyMs)
            })
        }
        root.put("logs", logsArr)

        if (latestReport != null) {
            root.put("latestReport", JSONObject().apply {
                put("title", latestReport.title)
                put("period", latestReport.periodLabel)
                put("healthScore", latestReport.healthScore)
                put("executiveSummary", latestReport.executiveSummary)
                put("recommendations", latestReport.recommendations)
            })
        }
        return root.toString(2)
    }

    private fun buildCsv(
        agents: List<CloudAgent>,
        tasks: List<OrchestratedTask>,
        logs: List<OperationLog>
    ): String = buildString {
        appendLine("# AGENTS TELEMETRY")
        appendLine("agent_id,name,provider,google_account,region,status,priority,load_pct,tasks_completed,failovers_handled,avg_latency_ms")
        agents.forEach { a ->
            appendLine(
                listOf(
                    a.id, escapeCsv(a.name), a.provider, escapeCsv(a.googleAccountEmail),
                    a.region, a.status, a.priority, a.currentLoadPct,
                    a.tasksCompleted, a.failoversHandled, a.avgLatencyMs
                ).joinToString(",")
            )
        }
        appendLine()
        appendLine("# ORCHESTRATED TASKS")
        appendLine("task_id,title,category,status,assigned_agent,failover_from,failover_count,latency_ms")
        tasks.forEach { t ->
            appendLine(
                listOf(
                    t.id, escapeCsv(t.title), t.category, t.status,
                    escapeCsv(t.assignedAgentName), escapeCsv(t.failoverFromAgentName),
                    t.failoverCount, t.latencyMs
                ).joinToString(",")
            )
        }
        appendLine()
        appendLine("# AUDIT LOGS")
        appendLine("event_type,severity,agent_name,latency_ms,message")
        logs.take(50).forEach { l ->
            appendLine(
                listOf(
                    l.eventType, l.severity, escapeCsv(l.agentName),
                    l.latencyMs, escapeCsv(l.message)
                ).joinToString(",")
            )
        }
    }

    private fun buildMarkdown(
        agents: List<CloudAgent>,
        tasks: List<OrchestratedTask>,
        logs: List<OperationLog>,
        latestReport: PerformanceReport?
    ): String = buildString {
        appendLine("# NexusMesh Performance & Failover Report")
        appendLine("Generated: `${isoFormat.format(Date())}`")
        appendLine()
        if (latestReport != null) {
            appendLine("## ${latestReport.title}")
            appendLine("- **Period:** ${latestReport.periodLabel}")
            appendLine("- **Health Score:** ${latestReport.healthScore}/100")
            appendLine()
            appendLine("### Executive Summary")
            appendLine(latestReport.executiveSummary)
            appendLine()
            appendLine("### Recommendations")
            appendLine(latestReport.recommendations)
            appendLine()
        }
        appendLine("## Linked Cloud Agents (${agents.size})")
        appendLine("| Agent | Provider | Google Account | Status | Priority | Load | Failovers |")
        appendLine("|---|---|---|---|---|---|---|")
        agents.forEach { a ->
            appendLine("| ${a.name} | ${a.provider} | ${a.googleAccountEmail} | ${a.status} | ${a.priority}/10 | ${a.currentLoadPct}% | ${a.failoversHandled} |")
        }
        appendLine()
        appendLine("## Recent Orchestrated Tasks (${tasks.size})")
        tasks.take(15).forEach { t ->
            appendLine("- **${t.title}** `[${t.status}]` -> Agent: `${t.assignedAgentName}` (${t.latencyMs}ms, Failovers: ${t.failoverCount})")
        }
        appendLine()
        appendLine("## Recent Audit Logs (${logs.size})")
        logs.take(20).forEach { l ->
            appendLine("- `[${l.severity}]` **${l.eventType}** (${l.agentName}): ${l.message}")
        }
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\"", "\"\"").replace("\n", " ")
        return "\"$clean\""
    }
}
