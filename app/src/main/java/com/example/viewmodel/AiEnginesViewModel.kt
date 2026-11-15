package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ChatTurnMessage
import com.example.ai.ChatbotRolePreset
import com.example.ai.GeminiChatModel
import com.example.ai.GeminiChatService
import com.example.ai.GeneratedVideoResult
import com.example.ai.InteractionsVideoService
import com.example.ai.VideoAspectRatio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "AiEnginesViewModel"

data class AiChatbotUiState(
    val selectedModel: GeminiChatModel = GeminiChatModel.HYDROGEN,
    val selectedPreset: ChatbotRolePreset = ChatbotRolePreset.CLOUD_ARCHITECT,
    val systemInstruction: String = ChatbotRolePreset.CLOUD_ARCHITECT.systemInstruction,
    val messages: List<ChatTurnMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val errorMessage: String? = null
)

data class VideoGenerationUiState(
    val selectedAspectRatio: VideoAspectRatio = VideoAspectRatio.LANDSCAPE_16_9,
    val isGenerating: Boolean = false,
    val statusLabel: String = "",
    val errorMessage: String? = null,
    val activeVideo: GeneratedVideoResult? = null,
    val history: List<GeneratedVideoResult> = emptyList(),
    val isApiKeyConfigured: Boolean = false
)

class AiEnginesViewModel(
    private val chatService: GeminiChatService = GeminiChatService(),
    private val videoService: InteractionsVideoService = InteractionsVideoService()
) : ViewModel() {

    private val _chatState = MutableStateFlow(AiChatbotUiState())
    val chatState: StateFlow<AiChatbotUiState> = _chatState.asStateFlow()

    private val _videoState = MutableStateFlow(
        VideoGenerationUiState(isApiKeyConfigured = videoService.isApiKeyConfigured())
    )
    val videoState: StateFlow<VideoGenerationUiState> = _videoState.asStateFlow()

    fun selectChatModel(model: GeminiChatModel) {
        _chatState.update { it.copy(selectedModel = model, errorMessage = null) }
    }

    fun selectRolePreset(preset: ChatbotRolePreset) {
        _chatState.update {
            it.copy(
                selectedPreset = preset,
                systemInstruction = preset.systemInstruction,
                errorMessage = null
            )
        }
    }

    fun updateSystemInstruction(instruction: String) {
        _chatState.update { it.copy(systemInstruction = instruction) }
    }

    fun clearChatHistory() {
        _chatState.update {
            it.copy(
                messages = emptyList(),
                isStreaming = false,
                errorMessage = null
            )
        }
    }

    fun sendChatMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isBlank() || _chatState.value.isStreaming) return

        val snapshot = _chatState.value
        val priorHistory = snapshot.messages
        val userMsg = ChatTurnMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            text = prompt,
            modelId = snapshot.selectedModel.modelId
        )
        val modelReplyId = UUID.randomUUID().toString()
        val placeholderReply = ChatTurnMessage(
            id = modelReplyId,
            role = "model",
            text = "",
            modelId = snapshot.selectedModel.modelId
        )

        _chatState.update {
            it.copy(
                messages = it.messages + userMsg + placeholderReply,
                isStreaming = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                chatService.streamReply(
                    model = snapshot.selectedModel,
                    systemInstruction = snapshot.systemInstruction,
                    history = priorHistory,
                    userPrompt = prompt
                ).collect { accumulatedText ->
                    _chatState.update { state ->
                        val updated = state.messages.map { msg ->
                            if (msg.id == modelReplyId) msg.copy(text = accumulatedText) else msg
                        }
                        state.copy(messages = updated)
                    }
                }
                _chatState.update { it.copy(isStreaming = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini chat error", e)
                val errText = e.localizedMessage ?: "تعذر الاتصال بمحرك Gemini."
                _chatState.update { state ->
                    val cleaned = state.messages.map { msg ->
                        if (msg.id == modelReplyId) {
                            msg.copy(
                                text = msg.text.ifBlank { errText },
                                isError = true
                            )
                        } else {
                            msg
                        }
                    }
                    state.copy(
                        messages = cleaned,
                        isStreaming = false,
                        errorMessage = errText
                    )
                }
            }
        }
    }

    fun selectAspectRatio(ratio: VideoAspectRatio) {
        _videoState.update { it.copy(selectedAspectRatio = ratio) }
    }

    fun selectActiveVideo(video: GeneratedVideoResult) {
        _videoState.update { it.copy(activeVideo = video, errorMessage = null) }
    }

    fun generateVideo(context: Context, rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isBlank() || _videoState.value.isGenerating) return

        val ratio = _videoState.value.selectedAspectRatio
        _videoState.update {
            it.copy(
                isGenerating = true,
                statusLabel = "جاري تهيئة طلب الفيديو عبر gemini-omni-1.2-flash...",
                errorMessage = null,
                isApiKeyConfigured = videoService.isApiKeyConfigured()
            )
        }

        viewModelScope.launch {
            try {
                val result = videoService.generateVideoFromText(
                    context = context.applicationContext,
                    prompt = prompt,
                    aspectRatio = ratio,
                    onStatusUpdate = { status ->
                        _videoState.update { it.copy(statusLabel = status) }
                    }
                )
                _videoState.update { state ->
                    state.copy(
                        isGenerating = false,
                        statusLabel = "تم توليد الفيديو بنجاح (${result.aspectRatio.apiValue})",
                        activeVideo = result,
                        history = listOf(result) + state.history
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Video generation failed", e)
                _videoState.update {
                    it.copy(
                        isGenerating = false,
                        statusLabel = "",
                        errorMessage = e.localizedMessage ?: "فشل توليد الفيديو."
                    )
                }
            }
        }
    }
}
