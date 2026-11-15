package com.example.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiChatService"

enum class GeminiChatModel(
    val modelId: String,
    val displayName: String,
    val badge: String,
    val description: String
) {
    HYDROGEN(
        modelId = "gemini-4-hydrogen",
        displayName = "Gemini 4 Hydrogen",
        badge = "عام",
        description = "متوازن للمهام العامة وإدارة الشبكة"
    ),
    CARBON(
        modelId = "gemini-4-carbon",
        displayName = "Gemini 4 Carbon",
        badge = "استدلال عميق",
        description = "مخصص للتفكير العميق والتحليل الهندسي"
    ),
    ARGON(
        modelId = "gemini-4-argon",
        displayName = "Gemini 4 Argon",
        badge = "مهام معقدة",
        description = "للمهام التفاعلية شديدة التعقيد والتنسيق"
    ),
    FLASH_LITE(
        modelId = "gemini-3.1-flash-lite",
        displayName = "Gemini 3.1 Flash Lite",
        badge = "فائق السرعة",
        description = "للاستجابات الفورية والمهام السريعة"
    )
}

enum class ChatbotRolePreset(
    val title: String,
    val systemInstruction: String
) {
    CLOUD_ARCHITECT(
        title = "مهندس سحابة",
        systemInstruction = "أنت مهندس معماري سحابي أول (Cloud Architect) في منصة NexusMesh. ساعد المستخدم في تصميم البنية السحابية، موازنة الأحمال، وضمان التحول التلقائي للوكلاء بدقة ووضوح."
    ),
    REASONING_AUDITOR(
        title = "محلل عميق",
        systemInstruction = "أنت محلل أنظمة وذكاء اصطناعي متخصص في الاستدلال العميق وكشف الأسباب الجذرية للمشكلات التقنية خطوة بخطوة مع توصيات عملية."
    ),
    INTERACTIVE_COORDINATOR(
        title = "منسق تفاعلي",
        systemInstruction = "أنت منسق تفاعلي للمهام المعقدة؛ تساعد المستخدم في تقسيم المهام المتداخلة إلى مراحل متسلسلة وتوزيع الأدوار بين الوكلاء بكفاءة."
    ),
    FAST_ASSISTANT(
        title = "مساعد سريع",
        systemInstruction = "أنت مساعد تقني سريع البديهة. قدّم إجابات مركزة ومباشرة وقابلة للتنفيذ الفوري بأقل عدد من الأسطر."
    )
}

data class ChatTurnMessage(
    val id: String,
    val role: String, // "user" or "model"
    val text: String,
    val modelId: String = GeminiChatModel.HYDROGEN.modelId,
    val timestampMs: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

class GeminiChatService {

    fun streamReply(
        model: GeminiChatModel,
        systemInstruction: String,
        history: List<ChatTurnMessage>,
        userPrompt: String
    ): Flow<String> = flow {
        val cleanSystem = systemInstruction.trim().ifBlank {
            ChatbotRolePreset.CLOUD_ARCHITECT.systemInstruction
        }
        val generativeModel = Firebase.ai.generativeModel(
            modelName = model.modelId,
            systemInstruction = content { text(cleanSystem) }
        )

        val sdkHistory = history
            .filter { !it.isError && it.text.isNotBlank() }
            .map { msg ->
                val sdkRole = if (msg.role == "model") "model" else "user"
                content(role = sdkRole) { text(msg.text) }
            }

        val chat = generativeModel.startChat(history = sdkHistory)
        val accumulated = StringBuilder()

        try {
            withTimeout(60_000L) {
                chat.sendMessageStream(userPrompt).collect { chunk ->
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("لم يُرجع النموذج أي نص.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Chat stream timed out for ${model.modelId}", e)
            throw IllegalStateException("انتهت مهلة الاستجابة (60 ثانية) من ${model.displayName}.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chat stream failed for ${model.modelId}", e)
            throw e
        }
    }
}
