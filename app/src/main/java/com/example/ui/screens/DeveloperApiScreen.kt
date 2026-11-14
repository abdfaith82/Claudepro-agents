package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.data.model.ClusterSettings
import com.example.network.WebhookDispatchResult
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeveloperApiScreen(
    settings: ClusterSettings,
    isTestingWebhook: Boolean,
    webhookResult: WebhookDispatchResult?,
    onSaveWebhookUrl: (String) -> Unit,
    onRotateToken: () -> Unit,
    onTestWebhook: (url: String, token: String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var webhookUrl by remember(settings.webhookUrl) { mutableStateOf(settings.webhookUrl) }
    var docLanguage by remember { mutableStateOf("cURL") }

    val snippet = remember(docLanguage, settings.apiToken) {
        when (docLanguage) {
            "Kotlin" -> """
val client = OkHttpClient()
val payload = ""${'"'}
  {"title":"Sync Audit","category":"ANALYSIS","autoFailover":true}
""${'"'}.trimIndent()
val req = Request.Builder()
  .url("https://api.nexusmesh.cloud/v1/mesh/tasks")
  .header("Authorization", "Bearer ${settings.apiToken}")
  .post(payload.toRequestBody("application/json".toMediaType()))
  .build()
client.newCall(req).execute()
            """.trimIndent()
            "Python" -> """
import requests

resp = requests.post(
    "https://api.nexusmesh.cloud/v1/mesh/tasks",
    headers={"Authorization": "Bearer ${settings.apiToken}"},
    json={"title": "Sync Audit", "category": "ANALYSIS", "autoFailover": True},
    timeout=15,
)
print(resp.json())
            """.trimIndent()
            "JSON Schema" -> """
{
  "specVersion": "1.0",
  "eventType": "task.failover",
  "data": {
    "taskId": "uuid-v4",
    "fromAgent": "Vertex Primary",
    "toAgent": "Cloud Backup EU",
    "checkpointPreserved": true,
    "recoveryLatencyMs": 180
  }
}
            """.trimIndent()
            else -> """
curl -X POST https://api.nexusmesh.cloud/v1/mesh/tasks \
  -H "Authorization: Bearer ${settings.apiToken}" \
  -H "Content-Type: application/json" \
  -d '{"title":"Sync Audit","category":"ANALYSIS","autoFailover":true}'
            """.trimIndent()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. API Token & Webhook Integration Bridge
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
                            Icon(Icons.Default.Api, contentDescription = null, tint = CyanPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("بوابة التكامل البرمجي (REST & Webhooks)", style = MaterialTheme.typography.titleMedium)
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = EmeraldSecondary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("مفتاح المصادقة (Bearer Token)", style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        text = settings.apiToken.ifBlank { "nxm_initializing..." },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyanPrimary
                                    )
                                }
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(settings.apiToken))
                                    Toast.makeText(context, "تم نسخ مفتاح API", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ المفتاح")
                                }
                                IconButton(onClick = onRotateToken) {
                                    Icon(Icons.Default.Refresh, contentDescription = "تدوير المفتاح")
                                }
                            }
                        }

                        OutlinedTextField(
                            value = webhookUrl,
                            onValueChange = { webhookUrl = it },
                            label = { Text("رابط Webhook للأنظمة الخارجية (https://...)") },
                            placeholder = { Text("https://httpbin.org/post") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("webhook_url_input")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { onSaveWebhookUrl(webhookUrl) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("حفظ الرابط")
                            }
                            Button(
                                onClick = {
                                    val target = webhookUrl.ifBlank { "https://httpbin.org/post" }
                                    webhookUrl = target
                                    onTestWebhook(target, settings.apiToken)
                                },
                                enabled = !isTestingWebhook,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_webhook_button")
                            ) {
                                if (isTestingWebhook) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("اختبار الاتصال")
                                }
                            }
                        }

                        if (webhookResult != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (webhookResult.success) {
                                            "نجاح الاستجابة: HTTP ${webhookResult.statusCode} (${webhookResult.latencyMs}ms)"
                                        } else {
                                            "حالة الاتصال: HTTP ${webhookResult.statusCode} (${webhookResult.latencyMs}ms)"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (webhookResult.success) EmeraldSecondary else CoralError
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = webhookResult.responseBody,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Comprehensive Developer Documentation
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
                            Icon(Icons.Default.Code, contentDescription = null, tint = EmeraldSecondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("الوثائق التقنية الشاملة للمطورين (SDK & API)", style = MaterialTheme.typography.titleMedium)
                        }

                        EndpointDocItem(
                            method = "POST",
                            path = "/v1/mesh/tasks",
                            description = "إرسال مهمة ذكاء اصطناعي جديدة ليتم توجيهها تلقائياً حسب الأولوية والأعباء."
                        )
                        EndpointDocItem(
                            method = "GET",
                            path = "/v1/mesh/agents",
                            description = "استعلام الحالة الحية للوكلاء، حسابات Google المرتبطة، ونسبة التحميل الحالية."
                        )
                        EndpointDocItem(
                            method = "POST",
                            path = "/v1/mesh/failover",
                            description = "تبديل مسار التنفيذ يدوياً أو آلياً إلى وكيل احتياطي مع الحفاظ على نقطة الاستئناف."
                        )
                        EndpointDocItem(
                            method = "GET",
                            path = "/v1/mesh/reports/export?format=json|csv|md",
                            description = "تصدير حزم الأداء وسجل العمليات بصيغ متعددة للربط مع أنظمة المراقبة."
                        )

                        Text("أمثلة التكامل البرمجي:", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("cURL", "Kotlin", "Python", "JSON Schema").forEach { lang ->
                                FilterChip(
                                    selected = docLanguage == lang,
                                    onClick = { docLanguage = lang },
                                    label = { Text(lang) }
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = snippet,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(snippet))
                                        Toast.makeText(context, "تم نسخ الكود", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("نسخ مثال $docLanguage")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EndpointDocItem(
    method: String,
    path: String,
    description: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (method == "POST") EmeraldSecondary.copy(alpha = 0.2f) else CyanPrimary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = method,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (method == "POST") EmeraldSecondary else CyanPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
