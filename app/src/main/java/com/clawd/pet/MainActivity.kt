package com.clawd.pet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import android.content.ClipboardManager
import android.content.ClipData
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);AppState.init(this);requestRuntimePermissions();setContent{Screen()}}
    private fun requestRuntimePermissions(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),2001)
        }
    }
    override fun onResume(){
        super.onResume()
        if(AppState.mcpServerEnabled && Settings.canDrawOverlays(this) && !ClawdMcpServer.isRunning()){
            runCatching{startService(Intent(this,ClawdOverlayService::class.java))}
        }
    }
    private fun startPet(){
        if(!Settings.canDrawOverlays(this)){
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this,"请先允许 Clawd 显示在其他应用上层",Toast.LENGTH_LONG).show()
            return
        }
        AppState.mcpServerEnabled=true
        runCatching{startService(Intent(this,ClawdOverlayService::class.java))}.onSuccess{
            Toast.makeText(this,"Clawd 已启动",Toast.LENGTH_SHORT).show()
        }.onFailure{
            Toast.makeText(this,"Clawd 启动失败：${it.message ?: "未知错误"}",Toast.LENGTH_LONG).show()
        }
    }
    private fun pick(which:String){pickTarget=which;pickImage.launch(arrayOf("image/png","image/jpeg","image/webp"))}

    @Composable fun Screen(){
        var serverEnabled by remember{mutableStateOf(AppState.mcpServerEnabled)};var diagnostic by remember{mutableStateOf(ClawdMcpServer.selfTest())};var port by remember{mutableStateOf(AppState.mcpServerPort.toString())};var token by remember{mutableStateOf(AppState.mcpServerToken)};var bindLan by remember{mutableStateOf(AppState.mcpBindHost=="0.0.0.0")}
        var ttsProvider by remember{mutableStateOf(AppState.ttsProvider)};var ttsUrl by remember{mutableStateOf(AppState.ttsUrl)};var ttsKey by remember{mutableStateOf(AppState.ttsKey)};var ttsModel by remember{mutableStateOf(AppState.ttsModel)};var voiceId by remember{mutableStateOf(AppState.voiceId)};var ttsEnabled by remember{mutableStateOf(AppState.ttsEnabled)}
        MaterialTheme(colorScheme=lightColorScheme(primary=Rose,background=Cream,onBackground=Ink)){
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Cream,Lilac)))){
                Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())){Text("Clawd",fontSize=34.sp,fontWeight=FontWeight.Bold,color=Ink);Text("Operit AI 的虚拟身体",fontSize=15.sp,color=Rose);Spacer(Modifier.height(16.dp))
                    Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(Color.White.copy(.9f))){Column(Modifier.padding(16.dp)){Text("权限",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Ink);PermissionRow("悬浮窗",Settings.canDrawOverlays(this@MainActivity),"允许 Clawd 显示在其他应用上层"){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))};PermissionRow("通知",Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED,"用于常驻服务状态通知"){requestRuntimePermissions()};PermissionRow("通知读取",isNotificationAccessGranted(),"如果需要读取通知状态，请手动授权"){startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))}}}
                    Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(Color.White.copy(.9f))){Column(Modifier.padding(18.dp)){Text("虚拟形象",fontSize=19.sp,fontWeight=FontWeight.Bold,color=Ink);Text("支持一张默认形象，也可以为不同状态分别上传图片。",fontSize=12.sp,color=Ink.copy(.62f));Spacer(Modifier.height(8.dp));ImageButton("默认 / 待机",AppState.petImageUri){pick("default")};ImageButton("开心",AppState.petHappyUri){pick("happy")};ImageButton("难过",AppState.petSadUri){pick("sad")};ImageButton("睡觉",AppState.petSleepUri){pick("sleep")};ImageButton("说话",AppState.petTalkUri){pick("talk")};ImageButton("惊讶",AppState.petSurpriseUri){pick("surprise")}}
                    }
                    Spacer(Modifier.height(12.dp));Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(Color.White.copy(.9f))){Column(Modifier.padding(18.dp)){Text("Clawd MCP",fontSize=19.sp,fontWeight=FontWeight.Bold,color=Ink);Text("不配置第二套 AI。Operit 里的 AI 直接通过 MCP 控制这个身体。",fontSize=12.sp,color=Ink.copy(.62f));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("启动本机 MCP");Switch(checked=serverEnabled,onCheckedChange={serverEnabled=it;AppState.mcpServerEnabled=it;if(it)startPet()else stopService(Intent(this@MainActivity,ClawdOverlayService::class.java))})};Text("真实网络地址",fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=Ink);Text("以下地址全部由 Clawd 在手机运行时检测，不写死 IP。",fontSize=11.sp,color=Ink.copy(.55f));CopyField("本机回环地址",ClawdMcpServer.loopbackEndpoint());if(bindLan){ClawdMcpServer.lanAddresses().forEachIndexed{index,ip->CopyField("局域网地址 ${index+1}","http://$ip:${AppState.mcpServerPort}/mcp")}};if(ClawdMcpServer.sseEndpoint().isNotBlank())CopyField("SSE 地址",ClawdMcpServer.sseEndpoint());Text(if(diagnostic.running)"MCP 状态：正在监听 ${diagnostic.port}" else "MCP 状态：未启动",color=Rose,fontSize=12.sp);Text("自测：${diagnostic.detail}",fontSize=12.sp,color=if(diagnostic.loopbackReachable)Ink else Rose);Spacer(Modifier.height(8.dp));OutlinedButton(onClick={Thread{val d=ClawdMcpServer.selfTest();runOnUiThread{diagnostic=d}}.start()},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("重新测试 MCP")}Field("端口",port,editable=true){v->port=v.filter{it.isDigit()};v.toIntOrNull()?.takeIf{it in 1024..65535}?.let{newPort->if(newPort!=AppState.mcpServerPort){AppState.mcpServerPort=newPort;if(ClawdMcpServer.isRunning()){ClawdMcpServer.stop();ClawdMcpServer.start(this@MainActivity)}}}};Field("Bearer Token（可选）",token,true,editable=true){token=it;AppState.mcpServerToken=it};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(if(bindLan)"允许局域网访问" else "仅本机访问");Switch(checked=bindLan,onCheckedChange={bindLan=it;AppState.mcpBindHost=if(it)"0.0.0.0" else "127.0.0.1";if(ClawdMcpServer.isRunning()){ClawdMcpServer.stop();ClawdMcpServer.start(this@MainActivity)}})};Text(if(ClawdMcpServer.isRunning())"MCP 状态：运行中" else "MCP 状态：未启动",color=Rose,fontSize=12.sp);Spacer(Modifier.height(8.dp));Button(onClick={startPet()},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=Rose)){Text("开启桌面 Clawd")}}
                    }
                    Spacer(Modifier.height(12.dp));Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(Color.White.copy(.9f))){Column(Modifier.padding(18.dp)){Text("语音",fontSize=19.sp,fontWeight=FontWeight.Bold,color=Ink);Text("Operit 决定说什么，Clawd 负责播放声音。",fontSize=12.sp,color=Ink.copy(.62f));ProviderPicker(ttsProvider){ttsProvider=it;AppState.ttsProvider=it};Field("Voice API URL",ttsUrl,editable=true){ttsUrl=it;AppState.ttsUrl=it};Field("Voice API Key",ttsKey,true,editable=true){ttsKey=it;AppState.ttsKey=it};Field("Voice Model",ttsModel,editable=true){ttsModel=it;AppState.ttsModel=it};Field("voice_id",voiceId,editable=true){voiceId=it;AppState.voiceId=it};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("启用语音");Switch(checked=ttsEnabled,onCheckedChange={ttsEnabled=it;AppState.ttsEnabled=it})}}
                    }
                    Spacer(Modifier.height(10.dp));Text("连接：Operit AI → Clawd MCP → 虚拟形象。
如果 Operit 与 Clawd 在同一手机上，请优先测试本机回环地址；如果 Operit 使用独立网络环境，再测试局域网地址。",fontSize=11.sp,color=Ink.copy(.55f))
                }
            }
        }
    }
    private fun isNotificationAccessGranted():Boolean{
        val enabled=Settings.Secure.getString(contentResolver,"enabled_notification_listeners") ?: return false
        return enabled.contains(packageName)
    }
    @Composable private fun PermissionRow(title:String,granted:Boolean,desc:String,onClick:()->Unit){
        Row(Modifier.fillMaxWidth().padding(vertical=5.dp),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(title,color=Ink,fontWeight=FontWeight.Medium);Text(desc,fontSize=11.sp,color=Ink.copy(.55f))};OutlinedButton(onClick=onClick,shape=RoundedCornerShape(14.dp)){Text(if(granted)"已授权" else "去授权")}}
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
