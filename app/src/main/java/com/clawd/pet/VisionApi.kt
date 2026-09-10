package com.clawd.pet

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object VisionApi {
    fun analyze(jpeg: ByteArray, prompt: String): Result<String> {
        val url0 = AppState.apiUrl.trim().trimEnd('/')
        if (url0.isBlank()) return Result.failure(IllegalStateException("还没有填写 API URL"))
        if (AppState.apiKey.isBlank()) return Result.failure(IllegalStateException("还没有填写 API Key"))
        if (AppState.model.isBlank()) return Result.failure(IllegalStateException("还没有填写视觉模型"))

        val endpoint = if (url0.endsWith("/chat/completions")) url0 else "$url0/chat/completions"
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)

        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image_url").put(
                "image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64")
            ))

        val message = JSONObject()
            .put("role", "user")
            .put("content", content)

        val body = JSONObject()
            .put("model", AppState.model)
            .put("messages", JSONArray().put(message))
            .put("stream", false)
            .put("max_tokens", 500)

        return runCatching {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${AppState.apiKey}")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) error("视觉 API HTTP $code：${text.take(800)}")
            val json = JSONObject(text)
            val msg = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            val contentValue = msg?.optString("content", "") ?: ""
            if (contentValue.isBlank()) error("视觉模型没有返回内容")
            contentValue
        }
    }
}
