package com.example.ui.components

import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ai.GeneratedVideoResult
import com.example.ai.VideoAspectRatio
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSecondary
import com.example.viewmodel.VideoGenerationUiState
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoGenerationSection(
    state: VideoGenerationUiState,
    onSelectAspectRatio: (VideoAspectRatio) -> Unit,
    onGenerateVideo: (String) -> Unit,
    onSelectVideo: (GeneratedVideoResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var promptText by remember { mutableStateOf("") }

    val saveVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { destUri ->
        val sourcePath = state.activeVideo?.localFilePath
        if (destUri != null && !sourcePath.isNullOrBlank()) {
            runCatching {
                val srcFile = File(sourcePath)
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    srcFile.inputStream().use { input -> input.copyTo(out) }
                }
                Toast.makeText(context, "تم حفظ الفيديو بصيغة MP4", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "تعذر حفظ ملف الفيديو", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 840.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                        Icon(Icons.Default.SmartDisplay, contentDescription = null, tint = CyanPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("توليد الفيديو من النص", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "gemini-omni-1.2-flash • Gemini Interactions API",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSecondary
                            )
                        }
                    }

                    if (!state.isApiKeyConfigured) {
                        Surface(
                            color = AmberWarning.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "يتطلب محرك Interactions API تهيئة GEMINI_API_KEY في لوحة Secrets داخل Google AI Studio لتشغيل توليد الفيديو السحابي.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberWarning,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Text("نسبة أبعاد الفيديو (Aspect Ratio):", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VideoAspectRatio.entries.forEach { ratio ->
                            FilterChip(
                                selected = state.selectedAspectRatio == ratio,
                                onClick = { onSelectAspectRatio(ratio) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (ratio == VideoAspectRatio.LANDSCAPE_16_9) {
                                            Icons.Default.CropLandscape
                                        } else {
                                            Icons.Default.CropPortrait
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                label = { Text(ratio.labelAr) },
                                modifier = Modifier.testTag("aspect_ratio_${ratio.apiValue}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("وصف المشهد المطلوب توليده بالفيديو") },
                        placeholder = { Text("مثال: جولة سينمائية داخل مركز بيانات سحابي متوهج بالألياف الضوئية...") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_prompt_input")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "لقطة جوية لمركز بيانات سحابي ليلي بأضواء سيان مترابطة",
                            "شبكة ثلاثية الأبعاد لعقد ذكاء اصطناعي تنقل البيانات بسلاسة"
                        ).forEach { suggestion ->
                            FilterChip(
                                selected = false,
                                onClick = { promptText = suggestion },
                                label = { Text(suggestion.take(36) + "...") }
                            )
                        }
                    }

                    Button(
                        onClick = { onGenerateVideo(promptText) },
                        enabled = !state.isGenerating && promptText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("generate_video_button")
                    ) {
                        if (state.isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري توليد الفيديو...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("توليد فيديو (${state.selectedAspectRatio.apiValue})")
                        }
                    }

                    if (state.isGenerating && state.statusLabel.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = state.statusLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = CyanPrimary
                            )
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
                }
            }
        }

        // Active video player card respecting 16:9 or 9:16
        state.activeVideo?.let { video ->
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "معاينة الفيديو المولّد (${video.aspectRatio.apiValue})",
                                style = MaterialTheme.typography.titleSmall,
                                color = EmeraldSecondary
                            )
                            OutlinedButton(
                                onClick = {
                                    saveVideoLauncher.launch("nexusmesh_${video.interactionId}.mp4")
                                },
                                modifier = Modifier.testTag("save_generated_video_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حفظ MP4")
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            val playerModifier = if (video.aspectRatio == VideoAspectRatio.PORTRAIT_9_16) {
                                Modifier
                                    .widthIn(max = 260.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(9f / 16f)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                            }

                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        val controller = MediaController(ctx)
                                        controller.setAnchorView(this)
                                        setMediaController(controller)
                                        setVideoURI(Uri.fromFile(File(video.localFilePath)))
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = true
                                            start()
                                        }
                                    }
                                },
                                update = { view ->
                                    view.setVideoURI(Uri.fromFile(File(video.localFilePath)))
                                    view.start()
                                },
                                modifier = playerModifier
                            )
                        }

                        Text(
                            text = video.prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (state.history.isNotEmpty()) {
            item {
                Text(
                    text = "سجل الفيديوهات المولّدة (${state.history.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(state.history, key = { it.interactionId }) { item ->
                Card(
                    onClick = { onSelectVideo(item) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = CyanPrimary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.prompt,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Text(
                                text = "النسبة: ${item.aspectRatio.apiValue} • المعرف: ${item.interactionId.take(12)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
