package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.ai.ChatTurnMessage
import com.example.ai.ChatbotRolePreset
import com.example.ai.GeminiChatModel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSecondary
import com.example.viewmodel.AiChatbotUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeminiChatbotSection(
    state: AiChatbotUiState,
    onSelectModel: (GeminiChatModel) -> Unit,
    onSelectPreset: (ChatbotRolePreset) -> Unit,
    onUpdateSystemInstruction: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var showRolePanel by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 840.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Model selector & Role System Instruction header card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = CyanPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "محرك الدردشة: ${state.selectedModel.displayName}",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { showRolePanel = !showRolePanel },
                            modifier = Modifier.testTag("toggle_system_instruction_button")
                        ) {
                            Text("الدور والتوجيه")
                            Icon(
                                imageVector = if (showRolePanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (state.messages.isNotEmpty()) {
                            IconButton(
                                onClick = onClearHistory,
                                modifier = Modifier.testTag("clear_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "بدء محادثة جديدة",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GeminiChatModel.entries.forEach { model ->
                        FilterChip(
                            selected = state.selectedModel == model,
                            onClick = { onSelectModel(model) },
                            label = { Text("${model.displayName} (${model.badge})") },
                            modifier = Modifier.testTag("chat_model_chip_${model.name.lowercase()}")
                        )
                    }
                }

                Text(
                    text = state.selectedModel.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AnimatedVisibility(visible = showRolePanel) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ChatbotRolePreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = state.selectedPreset == preset,
                                    onClick = { onSelectPreset(preset) },
                                    label = { Text(preset.title) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = state.systemInstruction,
                            onValueChange = onUpdateSystemInstruction,
                            label = { Text("تعليمات النظام (System Instruction)") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("system_instruction_input")
                        )
                    }
                }
            }
        }

        // Scrollable multi-turn thread
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (state.messages.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "محادثة متعددة الأدوار عبر محركات Gemini 4 و 3.1",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "اختر المحرك المناسب لمهمتك (Carbon للاستدلال العميق، Argon للتفاعل المعقد، Hydrogen للمهام العامة، وFlash Lite للسرعة الفائقة) مع الحفاظ الكامل على سياق المحادثة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onSelectModel(GeminiChatModel.CARBON)
                                    onSendMessage("صمم خطة Failover متعددة المناطق لثلاثة وكلاء سحابيين دون فقدان الجلسة.")
                                }
                            ) {
                                Text("خطة Failover (Carbon)")
                            }
                            OutlinedButton(
                                onClick = {
                                    onSelectModel(GeminiChatModel.FLASH_LITE)
                                    onSendMessage("أعطني 3 قواعد سريعة لمراقبة كمون الوكلاء.")
                                }
                            ) {
                                Text("ملخص سريع (Flash Lite)")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("chat_thread_list"),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        ChatBubbleItem(
                            message = msg,
                            isStreamingThis = state.isStreaming && msg.id == state.messages.lastOrNull()?.id && msg.role == "model",
                            onCopy = {
                                clipboard.setText(AnnotatedString(msg.text))
                                Toast.makeText(context, "تم نسخ الرسالة", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        if (state.errorMessage != null) {
            Surface(
                color = CoralError.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoralError,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // Composer Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("اكتب رسالتك للمحرك المختار...") },
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input")
            )
            IconButton(
                onClick = {
                    val toSend = inputText
                    inputText = ""
                    onSendMessage(toSend)
                },
                enabled = !state.isStreaming && inputText.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = CyanPrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .size(52.dp)
                    .testTag("chat_send_button")
            ) {
                if (state.isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال الرسالة")
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatTurnMessage,
    isStreamingThis: Boolean,
    onCopy: () -> Unit
) {
    val isUser = message.role == "user"
    val bubbleColor = when {
        message.isError -> CoralError.copy(alpha = 0.16f)
        isUser -> CyanPrimary.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            modifier = Modifier.widthIn(max = 620.dp)
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
                    Text(
                        text = if (isUser) "أنت" else message.modelId,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isError) CoralError else if (isUser) CyanPrimary else EmeraldSecondary
                    )
                    if (!isUser && message.text.isNotBlank()) {
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "نسخ الرد",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (message.text.isBlank() && isStreamingThis) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "جاري التفكير والتوليد...",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberWarning
                        )
                    }
                } else {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.isError) CoralError else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
