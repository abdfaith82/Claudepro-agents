package com.example.ai

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "InteractionsVideoService"

enum class VideoAspectRatio(
    val apiValue: String,
    val labelAr: String,
    val ratioFloat: Float
) {
    LANDSCAPE_16_9("16:9", "أفقي 16:9", 16f / 9f),
    PORTRAIT_9_16("9:16", "عمودي 9:16", 9f / 16f)
}

data class GeneratedVideoResult(
    val interactionId: String,
    val localFilePath: String,
    val remoteUri: String,
    val aspectRatio: VideoAspectRatio,
    val prompt: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

class InteractionsVideoService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    suspend fun generateVideoFromText(
        context: Context,
        prompt: String,
        aspectRatio: VideoAspectRatio,
        onStatusUpdate: (String) -> Unit
    ): GeneratedVideoResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException(
                "مفتاح GEMINI_API_KEY غير مهيأ. يرجى إضافته في لوحة Secrets داخل Google AI Studio لتشغيل توليد الفيديو عبر Interactions API."
            )
        }

        val cleanPrompt = prompt.trim()
        require(cleanPrompt.isNotBlank()) { "يرجى إدخال وصف نصي لتوليد الفيديو." }

        onStatusUpdate("جاري إرسال طلب التوليد إلى gemini-omni-1.2-flash (${aspectRatio.apiValue})...")

        val payload = JSONObject().apply {
            put("model", VIDEO_MODEL_ID)
            put("store", true)
            put("background", true)
            put(
                "input",
                JSONArray().put(
                    JSONObject().apply {
                        put("type", "text")
                        put("text", cleanPrompt)
                    }
                )
            )
            put("response_modalities", JSONArray().put("video"))
            put(
                "response_format",
                JSONObject().apply {
                    put("type", "video")
                    put("delivery", "uri")
                    put("resolution", "720p")
                    put("aspect_ratio", aspectRatio.apiValue)
                }
            )
        }

        val createRequest = Request.Builder()
            .url(INTERACTIONS_ENDPOINT)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        var interactionJson = executeJsonRequest(createRequest)
        val interactionId = interactionJson.optString("id")
            .ifBlank { interactionJson.optString("interaction_id") }

        var status = interactionJson.optString("status").lowercase()
        var pollAttempt = 0

        while ((status == "in_progress" || status == "queued" || status == "running" || status == "pending") &&
            interactionId.isNotBlank() &&
            pollAttempt < MAX_POLL_ATTEMPTS
        ) {
            pollAttempt++
            onStatusUpdate("جاري تصيير الفيديو على السحابة (${aspectRatio.apiValue})... محاولة $pollAttempt/$MAX_POLL_ATTEMPTS")
            delay(POLL_INTERVAL_MS)

            val pollRequest = Request.Builder()
                .url("$INTERACTIONS_ENDPOINT/$interactionId")
                .addHeader("x-goog-api-key", apiKey)
                .get()
                .build()

            interactionJson = executeJsonRequest(pollRequest)
            status = interactionJson.optString("status").lowercase()
        }

        if (status == "failed" || status == "cancelled" || status == "canceled") {
            val errorMsg = interactionJson.optJSONObject("error")?.optString("message")
                ?.ifBlank { null }
                ?: "فشل توليد الفيديو في خادم Interactions API (الحالة: $status)."
            throw IllegalStateException(errorMsg)
        }

        if (status != "completed" && status.isNotBlank() && extractVideoOutput(interactionJson) == null) {
            throw IllegalStateException("انتهت مهلة انتظار توليد الفيديو (الحالة الحالية: $status).")
        }

        val videoOutput = extractVideoOutput(interactionJson)
            ?: throw IllegalStateException("اكتمل الطلب ولكن لم يحتوي الرد على ملف فيديو في المخرجات.")

        onStatusUpdate("جاري تجهيز ملف الفيديو للعرض...")
        val localFile = saveVideoLocally(
            context = context,
            interactionId = interactionId.ifBlank { System.currentTimeMillis().toString() },
            apiKey = apiKey,
            output = videoOutput
        )

        GeneratedVideoResult(
            interactionId = interactionId.ifBlank { "local_${System.currentTimeMillis()}" },
            localFilePath = localFile.absolutePath,
            remoteUri = videoOutput.uri.orEmpty(),
            aspectRatio = aspectRatio,
            prompt = cleanPrompt
        )
    }

    private fun executeJsonRequest(request: Request): JSONObject {
        try {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val apiErr = runCatching {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message")
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                    throw IllegalStateException(
                        apiErr ?: "خطأ من Gemini Interactions API (HTTP ${response.code}): ${rawBody.take(180)}"
                    )
                }
                return JSONObject(rawBody)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Interactions API HTTP call failed", e)
            throw e
        }
    }

    private data class ExtractedVideo(
        val uri: String?,
        val base64Data: String?
    )

    private fun extractVideoOutput(root: JSONObject): ExtractedVideo? {
        val outputs = root.optJSONArray("outputs") ?: JSONArray()
        for (i in 0 until outputs.length()) {
            val item = outputs.optJSONObject(i) ?: continue
            val type = item.optString("type").lowercase()
            if (type == "video" || item.has("video")) {
                val videoObj = item.optJSONObject("video") ?: item
                val uri = videoObj.optString("uri")
                    .ifBlank { videoObj.optString("url") }
                    .ifBlank { item.optString("uri") }
                    .takeIf { it.isNotBlank() }
                val b64 = videoObj.optString("data")
                    .ifBlank { item.optString("data") }
                    .takeIf { it.isNotBlank() }
                if (uri != null || b64 != null) {
                    return ExtractedVideo(uri = uri, base64Data = b64)
                }
            }
        }
        return null
    }

    private fun saveVideoLocally(
        context: Context,
        interactionId: String,
        apiKey: String,
        output: ExtractedVideo
    ): File {
        val outDir = File(context.cacheDir, "generated_videos").apply { mkdirs() }
        val safeId = interactionId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val targetFile = File(outDir, "nexus_video_$safeId.mp4")

        if (!output.base64Data.isNullOrBlank()) {
            val bytes = Base64.decode(output.base64Data, Base64.DEFAULT)
            targetFile.writeBytes(bytes)
            return targetFile
        }

        val uri = requireNotNull(output.uri) { "رابط الفيديو فارغ" }
        val downloadRequest = Request.Builder()
            .url(uri)
            .addHeader("x-goog-api-key", apiKey)
            .get()
            .build()

        client.newCall(downloadRequest).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("تعذر تنزيل الفيديو المولّد (HTTP ${resp.code})")
            }
            val bodyBytes = resp.body?.bytes()
                ?: throw IllegalStateException("بيانات الفيديو المولّد فارغة.")
            targetFile.writeBytes(bodyBytes)
        }
        return targetFile
    }

    private companion object {
        const val VIDEO_MODEL_ID = "gemini-omni-1.2-flash"
        const val INTERACTIONS_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
        const val POLL_INTERVAL_MS = 4_000L
        const val MAX_POLL_ATTEMPTS = 45
    }
}
