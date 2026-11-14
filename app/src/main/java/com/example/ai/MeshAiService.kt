package com.example.ai

import android.util.Log
import com.example.data.model.CloudAgent
import com.example.data.model.OperationLog
import com.example.data.model.OrchestratedTask
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "MeshAiService"

data class StructuredReportOutput(
    val title: String,
    val executiveSummary: String,
    val recommendations: String,
    val healthScore: Int
)

class MeshAiService {

    private val streamingModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val reportSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Concise Arabic report title summarizing cluster status"),
            "executiveSummary" to Schema.string(description = "Detailed executive performance analysis in Arabic covering agent uptime, latency, and failover continuity"),
            "recommendations" to Schema.string(description = "Bullet-pointed actionable Arabic engineering recommendations for priority weights and load balancing"),
            "healthScore" to Schema.integer(description = "Overall cluster health score from 0 to 100")
        )
    )

    private val structuredReportModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = reportSchema
            }
        )
    }

    fun executeAgentWorkloadStream(
        agent: CloudAgent,
        taskTitle: String,
        taskPrompt: String,
        category: String,
        failoverCheckpoint: String? = null,
        failedAgentName: String? = null
    ): Flow<String> = flow {
        val systemContext = buildString {
            appendLine("أنت وكيل ذكاء اصطناعي سحابي في منظومة NexusMesh المترابطة.")
            appendLine("معرّف الوكيل المنفذ: ${agent.name} (${agent.provider} | ${agent.region})")
            appendLine("حساب Google المرتبط: ${agent.googleAccountEmail}")
            appendLine("التخصص: ${agent.role} | الأولوية: ${agent.priority}/10")
            appendLine("تصنيف المهمة: $category")
            if (!failoverCheckpoint.isNullOrBlank() && !failedAgentName.isNullOrBlank()) {
                appendLine("تنبيه استمرارية العمل (Failover Handoff): توقف الوكيل السابق ($failedAgentName) وتم تحويل المهمة إليك تلقائياً.")
                appendLine("نقطة الاستئناف (Checkpoint): $failoverCheckpoint")
                appendLine("أكمل التنفيذ مباشرة من نقطة التوقف دون إعادة أو فقدان للسياق.")
            }
            appendLine("قدّم إجابة هندسية دقيقة ومنظمة باللغة العربية مع مصطلحات تقنية واضحة.")
            appendLine()
            appendLine("عنوان المهمة: $taskTitle")
            appendLine("تفاصيل الطلب: $taskPrompt")
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                streamingModel.generateContentStream(systemContext).collect { chunk ->
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("لم يُرجع النموذج السحابي أي مخرجات.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI workload timed out after 60s", e)
            throw IllegalStateException("انتهت مهلة الاستجابة (60 ثانية) من الوكيل السحابي.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI workload execution failed", e)
            throw e
        }
    }

    suspend fun generateClusterPerformanceReport(
        agents: List<CloudAgent>,
        tasks: List<OrchestratedTask>,
        logs: List<OperationLog>,
        periodLabel: String
    ): StructuredReportOutput {
        val avgLatency = if (tasks.isEmpty()) {
            agents.map { it.avgLatencyMs }.average().toLong().coerceAtLeast(0L)
        } else {
            tasks.filter { it.latencyMs > 0 }.map { it.latencyMs }.average().toLong().coerceAtLeast(0L)
        }
        val failoverCount = tasks.sumOf { it.failoverCount }
        val activeAgents = agents.count { it.status == "ACTIVE" }

        val telemetryDigest = buildString {
            appendLine("الفترة التحليلية: $periodLabel")
            appendLine("إجمالي الوكلاء السحابيين: ${agents.size} (النشط حالياً: $activeAgents)")
            agents.forEach { a ->
                appendLine("- الوكيل: ${a.name} | المزود: ${a.provider} | Google: ${a.googleAccountEmail} | الحالة: ${a.status} | الأولوية: ${a.priority} | الحمل: ${a.currentLoadPct}% | المهام المنجزة: ${a.tasksCompleted} | التحولات: ${a.failoversHandled} | الكمون: ${a.avgLatencyMs}ms")
            }
            appendLine("إجمالي المهام المنسقة: ${tasks.size} | إجمالي عمليات التحول التلقائي (Failover): $failoverCount | متوسط الكمون: ${avgLatency}ms")
            appendLine("آخر أحداث السجل (${logs.take(10).size}):")
            logs.take(10).forEach { log ->
                appendLine("* [${log.severity}] ${log.eventType} (${log.agentName}): ${log.message}")
            }
        }

        try {
            val response = withTimeout(60_000L) {
                structuredReportModel.generateContent(
                    "بصفتك مهندس موثوقية أنظمة سحابية (SRE)، قم بتحليل قياسات شبكة وكلاء الذكاء الاصطناعي التالية واستخرج تقريراً دورياً احترافياً بصيغة JSON حسب المخطط:\n$telemetryDigest"
                )
            }
            val jsonText = response.text ?: throw IllegalStateException("استجابة التقرير فارغة.")
            val json = JSONObject(jsonText)
            return StructuredReportOutput(
                title = json.optString("title", "تقرير أداء منظومة الوكلاء ($periodLabel)"),
                executiveSummary = json.optString("executiveSummary", "اكتمل تحليل الأداء."),
                recommendations = json.optString("recommendations", "الحفاظ على توزيع الأولويات الحالي."),
                healthScore = json.optInt("healthScore", 92).coerceIn(0, 100)
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Report generation timed out", e)
            throw IllegalStateException("انتهت مهلة توليد التقرير التحليلي (60 ثانية).", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Structured report generation failed", e)
            throw e
        }
    }
}
