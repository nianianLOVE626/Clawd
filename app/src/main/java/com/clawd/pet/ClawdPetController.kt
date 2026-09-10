package com.clawd.pet

import android.os.Handler
import android.os.Looper

object ClawdPetController {
    @Volatile private var service: ClawdOverlayService? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile var lastMood: String = "neutral"
    @Volatile var lastAction: String = "idle"
    @Volatile var lastMessage: String = ""
    @Volatile var lastUpdatedAt: Long = 0L

    fun attach(s: ClawdOverlayService) { service = s }
    fun detach(s: ClawdOverlayService) { if (service === s) service = null }
    fun say(text: String): Boolean { if(text.isBlank()) return false; lastMessage=text; lastUpdatedAt=System.currentTimeMillis(); main.post{service?.showMcpSpeech(text)}; return service!=null }
    fun showBubble(text:String,seconds:Int=10):Boolean { if(text.isBlank()) return false; lastMessage=text; lastUpdatedAt=System.currentTimeMillis(); main.post{service?.showMcpSpeech(text,seconds)}; return service!=null }
    fun setMood(mood:String):Boolean { if(mood.isBlank()) return false; lastMood=mood; lastUpdatedAt=System.currentTimeMillis(); main.post{service?.setPetMood(mood)}; return service!=null }
    fun setAction(action:String):Boolean { if(action.isBlank()) return false; lastAction=action; lastUpdatedAt=System.currentTimeMillis(); main.post{service?.setPetAction(action)}; return service!=null }
    fun state():Map<String,Any> = mapOf("online" to (service!=null),"mood" to lastMood,"action" to lastAction,"last_message" to lastMessage,"updated_at" to lastUpdatedAt,"image_uri_set" to AppState.petImageUri.isNotBlank(),"happy_image_set" to AppState.petHappyUri.isNotBlank(),"sad_image_set" to AppState.petSadUri.isNotBlank(),"sleep_image_set" to AppState.petSleepUri.isNotBlank(),"talk_image_set" to AppState.petTalkUri.isNotBlank(),"surprise_image_set" to AppState.petSurpriseUri.isNotBlank())
}
