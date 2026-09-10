package com.clawd.pet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 统一缓存统计，替代原来的 CacheStatsStore + CacheTracker 双重统计。
 * 保留最近 100 条请求级别的记录，支持按 session 筛选和趋势查看。
 */
object CacheTracker {
    data class Entry(
        val promptTokens: Long,
        val cachedTokens: Long,
        val timestamp: Long,
        val sessionId: String
    ) {
        val hitRate: Double get() = if (promptTokens > 0) cachedTokens.toDouble() / promptTokens else 0.0
    }

    data class Summary(
        val requests: Int,
        val totalPrompt: Long,
        val totalCached: Long,
        val recentEntries: List<Entry>
    ) {
        val hitRate: Double get() = if (totalPrompt > 0) totalCached.toDouble() / totalPrompt else 0.0
        val hitsCount: Int get() = recentEntries.count { it.cachedTokens > 0 }
    }

    private const val KEY = "clawd-cache-entries"
    private const val MAX_ENTRIES = 100
    private var currentSessionId: String = System.currentTimeMillis().toString()

    fun newSession() { currentSessionId = System.currentTimeMillis().toString() }
    fun sessionId() = currentSessionId

    private fun prefs(c: Context) = c.getSharedPreferences("clawd", Context.MODE_PRIVATE)

    fun record(c: Context, prompt: Long, cached: Long) {
        if (prompt <= 0) return
        val entries = loadEntries(c).toMutableList()
        entries.add(Entry(prompt, cached, System.currentTimeMillis(), currentSessionId))
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
        saveEntries(c, entries)
    }

    fun summary(c: Context): Summary {
        val entries = loadEntries(c)
        val totalPrompt = entries.sumOf { it.promptTokens }
        val totalCached = entries.sumOf { it.cachedTokens }
        return Summary(entries.size, totalPrompt, totalCached, entries)
    }

    fun summaryForSession(c: Context, sessionId: String? = null): Summary {
        val sid = sessionId ?: currentSessionId
        val entries = loadEntries(c).filter { it.sessionId == sid }
        val totalPrompt = entries.sumOf { it.promptTokens }
        val totalCached = entries.sumOf { it.cachedTokens }
        return Summary(entries.size, totalPrompt, totalCached, entries)
    }

    /** 最近 n 条的命中率趋势 */
    fun recentTrend(c: Context, n: Int = 5): List<Double> {
        return loadEntries(c).takeLast(n).map { it.hitRate }
    }

    fun label(rate: Double): String = "缓存 ${"%.0f".format(rate * 100)}%"

    fun detailLabel(c: Context): String {
        val s = summary(c)
        if (s.requests == 0) return "暂无缓存数据"
        val trend = recentTrend(c, 3)
        val trendStr = if (trend.size >= 2) {
            val diff = trend.last() - trend.first()
            when {
                diff > 0.05 -> " ↑"
                diff < -0.05 -> " ↓"
                else -> " →"
            }
        } else ""
        return "${s.totalCached}/${s.totalPrompt} tokens · ${label(s.hitRate)}$trendStr · ${s.requests}次请求"
    }

    fun clear(c: Context) { prefs(c).edit().remove(KEY).apply() }

    private fun loadEntries(c: Context): List<Entry> {
        val raw = prefs(c).getString(KEY, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(Entry(
                    o.optLong("p", 0),
                    o.optLong("c", 0),
                    o.optLong("t", 0),
                    o.optString("s", "")
                ))
            }
        }
    }

    private fun saveEntries(c: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject()
                .put("p", e.promptTokens)
                .put("c", e.cachedTokens)
                .put("t", e.timestamp)
                .put("s", e.sessionId))
        }
        prefs(c).edit().putString(KEY, arr.toString()).apply()
    }
}
