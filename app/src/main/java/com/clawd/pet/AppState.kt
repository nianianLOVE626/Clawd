package com.clawd.pet

import android.content.Context

object AppState {
    private var ctx: Context? = null
    fun init(c: Context) { ctx = c.applicationContext }
    fun context(): Context = ctx ?: error("AppState not initialized")
    private fun s() = ctx!!.getSharedPreferences("clawd", Context.MODE_PRIVATE)

    var chatProvider: String get() = s().getString("chatProvider","custom") ?: "custom"; set(v)=s().edit().putString("chatProvider",v).apply()
    var apiUrl: String get() = s().getString("apiUrl","") ?: ""; set(v)=s().edit().putString("apiUrl",v).apply()
    var apiKey: String get() = s().getString("apiKey","") ?: ""; set(v)=s().edit().putString("apiKey",v).apply()
    var model: String get() = s().getString("model","") ?: ""; set(v)=s().edit().putString("model",v).apply()
    var mcpUrl: String get() = s().getString("mcpUrl","") ?: ""; set(v)=s().edit().putString("mcpUrl",v).apply()
    var mcpToken: String get() = s().getString("mcpToken","") ?: ""; set(v)=s().edit().putString("mcpToken",v).apply()
    var mcpTransport: String get() = s().getString("mcpTransport","http") ?: "http"; set(v)=s().edit().putString("mcpTransport",v).apply()
    var mcpSseUrl: String get() = s().getString("mcpSseUrl","") ?: ""; set(v)=s().edit().putString("mcpSseUrl",v).apply()
    var mcpSseMessageUrl: String get() = s().getString("mcpSseMessageUrl","") ?: ""; set(v)=s().edit().putString("mcpSseMessageUrl",v).apply()
    var ttsProvider: String get() = s().getString("ttsProvider","custom_tts") ?: "custom_tts"; set(v)=s().edit().putString("ttsProvider",v).apply()
    var ttsUrl: String get() = s().getString("ttsUrl","") ?: ""; set(v)=s().edit().putString("ttsUrl",v).apply()
    var ttsKey: String get() = s().getString("ttsKey","") ?: ""; set(v)=s().edit().putString("ttsKey",v).apply()
    var ttsModel: String get() = s().getString("ttsModel","gpt-4o-mini-tts") ?: "gpt-4o-mini-tts"; set(v)=s().edit().putString("ttsModel",v).apply()
    var voiceId: String get() = s().getString("voice_id","") ?: ""; set(v)=s().edit().putString("voice_id",v).apply()
    var ttsEnabled: Boolean get() = s().getBoolean("ttsEnabled",false); set(v)=s().edit().putBoolean("ttsEnabled",v).apply()

    var visionEnabled: Boolean get() = s().getBoolean("visionEnabled",false); set(v)=s().edit().putBoolean("visionEnabled",v).apply()
    var visionInterval: Int get() = s().getInt("visionInterval",20); set(v)=s().edit().putInt("visionInterval",v).apply()
    var visionMode: String get() = s().getString("visionMode","on_demand") ?: "on_demand"; set(v)=s().edit().putString("visionMode",v).apply()
    var visionAppWhitelist: String get() = s().getString("visionWhitelist","") ?: ""; set(v)=s().edit().putString("visionWhitelist",v).apply()

    var proactiveEnabled: Boolean get() = s().getBoolean("proactiveEnabled",true); set(v)=s().edit().putBoolean("proactiveEnabled",v).apply()
    var proactiveCooldownMin: Int get() = s().getInt("proactiveCooldownMin",30); set(v)=s().edit().putInt("proactiveCooldownMin",v).apply()

    var petImagePath: String get() = s().getString("petImagePath","") ?: ""; set(v)=s().edit().putString("petImagePath",v).apply()
    var petSize: Int get() = s().getInt("petSize",120); set(v)=s().edit().putInt("petSize",v).apply()
}
