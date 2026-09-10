package com.clawd.pet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors

object ClawdMcpServer {
    private const val PROTOCOL = "2025-06-18"
    private const val SERVER_NAME = "Clawd Desktop Body"
    private const val SERVER_VERSION = "1.5.0"
    private val pool=Executors.newCachedThreadPool()
    @Volatile private var server:ServerSocket?=null
    @Volatile private var running=false
    fun start(context:Context):Result<Int> = runCatching{
        if(running) return@runCatching AppState.mcpServerPort
        val port=AppState.mcpServerPort.coerceIn(1024,65535)
        val ss=ServerSocket(port,32,java.net.InetAddress.getByName("127.0.0.1"))
        server=ss;running=true
        pool.execute{while(running){try{pool.execute{handle(ss.accept())}}catch(_:SocketException){break}catch(_:Throwable){}}}
        port
    }
    fun stop(){running=false;runCatching{server?.close()};server=null}
    fun isRunning()=running
    fun endpoint()="http://127.0.0.1:${AppState.mcpServerPort}/mcp"
    fun sseEndpoint()="http://127.0.0.1:${AppState.mcpServerPort}/sse"

    private fun handle(s:Socket){s.use{socket->
        socket.soTimeout=35000
        val r=BufferedReader(InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8))
        val line=r.readLine()?:return
        val parts=line.split(" ");if(parts.size<2)return
        val method=parts[0].uppercase();val target=parts[1];val headers=linkedMapOf<String,String>()
        while(true){val h=r.readLine()?:return;if(h.isEmpty())break;val i=h.indexOf(':');if(i>0)headers[h.substring(0,i).trim().lowercase()]=h.substring(i+1).trim()}
        if(method=="OPTIONS"){respond(socket,204,"application/json","");return}
        if(!authorized(headers)){respond(socket,401,"application/json",JSONObject().put("error","unauthorized").toString());return}
        val len=headers["content-length"]?.toIntOrNull()?:0
        val body=if(len>0)CharArray(len).also{r.read(it,0,len)}.concatToString() else ""
        val path=target.substringBefore('?')
        when{
            method=="GET"&&path=="/health"->respond(socket,200,"application/json",JSONObject().put("ok",true).put("running",running).put("name","Clawd Desktop Body").toString())
            method=="GET"&&path=="/mcp"->respond(socket,200,"application/json",JSONObject().put("transport","streamable-http").put("endpoint",endpoint()).put("protocolVersion",PROTOCOL).toString())
            method=="GET"&&path=="/sse"->handleSse(socket)
            method=="POST"&&(path=="/mcp"||path=="/message")->handleRpc(socket,body,headers)
            else->respond(socket,404,"application/json",JSONObject().put("error","not_found").toString())
        }
    }}
    private fun authorized(h:Map<String,String>):Boolean{val e=AppState.mcpServerToken.trim();if(e.isBlank())return true;return h["authorization"]?.removePrefix("Bearer ")?.trim()==e}
    private fun handleRpc(s:Socket,body:String,headers:Map<String,String>){
        val req=runCatching{JSONObject(body)}.getOrElse{return respond(s,400,"application/json",errorResponse(JSONObject.NULL,-32700,"Parse error").toString())}
        val method=req.optString("method")
        if(method.startsWith("notifications/")){respond(s,202,"application/json","");return}
        val response=rpc(req)
        val session=if(method=="initialize") UUID.randomUUID().toString() else headers["mcp-session-id"]
        respond(s,200,"application/json",response.toString(),session)
    }
    private fun handleSse(s:Socket){val session=UUID.randomUUID().toString();val endpointUrl="http://127.0.0.1:${AppState.mcpServerPort}/message?sessionId=$session";val payload="event: endpoint\ndata: $endpointUrl\n\n";val out=s.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\nCache-Control: no-cache\r\nConnection: keep-alive\r\nAccess-Control-Allow-Origin: *\r\n\r\n$payload").toByteArray(StandardCharsets.UTF_8));out.flush();runCatching{Thread.sleep(30000)}}

    private fun rpc(req:JSONObject):JSONObject{
        val id=req.opt("id");val method=req.optString("method")
        if(method.startsWith("notifications/"))return JSONObject().put("jsonrpc","2.0").put("id",JSONObject.NULL).put("result",JSONObject())
        val result=when(method){
            "initialize"->JSONObject()
                .put("protocolVersion", req.optJSONObject("params")?.optString("protocolVersion",PROTOCOL) ?: PROTOCOL)
                .put("capabilities",JSONObject().put("tools",JSONObject().put("listChanged",false)))
                .put("serverInfo",JSONObject().put("name",SERVER_NAME).put("version",SERVER_VERSION))
            "ping"->JSONObject()
            "tools/list"->JSONObject().put("tools",tools())
            "tools/call"->callTool(req.optJSONObject("params")?:JSONObject())
            else->return errorResponse(id,-32601,"Method not found: $method")
        }
        return JSONObject().put("jsonrpc","2.0").put("id",id?:JSONObject.NULL).put("result",result)
    }
    private fun tools()=JSONArray().apply{
        put(tool("clawd_say","让桌面上的 Clawd 说一句话",schema("text","string")))
        put(tool("clawd_show_bubble","在 Clawd 旁边显示一个气泡",schema("text","string","seconds","integer")))
        put(tool("clawd_set_mood","改变 Clawd 当前心情",schema("mood","string")))
        put(tool("clawd_set_action","改变 Clawd 当前动作",schema("action","string")))
        put(tool("clawd_get_state","读取桌面 Clawd 当前状态",JSONObject().put("type","object").put("properties",JSONObject())))
        put(tool("clawd_voice","让 Clawd 用配置好的语音 API 朗读文字",schema("text","string")))
    }
    private fun tool(n:String,d:String,s:JSONObject)=JSONObject().put("name",n).put("description",d).put("inputSchema",s)
    private fun schema(vararg p:String):JSONObject{val props=JSONObject();val req=JSONArray();var i=0;while(i+1<p.size){props.put(p[i],JSONObject().put("type",p[i+1]));req.put(p[i]);i+=2};return JSONObject().put("type","object").put("properties",props).put("required",req)}
    private fun callTool(p:JSONObject):JSONObject{val n=p.optString("name");val a=p.optJSONObject("arguments")?:JSONObject();return when(n){
        "clawd_say"->ok(if(ClawdPetController.say(a.optString("text")))"消息已显示" else "Clawd 未运行")
        "clawd_show_bubble"->ok(if(ClawdPetController.showBubble(a.optString("text"),a.optInt("seconds",10)))"气泡已显示" else "Clawd 未运行")
        "clawd_set_mood"->ok(if(ClawdPetController.setMood(a.optString("mood")))"心情已切换" else "Clawd 未运行")
        "clawd_set_action"->ok(if(ClawdPetController.setAction(a.optString("action")))"动作已切换" else "Clawd 未运行")
        "clawd_get_state"->JSONObject().put("content",JSONArray().put(JSONObject().put("type","text").put("text",JSONObject(ClawdPetController.state()).toString())))
        "clawd_voice"->{val text=a.optString("text");val f=VoiceApi.synthesize(AppState.context(),text).getOrNull();if(f!=null){ClawdPetController.showBubble("正在说：${text.take(40)}",8);VoicePlayer.play(f);ok("语音已播放")}else ok("语音生成失败")}
        else->JSONObject().put("isError",true).put("content",JSONArray().put(JSONObject().put("type","text").put("text","未知工具：$n")))
    }}
    private fun ok(t:String)=JSONObject().put("content",JSONArray().put(JSONObject().put("type","text").put("text",t)))
    private fun errorResponse(id:Any?,code:Int,msg:String)=JSONObject().put("jsonrpc","2.0").put("id",id?:JSONObject.NULL).put("error",JSONObject().put("code",code).put("message",msg))
    private fun respond(s:Socket,code:Int,type:String,body:String,sessionId:String?=null){
        val b=body.toByteArray(StandardCharsets.UTF_8)
        val reason=when(code){200->"OK";202->"Accepted";204->"No Content";400->"Bad Request";401->"Unauthorized";404->"Not Found";else->"Error"}
        val extra=if(!sessionId.isNullOrBlank())"Mcp-Session-Id: $sessionId\r\n" else ""
        val out:OutputStream=s.getOutputStream()
        out.write("HTTP/1.1 $code $reason\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${b.size}\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: Content-Type, Authorization, Accept, Mcp-Session-Id, MCP-Protocol-Version\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\n$extra\r\n".toByteArray(StandardCharsets.UTF_8))
        if(b.isNotEmpty()) out.write(b)
        out.flush()
    }
}
