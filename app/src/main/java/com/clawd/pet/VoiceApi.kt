package com.clawd.pet

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object VoiceApi {
    fun synthesize(context: Context, text: String): Result<File> {
        val base = AppState.ttsUrl.trim().trimEnd('/')
        if (base.isBlank()) return Result.failure(IllegalStateException("还没有填写语音 API URL"))
        if (AppState.voiceId.isBlank()) return Result.failure(IllegalStateException("还没有填写 voice_id"))
        val endpoint = when (AppState.ttsProvider) {
            "elevenlabs" -> "$base/text-to-speech/${java.net.URLEncoder.encode(AppState.voiceId, "UTF-8")}"
            else -> if (base.endsWith("/audio/speech")) base else "$base/audio/speech"
        }
        return runCatching {
            val body = when (AppState.ttsProvider) {
                "elevenlabs" -> JSONObject()
                    .put("text", text)
                    .put("model_id", AppState.ttsModel)
                    .toString()
                else -> JSONObject()
                    .put("model", AppState.ttsModel)
                    .put("voice", AppState.voiceId)
                    .put("input", text)
                    .put("response_format", "mp3")
                    .toString()
            }
            val c = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10000; readTimeout = 60000; doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (AppState.ttsKey.isNotBlank()) {
                    if (AppState.ttsProvider == "elevenlabs") setRequestProperty("xi-api-key", AppState.ttsKey)
                    else setRequestProperty("Authorization", "Bearer ${AppState.ttsKey}")
                }
            }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                error("TTS API HTTP $code：${err.take(500)}")
            }
            val file = File(context.cacheDir, "clawd_voice_${System.currentTimeMillis()}.mp3")
            c.inputStream.use { input -> file.outputStream().use { out -> input.copyTo(out) } }
            file
        }
    }
}