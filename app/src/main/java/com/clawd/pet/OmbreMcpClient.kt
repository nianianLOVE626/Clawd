package com.clawd.pet

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

data class McpTool(
    val name:String,
    val description:String,
    val inputSchema:JSONObject
)

object OmbreMcpClient {
    private fun endpoint() = if(AppState.mcpTransport=="sse") (AppState.mcpSseUrl.ifBlank{AppState.mcpUrl}).trim().trimEnd('/') else AppState.mcpUrl.trim().trimEnd('/')
    private fun conn(): HttpURLConnection {
        require(endpoint().isNotBlank()){"还没有填写 Ombre Brain MCP 地址"}
        return (URL(endpoint()).openConnection() as HttpURLConnection).apply {
            requestMethod="POST"
            connectTimeout=10000
            readTimeout=30000
            doOutput=true
            setRequestProperty("Content-Type","application/json")
            setRequestProperty("Accept","application/json, text/event-stream")
            if(AppState.mcpToken.isNotBlank())
                setRequestProperty("Authorization","Bearer ${AppState.mcpToken}")
        }
    }

    private fun rpc(method:String, params:JSONObject=JSONObject()):JSONObject {
        val id=UUID.randomUUID().toString()
        val body=JSONObject().put("jsonrpc","2.0").put("id",id).put("method",method).put("params",params)
        val c=conn()
        c.outputStream.use{it.write(body.toString().toByteArray(StandardCharsets.UTF_8))}
        val code=c.responseCode
        val stream=if(code in 200..299)c.inputStream else c.errorStream
        val raw=stream?.bufferedReader()?.use{it.readText()}?:""
        if(code !in 200..299) error("MCP HTTP $code：${raw.take(900)}")
        val parsed=parseRpc(raw)
        if(parsed.has("error")) error(parsed.getJSONObject("error").optString("message","MCP error"))
        return parsed.optJSONObject("result") ?: parsed
    }

    private fun parseRpc(raw:String):JSONObject {
        val trimmed=raw.trim()
        if(trimmed.startsWith("{"))return JSONObject(trimmed)
        val data=trimmed.lines().firstOrNull{it.trimStart().startsWith("data:")}?.substringAfter("data:")?.trim()
        return if(data?.startsWith("{")==true)JSONObject(data) else error("无法解析 MCP 响应")
    }

    private fun sseRequest(): Result<String> = runCatching {
        val c=(URL(endpoint()).openConnection() as HttpURLConnection).apply{
            requestMethod="GET";connectTimeout=10000;readTimeout=15000
            setRequestProperty("Accept","text/event-stream")
            if(AppState.mcpToken.isNotBlank())setRequestProperty("Authorization","Bearer ${AppState.mcpToken}")
        }
        val code=c.responseCode
        if(code !in 200..299) error("MCP SSE HTTP $code")
        val lines=c.inputStream.bufferedReader().readLines()
        lines.firstOrNull{it.startsWith("data:")}
            ?.removePrefix("data:")?.trim()
            ?: error("MCP SSE 没有返回 data")
    }

    fun initialize():Result<JSONObject> = runCatching {
        rpc("initialize",JSONObject()
            .put("protocolVersion","2025-06-18")
            .put("capabilities",JSONObject())
            .put("clientInfo",JSONObject().put("name","Clawd").put("version","0.8")))
    }

    fun listTools():Result<List<McpTool>> = runCatching {
        val r=rpc("tools/list")
        val a=r.optJSONArray("tools") ?: JSONArray()
        buildList {
            for(i in 0 until a.length()){
                val o=a.optJSONObject(i) ?: continue
                add(McpTool(
                    o.optString("name"),
                    o.optString("description"),
                    o.optJSONObject("inputSchema") ?: JSONObject().put("type","object")
                ))
            }
        }
    }

    fun callTool(name:String,args:JSONObject):Result<String> = runCatching {
        val r=rpc("tools/call",JSONObject().put("name",name).put("arguments",args))
        val content=r.optJSONArray("content") ?: return@runCatching r.toString()
        buildString {
            for(i in 0 until content.length()){
                val c=content.optJSONObject(i) ?: continue
                if(c.optString("type")=="text") append(c.optString("text"))
            }
        }.ifBlank{r.toString()}
    }

    fun test():Result<String> = runCatching {
        if(AppState.mcpTransport=="sse"){ sseRequest().getOrThrow(); return@runCatching "SSE 已连接" }
        initialize().getOrThrow()
        val tools=listTools().getOrThrow()
        "连接成功，共发现 ${tools.size} 个 MCP 工具"
    }
}
