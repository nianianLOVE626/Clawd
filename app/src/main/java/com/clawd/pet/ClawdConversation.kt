package com.clawd.pet

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.Executors

object ClawdConversation {
    private val io=Executors.newCachedThreadPool()

    fun send(context:Context,userText:String,onReply:(String)->Unit){
        ClawdChatStore.append(context,"user",userText)
        io.execute{
            val memoryResult=ClawdAgentSearch.sync(userText)
            val historyItems=ClawdChatStore.load(context).takeLast(12)
            val history=historyItems.joinToString("\n"){ "${it.role}: ${it.text}" }

            val system="""你是 Clawd，一个常驻手机桌面的亲密 AI 小伙伴。
你可以使用 Ombre Brain 提供的长期记忆。
只把记忆当作辅助背景，不要假装知道没有检索到的信息。
回复自然、简短、有陪伴感。
不要暴露内部工具、MCP、缓存、Agent 等实现细节。

相关长期记忆：
${memoryResult.take(6000)}

最近对话：
$history"""

            val reply=ChatApi.chat(context,userText,system,historyItems)
                .fold({ it.text }, { "我现在没连上 AI：${it.message}" })

            ClawdChatStore.append(context,"assistant",reply)

            // Conservative write policy: only explicit facts/preferences/requests.
            val candidate=extractMemoryCandidate(userText)
            if(candidate!=null){
                ClawdAgent.remember(candidate){}
            }
            onReply(reply)
        }
    }

    private fun extractMemoryCandidate(text:String):String? {
        val t=text.trim()
        if(t.length<3 || t.length>220)return null
        val triggers=listOf("请记住","记住","以后都","我喜欢","我不喜欢","我叫","我的生日","纪念日","我希望你","别忘了")
        return if(triggers.any{t.contains(it)}) t else null
    }
}

private object ClawdAgentSearch {
    fun sync(query:String):String {
        var result=""
        val lock=Object()
        ClawdAgent.searchMemory(query){
            synchronized(lock){
                result=it.getOrElse{""}
                lock.notify()
            }
        }
        synchronized(lock){
            if(result.isBlank())runCatching{lock.wait(9000)}
        }
        return result
    }
}
