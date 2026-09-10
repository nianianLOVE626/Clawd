package com.clawd.pet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object ChatApi {
    data class Reply(
        val text: String,
        val raw: String = "",
        val promptTokens: Long = 0,
        val cachedTokens: Long = 0,
        val cacheHitRate: Double = 0.0
    )

    fun chat(
        context: Context,
        userText: String,
        system: String = "你是 Clawd，一个会陪着用户的桌面小伙伴。",
        history: List<ClawdMessage> = emptyList()
    ): Result<Reply> {
        val base = AppState.apiUrl.trim().trimEnd('/')
        if (base.isBlank()) return Result.failure(IllegalStateException("还没有填写 API URL"))
        if (AppState.apiKey.isBlank()) return Result.failure(IllegalStateException("还没有填写 API Key"))
        if (AppState.model.isBlank()) return Result.failure(IllegalStateException("还没有填写模型"))
        val endpoint = when (AppState.chatProvider) {
            "anthropic" -> if (base.endsWith("/messages")) base else "$base/messages"
            else -> if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        }
        val body = when (AppState.chatProvider) {
            "anthropic" -> {
                val msgs = org.json.JSONArray()
                history.takeLast(12).forEach { m ->
                    if (m.text.isNotBlank() && (m.role == "user" || m.role == "assistant"))
                        msgs.put(JSONObject().put("role", m.role).put("content", m.text))
                }
                msgs.put(JSONObject().put("role", "user").put("content", userText))
                JSONObject().put("model", AppState.model).put("max_tokens", 2048)
                    .put("system", system).put("messages", msgs)
            }
            else -> {
                val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
                history.takeLast(12).forEach { m ->
                    if (m.text.isNotBlank() && (m.role == "user" || m.role == "assistant"))
                        messages.put(JSONObject().put("role", m.role).put("content", m.text))
                }
                messages.put(JSONObject().put("role", "user").put("content", userText))
                JSONObject().put("model", AppState.model).put("messages", messages).put("stream", false)
            }
        }
        return runCatching {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 12000; readTimeout = 60000; doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (AppState.chatProvider == "anthropic") {
                    setRequestProperty("x-api-key", AppState.apiKey)
                    setRequestProperty("anthropic-version", "2023-06-01")
                } else {
                    setRequestProperty("Authorization", "Bearer ${AppState.apiKey}")
                }
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) error("聊天 API HTTP $code：${raw.take(900)}")
            val json = JSONObject(raw)
            val content = when (AppState.chatProvider) {
                "anthropic" -> {
                    val arr = json.optJSONArray("content")
                    arr?.optJSONObject(0)?.optString("text", "") ?: ""
                }
                else -> {
                    val msg = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    msg?.optString("content", "") ?: ""
                }
            }
            if (content.isBlank()) error("模型没有返回内容")

            val (prompt, cached) = extractCacheTokens(json)
            if (prompt > 0) {
                CacheTracker.record(context, prompt, cached)
            }
            val rate = if (prompt > 0) cached.toDouble() / prompt else 0.0
            Reply(content.trim(), raw, prompt, cached, rate)
        }
    }

    /**
     * 兼容多家供应商的 cached_tokens 提取：
     * - OpenAI: usage.prompt_tokens_details.cached_tokens
     * - DeepSeek: usage.prompt_cache_hit_tokens
     * - 通用 fallback: usage.cached_tokens
     */
    private fun extractCacheTokens(json: JSONObject): Pair<Long, Long> {
        val usage = json.optJSONObject("usage") ?: return 0L to 0L
        val prompt = usage.optLong("prompt_tokens", 0)

        // OpenAI 格式
        val details = usage.optJSONObject("prompt_tokens_details")
        val fromDetails = details?.optLong("cached_tokens", 0) ?: 0

        // DeepSeek 格式
        val fromDeepSeek = usage.optLong("prompt_cache_hit_tokens", 0)

        // 通用 fallback
        val fromGeneric = usage.optLong("cached_tokens", 0)

        val cached = maxOf(fromDetails, fromDeepSeek, fromGeneric)
        return prompt to cached
    }
}