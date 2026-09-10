package com.clawd.pet

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import kotlin.math.abs
import kotlin.math.roundToInt

class ClawdOverlayService: Service() {
    private lateinit var wm: WindowManager
    private lateinit var petRoot: FrameLayout
    private lateinit var visual: View
    private lateinit var status: TextView
    private var bubbleRoot: View? = null
    private val main=Handler(Looper.getMainLooper())

    private fun bg(color:Int,r:Float)=GradientDrawable().apply{setColor(color);cornerRadius=r}

    override fun onCreate(){
        super.onCreate(); AppState.init(this); startForeground(1004,notification())
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)) return
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        showPet(); ClawdPetController.attach(this)
        if(AppState.mcpServerEnabled) ClawdMcpServer.start(this)
    }

    private fun notification():Notification{
        val id="clawd"; val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(NotificationChannel(id,"Clawd 常驻陪伴",NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this,id).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Clawd 正在陪着你").setContentText("Operit AI 的虚拟身体正在运行").setOngoing(true).build()
    }

    private fun lp(x:Int=42,y:Int=180)=WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
        if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;this.x=x;this.y=y}

    private fun showPet(){
        petRoot=FrameLayout(this).apply{setPadding(8,8,8,8);background=bg(Color.argb(235,255,248,251),44f)}
        visual=createVisual()
        petRoot.addView(visual,FrameLayout.LayoutParams(170,170))
        status=TextView(this).apply{setTextColor(Color.rgb(145,110,128));textSize=10f;gravity=Gravity.CENTER;setPadding(4,0,4,4);text="Clawd · 待机"}
        petRoot.addView(status,FrameLayout.LayoutParams(170,30).apply{gravity=Gravity.BOTTOM})
        val params=lp()
        visual.setOnTouchListener(object:View.OnTouchListener{
            var dx=0;var dy=0;var sx=0;var sy=0;var moved=false
            override fun onTouch(v:View,e:MotionEvent):Boolean=when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{dx=e.rawX.roundToInt();dy=e.rawY.roundToInt();sx=params.x;sy=params.y;moved=false;true}
                MotionEvent.ACTION_MOVE->{val mx=e.rawX.roundToInt()-dx;val my=e.rawY.roundToInt()-dy;if(abs(mx)>8||abs(my)>8)moved=true;params.x=sx+mx;params.y=sy+my;wm.updateViewLayout(petRoot,params);true}
                MotionEvent.ACTION_UP->{if(!moved) showMcpSpeech("我在这里。",5);true}
                else->true
            }
        })
        wm.addView(petRoot,params)
    }

    private fun createVisual():View{
        val uri=uriForCurrentState()
        if(uri.isNotBlank()) return ImageView(this).apply{
            val bmp=runCatching{contentResolver.openInputStream(Uri.parse(uri)).use{BitmapFactory.decodeStream(it)}}.getOrNull()
            if(bmp!=null)setImageBitmap(bmp) else setImageResource(android.R.drawable.ic_menu_gallery)
            scaleType=ImageView.ScaleType.CENTER_INSIDE;setPadding(8,8,8,8)
        }
        return CutePetView(this)
    }

    private fun uriForCurrentState():String{
        val m=ClawdPetController.lastMood.lowercase();val a=ClawdPetController.lastAction.lowercase()
        return when{
            m.contains("sleep")||m.contains("困")||a.contains("sleep")->AppState.petSleepUri
            m.contains("sad")||m.contains("难过")||m.contains("sad")->AppState.petSadUri
            m.contains("surprise")||m.contains("惊")->AppState.petSurpriseUri
            a.contains("talk")||a.contains("说")||m.contains("talk")->AppState.petTalkUri
            m.contains("happy")||m.contains("开心")||m.contains("高兴")->AppState.petHappyUri
            else->AppState.petImageUri
        }
    }

    private fun refreshVisual(){
        if(!::petRoot.isInitialized)return
        val index=petRoot.indexOfChild(visual); val old=visual
        visual=createVisual(); petRoot.removeView(old); petRoot.addView(visual,index,FrameLayout.LayoutParams(170,170))
    }

    fun setPetMood(mood:String){status.text="心情：$mood";refreshVisual()}
    fun setPetAction(action:String){status.text="动作：$action";refreshVisual()}

    fun showMcpSpeech(msg:String,seconds:Int=10){
        if(!::petRoot.isInitialized)return
        bubbleRoot?.let{remove(it)}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,12,16,12);background=bg(Color.argb(248,255,249,252),30f)}
        root.addView(TextView(this).apply{text="Clawd";textSize=11f;setTextColor(Color.rgb(210,125,157))})
        root.addView(TextView(this).apply{text=msg;textSize=14f;setTextColor(Color.rgb(82,63,73));setPadding(0,5,0,0)})
        root.setOnClickListener{remove(root)}
        addOverlay(root,250,120);bubbleRoot=root
        main.postDelayed({if(bubbleRoot===root){remove(root);bubbleRoot=null}},seconds.coerceIn(2,60)*1000L)
    }

    private fun addOverlay(v:View,x:Int,y:Int){wm.addView(v,lp(x,y))}
    private fun remove(v:View){runCatching{wm.removeView(v)}}

    override fun onDestroy(){
        if(::petRoot.isInitialized)remove(petRoot);bubbleRoot?.let{remove(it)};ClawdPetController.detach(this);ClawdMcpServer.stop();super.onDestroy()
    }
    override fun onBind(intent:Intent?)=null
}
