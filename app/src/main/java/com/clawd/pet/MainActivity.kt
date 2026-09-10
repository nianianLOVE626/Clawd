package com.clawd.pet

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink=Color(0xFF5A4650)
private val Rose=Color(0xFFD88FAA)
private val Cream=Color(0xFFFFF8FB)
private val Lilac=Color(0xFFF1E8F5)

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        AppState.init(this)
        setContent{Screen()}
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==ScreenVision.REQUEST_CODE){
            ScreenVision.acceptResult(resultCode,data)
        }
    }

    private fun overlay(){
        if(!Settings.canDrawOverlays(this))startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        else startService(Intent(this,ClawdOverlayService::class.java))
    }
    private fun notifications(){startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))}

    @Composable fun Screen(){
        var chatProvider by remember{mutableStateOf(AppState.chatProvider)}
        var url by remember{mutableStateOf(AppState.apiUrl)}
        var key by remember{mutableStateOf(AppState.apiKey)}
        var model by remember{mutableStateOf(AppState.model)}
        var mcp by remember{mutableStateOf(AppState.mcpUrl)}
        var token by remember{mutableStateOf(AppState.mcpToken)}
        var mcpTransport by remember{mutableStateOf(AppState.mcpTransport)}
        var sseUrl by remember{mutableStateOf(AppState.mcpSseUrl)}
        var sseMsgUrl by remember{mutableStateOf(AppState.mcpSseMessageUrl)}
        var ttsProvider by remember{mutableStateOf(AppState.ttsProvider)}
        var ttsUrl by remember{mutableStateOf(AppState.ttsUrl)}
        var ttsKey by remember{mutableStateOf(AppState.ttsKey)}
        var ttsModel by remember{mutableStateOf(AppState.ttsModel)}
        var voiceId by remember{mutableStateOf(AppState.voiceId)}
        var ttsEnabled by remember{mutableStateOf(AppState.ttsEnabled)}
        var vision by remember{mutableStateOf(AppState.visionEnabled)}
        var mode by remember{mutableStateOf(AppState.visionMode)}
        var interval by remember{mutableIntStateOf(AppState.visionInterval)}
        var proactive by remember{mutableStateOf(AppState.proactiveEnabled)}
        var mcpStatus by remember{mutableStateOf("未连接")}
        var mcpTools by remember{mutableStateOf(0)}
        MaterialTheme(colorScheme=lightColorScheme(primary=Rose,background=Cream,onBackground=Ink)){
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Cream,Lilac)))){
                androidx.compose.foundation.rememberScrollState().let{ scrollState ->
                Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(scrollState)){
                    Text("Clawd",fontSize=34.sp,fontWeight=FontWeight.Bold)
                    Text("soft companion · aware of your world",color=Rose)
                    Spacer(Modifier.height(18.dp))

                    Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(Color.White.copy(.88f))){
                        Column(Modifier.padding(18.dp)){
                            Text("视觉感知",fontWeight=FontWeight.Bold,fontSize=18.sp)
                            Text("让 Clawd 看懂你当前正在刷的视频内容。需要 Android 屏幕捕获授权。",
                                color=Ink.copy(.7f),fontSize=13.sp)
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                                Text(if(ScreenVision.hasConsent())"已授权" else "未授权")
                                Switch(checked=vision,onCheckedChange={
                                    vision=it;AppState.visionEnabled=it
                                    if(it)ScreenVision.requestPermission(this@MainActivity)
                                    else ScreenVision.clearConsent()
                                })
                            }
                            Text("模式", fontWeight=FontWeight.SemiBold)
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                listOf("on_demand" to "按需", "smart" to "智能低频").forEach { (v, label) ->
                                    FilterChip(
                                        selected = mode == v,
                                        onClick = { mode = v; AppState.visionMode = v },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            if (mode == "smart") {
                                Text("采样间隔：$interval 秒", fontSize=12.sp, color=Ink.copy(.7f))
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    listOf(10, 20, 30, 60).forEach { sec ->
                                        FilterChip(
                                            selected = interval == sec,
                                            onClick = { interval = sec; AppState.visionInterval = sec },
                                            label = { Text("${sec}s") }
                                        )
                                    }
                                }
                            }
                            Text("建议先使用“按需查看”。智能低频模式会先检测画面变化，只有明显变化时才请求视觉模型。",
                                color=Ink.copy(.55f),fontSize=11.sp)
                            val cacheDetail = CacheTracker.detailLabel(this@MainActivity)
                            Text(cacheDetail,
                                color=Ink.copy(.58f), fontSize=11.sp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(onClick={overlay()},modifier=Modifier.fillMaxWidth(),
                        shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=Rose)){
                        Text("开启悬浮 Clawd")
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("AI API",fontWeight=FontWeight.Bold,fontSize=19.sp)
                    ProviderPicker("聊天供应商",chatProvider,ProviderPresets.chat){ chosen ->
                        chatProvider=chosen.id; AppState.chatProvider=chosen.id
                        if(chosen.id!="custom"){ url=chosen.apiUrl; model=chosen.model; AppState.apiUrl=url; AppState.model=model }
                    }
                    Field("API URL",url){url=it;AppState.apiUrl=it}
                    Field("API Key",key,true){key=it;AppState.apiKey=it}
                    Field("Model",model){model=it;AppState.model=it}

                    Spacer(Modifier.height(8.dp))
                    Text("Ombre Brain MCP",fontWeight=FontWeight.Bold,fontSize=19.sp)
                    Field("MCP 地址",mcp){mcp=it;AppState.mcpUrl=it}
                    Field("Bearer Token（可选）",token,true){token=it;AppState.mcpToken=it}
                    Text("MCP 连接方式",fontWeight=FontWeight.SemiBold)
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        listOf("http" to "HTTP", "sse" to "SSE").forEach{(v,label)->FilterChip(selected=mcpTransport==v,onClick={mcpTransport=v;AppState.mcpTransport=v},label={Text(label)})}
                    }
                    if(mcpTransport=="sse"){
                        Field("SSE 地址",sseUrl){sseUrl=it;AppState.mcpSseUrl=it}
                        Field("SSE Message URL（可选）",sseMsgUrl){sseMsgUrl=it;AppState.mcpSseMessageUrl=it}
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("语音 API",fontWeight=FontWeight.Bold,fontSize=19.sp)
                    Text("供应商可以单独选择；voice_id 始终保留为独立配置。",fontSize=12.sp,color=Ink.copy(.62f))
                    ProviderPicker("语音供应商",ttsProvider,ProviderPresets.voice){ chosen ->
                        ttsProvider=chosen.id; AppState.ttsProvider=chosen.id
                        if(chosen.id!="custom_tts"){ ttsUrl=chosen.apiUrl; ttsModel=chosen.model; AppState.ttsUrl=ttsUrl; AppState.ttsModel=ttsModel }
                    }
                    Field("Voice API URL",ttsUrl){ttsUrl=it;AppState.ttsUrl=it}
                    Field("Voice API Key",ttsKey,true){ttsKey=it;AppState.ttsKey=it}
                    Field("Voice Model",ttsModel){ttsModel=it;AppState.ttsModel=it}
                    Field("voice_id",voiceId){voiceId=it;AppState.voiceId=it}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("启用语音气泡");Switch(checked=ttsEnabled,onCheckedChange={ttsEnabled=it;AppState.ttsEnabled=it})}

                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("主动陪伴")
                        Switch(checked = proactive, onCheckedChange = {
                            proactive = it; AppState.proactiveEnabled = it
                        })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={
                            mcpStatus="连接中…"
                            Thread {
                                val r=ClawdAgent.discover()
                                r.onSuccess { mcpTools=it.size; mcpStatus="已连接 · ${it.size} 个工具" }
                                    .onFailure { mcpStatus="连接失败：${it.message}" }
                            }.start()
                        },modifier=Modifier.weight(1f),shape=RoundedCornerShape(18.dp)){Text("连接 Ombre")}
                        OutlinedButton(onClick={
                            Thread {
                                val r=OmbreMcpClient.test()
                                r.onSuccess {mcpStatus=it}.onFailure{mcpStatus="失败：${it.message}"}
                            }.start()
                        },modifier=Modifier.weight(1f),shape=RoundedCornerShape(18.dp)){Text("测试 MCP")}
                    }
                    Text("Ombre Brain：$mcpStatus", fontSize=11.sp, color=Ink.copy(.62f))
                    OutlinedButton(onClick={notifications()},modifier=Modifier.fillMaxWidth(),
                        shape=RoundedCornerShape(18.dp)){Text("授权读取通知栏")}

                    Spacer(Modifier.height(10.dp))
                    Text("Clawd 将把屏幕视觉、通知、时间、音乐状态统一作为感知输入；真正发送到视觉模型前还会加入采样频率、App 白名单与隐私过滤。",
                        fontSize=12.sp,color=Ink.copy(.55f))
                    Spacer(Modifier.height(30.dp))
                }
                }
            }
        }
    }

    @Composable fun ProviderPicker(label:String,current:String,items:List<ProviderPreset>,onPick:(ProviderPreset)->Unit){
        var expanded by remember{mutableStateOf(false)}
        val selected=items.firstOrNull{it.id==current} ?: items.last()
        Box(Modifier.fillMaxWidth().padding(vertical=3.dp)){
            OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(17.dp)){
                Column(Modifier.fillMaxWidth()){ Text(label,fontSize=11.sp,color=Ink.copy(.6f)); Text(selected.name,fontWeight=FontWeight.SemiBold) }
            }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
                items.forEach{item->DropdownMenuItem(text={Column{Text(item.name);Text(item.note,fontSize=11.sp,color=Ink.copy(.55f))}},onClick={expanded=false;onPick(item)})}
            }
        }
    }

    @Composable fun Field(label:String,value:String,secret:Boolean=false,onChange:(String)->Unit){
        OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},
            modifier=Modifier.fillMaxWidth().padding(vertical=3.dp),singleLine=true,
            shape=RoundedCornerShape(17.dp),
            visualTransformation=if(secret)androidx.compose.ui.text.input.PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None)
    }
}
