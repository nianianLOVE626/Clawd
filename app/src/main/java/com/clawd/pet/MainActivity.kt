package com.clawd.pet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import android.content.ClipboardManager
import android.content.ClipData
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink=Color(0xFF5A4650); private val Rose=Color(0xFFD88FAA); private val Cream=Color(0xFFFFF8FB); private val Lilac=Color(0xFFF1E8F5)

class MainActivity:ComponentActivity(){
    private var pickTarget="default"
    private val pickImage=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri:Uri?->
        uri?:return@registerForActivityResult
        runCatching{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        when(pickTarget){"default"->AppState.petImageUri=uri.toString();"happy"->AppState.petHappyUri=uri.toString();"sad"->AppState.petSadUri=uri.toString();"sleep"->AppState.petSleepUri=uri.toString();"talk"->AppState.petTalkUri=uri.toString();"surprise"->AppState.petSurpriseUri=uri.toString()}
        Toast.makeText(this,"形象已保存",Toast.LENGTH_SHORT).show()
    }
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);AppState.init(this);setContent{Screen()}}
    private fun startPet(){if(!Settings.canDrawOverlays(this))startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))else{AppState.mcpServerEnabled=true;startService(Intent(this,ClawdOverlayService::class.java));Toast.makeText(this,"Clawd 已出现在桌面",Toast.LENGTH_SHORT).show()}}
    private fun pick(which:String){pickTarget=which;pickImage.launch(arrayOf("image/png","image/jpeg","image/webp"))}

    @Composable fun Screen(){
        var serverEnabled by remember{mutableStateOf(AppState.mcpServerEnabled)};var port by remember{mutableStateOf(AppState.mcpServerPort.toString())};var token by remember{mutableStateOf(AppState.mcpServerToken)}
        var ttsProvider by remember{mutableStateOf(AppState.ttsProvider)};var ttsUrl by remember{mutableStateOf(AppState.ttsUrl)};var ttsKey by remember{mutableStateOf(AppState.ttsKey)};var ttsModel by remember{mutableStateOf(AppState.ttsModel)};var voiceId by remember{mutableStateOf(AppState.voiceId)};var ttsEnabled by remember{mutableStateOf(AppState.ttsEnabled)}
        MaterialTheme(colorScheme=lightColorScheme(primary=Rose,background=Cream,onBackground=Ink)){
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Cream,Lilac)))){
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)){Text("Clawd",fontSize=34.sp,fontWeight=FontWeight.Bold,color=Ink);Text("Operit AI 的虚拟身体",fontSize=15.sp,color=Rose);Spacer(Modifier.height(16.dp))
                    Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(Color.White.copy(.9f))){Column(Modifier.padding(18.dp)){Text("虚拟形象",fontSize=19.sp,fontWeight=FontWeight.Bold,color=Ink);Text("支持一张默认形象，也可以为不同状态分别上传图片。",fontSize=12.sp,color=Ink.copy(.62f));Spacer(Modifier.height(8.dp));ImageButton("默认 / 待机",AppState.petImageUri){pick("default")};ImageButton("开心",AppState.petHappyUri){pick("happy")};ImageButton("难过",AppState.petSadUri){pick("sad")};ImageButton("睡觉",AppState.petSleepUri){pick("sleep")};ImageButton("说话",AppState.petTalkUri){pick("talk")};ImageButton("惊讶",AppState.petSurpriseUri){pick("surprise")}}
                    }
                    Spacer(Modifier.height(12.dp));Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(Color.White.copy(.9f))){Column(Modifier.padding(18.dp)){Text("Clawd MCP",fontSize=19.sp,fontWeight=FontWeight.Bold,color=Ink);Text("不配置第二套 AI。Operit 里的 AI 直接通过 MCP 控制这个身体。",fontSize=12.sp,color=Ink.copy(.62f));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("启动本机 MCP");Switch(checked=serverEnabled,onCheckedChange={serverEnabled=it;AppState.mcpServerEnabled=it;if(it)startPet()else stopService(Intent(this@MainActivity,ClawdOverlayService::class.java))})};CopyField("HTTP MCP 地址",ClawdMcpServer.endpoint());CopyField("SSE 地址",ClawdMcpServer.sseEndpoint());Field("端口",port,editable=true){v->port=v.filter{it.isDigit()};v.toIntOrNull()?.takeIf{it in 1024..65535}?.let{AppState.mcpServerPort=it}};Field("Bearer Token（可选）",token,true,editable=true){token=it;AppState.mcpServerToken=it};Text(if(ClawdMcpServer.isRunning())"MCP 状态：运行中" else "MCP 状态：未启动",color=Rose,fontSize=12.sp);Spacer(Modifier.height(8.dp));Button(onClick={startPet()},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=Rose)){Text("开启桌面 Clawd")}}
                    }
                    Spacer(Modifier.height(12.dp));Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(Color.White.copy(.9f))){Column(Modifier.padding(18.dp)){Text("语音",fontSize=19.sp,fontWeight=FontWeight.Bold,color=Ink);Text("Operit 决定说什么，Clawd 负责播放声音。",fontSize=12.sp,color=Ink.copy(.62f));ProviderPicker(ttsProvider){ttsProvider=it;AppState.ttsProvider=it};Field("Voice API URL",ttsUrl,editable=true){ttsUrl=it;AppState.ttsUrl=it};Field("Voice API Key",ttsKey,true,editable=true){ttsKey=it;AppState.ttsKey=it};Field("Voice Model",ttsModel,editable=true){ttsModel=it;AppState.ttsModel=it};Field("voice_id",voiceId,editable=true){voiceId=it;AppState.voiceId=it};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("启用语音");Switch(checked=ttsEnabled,onCheckedChange={ttsEnabled=it;AppState.ttsEnabled=it})}}
                    }
                    Spacer(Modifier.height(10.dp));Text("连接：Operit AI → Clawd MCP → 虚拟形象。",fontSize=11.sp,color=Ink.copy(.55f))
                }
            }
        }
    }
    @Composable private fun ImageButton(label:String,uri:String,onClick:()->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(if(uri.isBlank())"$label：未设置" else "$label：已设置",fontSize=13.sp,color=Ink);OutlinedButton(onClick=onClick,shape=RoundedCornerShape(16.dp)){Text("选择")}}}
    @Composable private fun CopyField(label:String,value:String){
        Column(Modifier.fillMaxWidth()){
            OutlinedTextField(value=value,onValueChange={},label={Text(label)},singleLine=true,readOnly=true,modifier=Modifier.fillMaxWidth().padding(vertical=4.dp),shape=RoundedCornerShape(18.dp))
            OutlinedButton(onClick={val cm=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager;cm.setPrimaryClip(ClipData.newPlainText(label,value));Toast.makeText(this@MainActivity,"已复制 MCP 地址",Toast.LENGTH_SHORT).show()},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("复制地址")}
        }
    }
    @Composable private fun Field(label:String,value:String,secret:Boolean=false,editable:Boolean=true,onChange:(String)->Unit){
        OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},singleLine=true,readOnly=!editable,visualTransformation=if(secret)androidx.compose.ui.text.input.PasswordVisualTransformation()else androidx.compose.ui.text.input.VisualTransformation.None,modifier=Modifier.fillMaxWidth().padding(vertical=4.dp),shape=RoundedCornerShape(18.dp))
    }
    @Composable private fun ProviderPicker(value:String,onPick:(String)->Unit){var expanded by remember{mutableStateOf(false)};val items=listOf("custom_tts" to "自定义","openai_tts" to "OpenAI TTS","elevenlabs" to "ElevenLabs","minimax_tts" to "MiniMax Voice");Box(Modifier.fillMaxWidth()){OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Text("TTS 供应商：${items.firstOrNull{it.first==value}?.second?:value}")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){items.forEach{(id,name)->DropdownMenuItem(text={Text(name)},onClick={expanded=false;onPick(id)})}}}}
}
