package com.clawd.pet

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class VisionCompanion(
    private val context: Context,
    private val onProactive: (String) -> Unit
) {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var lastInsight = ""
    private var lastSentAt = 0L
    private var tick: Runnable? = null

    fun start() {
        if (!AppState.visionEnabled || AppState.visionMode != "smart") return
        if (!running.compareAndSet(false,true)) return
        scheduleNext()
    }

    private fun scheduleNext(){
        if(!running.get()) return
        val delay=AppState.visionInterval.coerceIn(10,120)*1000L
        tick=Runnable{sample();scheduleNext()}
        main.postDelayed(tick!!,delay)
    }

    private fun sample(){
        io.execute{
            val captured=ScreenVision.captureIfChanged(context) ?: return@execute
            val (jpeg,change)=captured
            val vision=VisionApi.analyze(jpeg,
                """你是 Clawd 的视觉观察器。
只描述当前屏幕中最明显、最可靠的视频/内容。
重点：主体、动作、场景、清晰字幕。
不要猜身份、地点或看不清的细节。
如果只是广告、页面切换或没有值得分享的内容，返回 NO_INSIGHT。
只输出简短中文描述。场景变化强度：$change"""
            ).getOrNull() ?: return@execute
            if(vision.trim().equals("NO_INSIGHT",true)) return@execute

            val now=System.currentTimeMillis()
            if(vision.trim().equals(lastInsight.trim(),true)) return@execute
            if(now-lastSentAt < AppState.proactiveCooldownMin*60_000L) return@execute

            val decision=ChatApi.chat(context,
                """屏幕观察：
$vision

请决定你现在是否应该主动打扰用户。
只有真的有趣、相关、自然，或者和你们正在聊天的内容明显有关时才主动说。
如果不值得打扰，只返回 SILENT。
如果值得，只返回一句自然、简短、像亲近桌宠会说的话，不要解释规则。""",
                """你是 Clawd 的主动陪伴决策器。
你可以主动和用户说一句话，但不要频繁打扰。
用户正在使用其他 App 刷视频，所以默认保持安静；只有内容确实值得分享才说。"""
            ).getOrNull()?.text ?: return@execute

            if(decision.equals("SILENT",true) || decision.isBlank()) return@execute
            lastInsight=vision
            lastSentAt=now
            main.post { onProactive(decision.trim()) }
        }
    }

    fun stop(){
        running.set(false)
        tick?.let{main.removeCallbacks(it)}
        tick=null
        io.shutdownNow()
    }
}
