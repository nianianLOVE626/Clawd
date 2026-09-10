package com.clawd.pet

import android.content.Context

object AppState {
    private var ctx: Context? = null
    fun init(c: Context) { ctx = c.applicationContext }
    fun context(): Context = ctx ?: error("AppState not initialized")
    private fun s() = ctx!!.getSharedPreferences("clawd", Context.MODE_PRIVATE)

    var mcpServerEnabled: Boolean get() = s().getBoolean("mcpServerEnabled", true); set(v)=s().edit().putBoolean("mcpServerEnabled",v).apply()
    var mcpServerPort: Int get() = s().getInt("mcpServerPort",18765); set(v)=s().edit().putInt("mcpServerPort",v).apply()
    var mcpBindHost: String get() = s().getString("mcpBindHost","0.0.0.0") ?: "0.0.0.0"; set(v)=s().edit().putString("mcpBindHost",v).apply()
    var mcpServerToken: String get() = s().getString("mcpServerToken","") ?: ""; set(v)=s().edit().putString("mcpServerToken",v).apply()
    var petImageUri: String get() = s().getString("petImageUri","") ?: ""; set(v)=s().edit().putString("petImageUri",v).apply()
    var petHappyUri: String get() = s().getString("petHappyUri","") ?: ""; set(v)=s().edit().putString("petHappyUri",v).apply()
    var petSadUri: String get() = s().getString("petSadUri","") ?: ""; set(v)=s().edit().putString("petSadUri",v).apply()
    var petSleepUri: String get() = s().getString("petSleepUri","") ?: ""; set(v)=s().edit().putString("petSleepUri",v).apply()
    var petTalkUri: String get() = s().getString("petTalkUri","") ?: ""; set(v)=s().edit().putString("petTalkUri",v).apply()
    var petSurpriseUri: String get() = s().getString("petSurpriseUri","") ?: ""; set(v)=s().edit().putString("petSurpriseUri",v).apply()

    var ttsProvider: String get() = s().getString("ttsProvider","custom_tts") ?: "custom_tts"; set(v)=s().edit().putString("ttsProvider",v).apply()
    var ttsUrl: String get() = s().getString("ttsUrl","") ?: ""; set(v)=s().edit().putString("ttsUrl",v).apply()
    var ttsKey: String get() = s().getString("ttsKey","") ?: ""; set(v)=s().edit().putString("ttsKey",v).apply()
    var ttsModel: String get() = s().getString("ttsModel","gpt-4o-mini-tts") ?: "gpt-4o-mini-tts"; set(v)=s().edit().putString("ttsModel",v).apply()
    var voiceId: String get() = s().getString("voice_id","") ?: ""; set(v)=s().edit().putString("voice_id",v).apply()
    var lastNotification: String get() = s().getString("lastNotification","") ?: ""; set(v)=s().edit().putString("lastNotification",v.take(2000)).apply()

    var ttsEnabled: Boolean get() = s().getBoolean("ttsEnabled",false); set(v)=s().edit().putBoolean("ttsEnabled",v).apply()
}
