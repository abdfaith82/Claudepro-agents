package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.VoiceChat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ai.ChatbotRolePreset
import com.example.ai.GeminiChatModel
import com.example.ai.GeneratedVideoResult
import com.example.ai.VideoAspectRatio
import com.example.ui.components.GeminiChatbotSection
import com.example.ui.components.VideoGenerationSection
import com.example.viewmodel.AiChatbotUiState
import com.example.viewmodel.VideoGenerationUiState

@Composable
fun AiEnginesScreen(
    chatState: AiChatbotUiState,
    videoState: VideoGenerationUiState,
    onSelectModel: (GeminiChatModel) -> Unit,
    onSelectPreset: (ChatbotRolePreset) -> Unit,
    onUpdateSystemInstruction: (String) -> Unit,
    onSendChatMessage: (String) -> Unit,
    onClearChatHistory: () -> Unit,
    onSelectAspectRatio: (VideoAspectRatio) -> Unit,
    onGenerateVideo: (android.content.Context, String) -> Unit,
    onSelectVideo: (GeneratedVideoResult) -> Unit
) {
    val context = LocalContext.current
    var subTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TabRow(
            selectedTabIndex = subTab,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 840.dp)
        ) {
            Tab(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                text = { Text("محادثة Gemini") },
                icon = { Icon(Icons.Default.VoiceChat, contentDescription = null) },
                modifier = Modifier.testTag("ai_tab_chatbot")
            )
            Tab(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                text = { Text("استوديو الفيديو") },
                icon = { Icon(Icons.Default.SmartDisplay, contentDescription = null) },
                modifier = Modifier.testTag("ai_tab_video")
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (subTab == 0) {
                GeminiChatbotSection(
                    state = chatState,
                    onSelectModel = onSelectModel,
                    onSelectPreset = onSelectPreset,
                    onUpdateSystemInstruction = onUpdateSystemInstruction,
                    onSendMessage = onSendChatMessage,
                    onClearHistory = onClearChatHistory
                )
            } else {
                VideoGenerationSection(
                    state = videoState,
                    onSelectAspectRatio = onSelectAspectRatio,
                    onGenerateVideo = { prompt -> onGenerateVideo(context, prompt) },
                    onSelectVideo = onSelectVideo
                )
            }
        }
    }
}
