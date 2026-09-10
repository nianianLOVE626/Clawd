package com.clawd.pet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ClawdMessage(val role:String, val text:String, val time:Long, val cacheHitRate:Double=0.0)

object ClawdChatStore {
    private const val KEY="clawd-chat-history"
    private fun prefs(c:Context)=c.getSharedPreferences("clawd",Context.MODE_PRIVATE)

    fun load(c:Context):List<ClawdMessage>{
        val a=runCatching{JSONArray(prefs(c).getString(KEY,"[]")!!)}.getOrElse{return emptyList()}
        return buildList {
            for(i in 0 until a.length()){
                val o=a.optJSONObject(i) ?: continue
                add(ClawdMessage(o.optString("role","user"),o.optString("text",""),o.optLong("time",0),o.optDouble("cacheHitRate",0.0)))
            }
        }
    }

    fun append(c:Context, role:String, text:String, cacheHitRate:Double=0.0){
        val list=load(c).toMutableList()
        list.add(ClawdMessage(role,text,System.currentTimeMillis(),cacheHitRate))
        while(list.size>40) list.removeAt(0)
        val a=JSONArray()
        list.forEach{m->a.put(JSONObject().put("role",m.role).put("text",m.text).put("time",m.time).put("cacheHitRate",m.cacheHitRate))}
        prefs(c).edit().putString(KEY,a.toString()).apply()
    }

    fun clear(c:Context){prefs(c).edit().remove(KEY).apply()}
}
