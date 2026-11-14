package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WebhookDispatchResult(
    val success: Boolean,
    val statusCode: Int,
    val latencyMs: Long,
    val responseBody: String
)

class WebhookDispatcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun dispatchEvent(
        webhookUrl: String,
        apiToken: String,
        eventType: String,
        payloadJson: JSONObject
    ): WebhookDispatchResult = withContext(Dispatchers.IO) {
        val trimmedUrl = webhookUrl.trim()
        if (trimmedUrl.isBlank() || (!trimmedUrl.startsWith("https://") && !trimmedUrl.startsWith("http://"))) {
            return@withContext WebhookDispatchResult(
                success = false,
                statusCode = 0,
                latencyMs = 0L,
                responseBody = "يرجى إدخال رابط Webhook صالح يبدأ بـ https:// أو http://"
            )
        }

        val envelope = JSONObject().apply {
            put("specVersion", "1.0")
            put("source", "nexusmesh-android-orchestrator")
            put("eventType", eventType)
            put("timestampEpochMs", System.currentTimeMillis())
            put("data", payloadJson)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = envelope.toString().toRequestBody(mediaType)
        val requestBuilder = Request.Builder()
            .url(trimmedUrl)
            .post(body)
            .header("Content-Type", "application/json")
            .header("X-NexusMesh-Event", eventType)

        if (apiToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiToken")
        }

        val start = System.currentTimeMillis()
        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                val text = response.body?.string()?.take(500).orEmpty()
                WebhookDispatchResult(
                    success = response.isSuccessful,
                    statusCode = response.code,
                    latencyMs = elapsed,
                    responseBody = if (text.isBlank()) "HTTP ${response.code} ${response.message}" else text
                )
            }
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            WebhookDispatchResult(
                success = false,
                statusCode = 0,
                latencyMs = elapsed,
                responseBody = e.localizedMessage ?: "فشل الاتصال بنقطة النهاية الخارجية"
            )
        }
    }
}
