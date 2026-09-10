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
    private var chatRoot: View? = null
    private var chatScale = 1f
    private val main=Handler(Looper.getMainLooper())

    private fun bg(color:Int,r:Float)=GradientDrawable().apply{setColor(color);cornerRadius=r}

    override fun onCreate(){
        super.onCreate(); AppState.init(this); startForeground(1004,notification())
        ClawdPetController.attach(this)
    }

    override fun onStartCommand(intent:Intent?, flags:Int, startId:Int):Int {
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
            stopSelf(); return START_NOT_STICKY
        }
        if(!::wm.isInitialized) wm=getSystemService(WINDOW_SERVICE) as WindowManager
        if(!::petRoot.isInitialized) showPet()
        ClawdPetController.attach(this)
        if(AppState.mcpServerEnabled && !ClawdMcpServer.isRunning()) ClawdMcpServer.start(this)
        return START_STICKY
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
        // Pet body, speech bubble and status are intentionally kept inside ONE
        // WindowManager overlay. Moving the body therefore moves everything
        // together instead of leaving the bubble behind at a fixed screen point.
        petRoot=FrameLayout(this).apply{
            setPadding(0,0,0,0)
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren=false
            clipToPadding=false
        }
        visual=createVisual()
        petRoot.addView(visual,FrameLayout.LayoutParams(170,170).apply{
            gravity=Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        })
        status=TextView(this).apply{
            setTextColor(Color.rgb(145,110,128))
            textSize=10f
            gravity=Gravity.CENTER
            setPadding(4,0,4,4)
            text="Clawd · 待机"
        }
        petRoot.addView(status,FrameLayout.LayoutParams(170,28).apply{
            gravity=Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin=0
        })
        // The root is deliberately larger than the image so the bubble can
        // live above the pet without requiring a second overlay window.
        val params=lp()
        params.width=220
        params.height=300
        attachPetTouch(visual, params)
        wm.addView(petRoot,params)
    }

    private fun createVisual():View{
        val uri=uriForCurrentState()
        if(uri.isNotBlank()) return ImageView(this).apply{
            isClickable=true
            isFocusable=false
            val bmp=runCatching{contentResolver.openInputStream(Uri.parse(uri)).use{BitmapFactory.decodeStream(it)}}.getOrNull()
            if(bmp!=null)setImageBitmap(bmp) else setImageResource(android.R.drawable.ic_menu_gallery)
            scaleType=ImageView.ScaleType.CENTER_INSIDE;setPadding(8,8,8,8)
            setBackgroundColor(Color.TRANSPARENT)
        }
        return CutePetView(this)
    }

    private fun uriForCurrentState():String{
        val m=ClawdPetController.lastMood.lowercase();val a=ClawdPetController.lastAction.lowercase()
        return when{
            m.contains("sleep")||m.contains("困")||a.contains("sleep")->AppState.petSleepUri
            m.contains("sad")||m.contains("难过")->AppState.petSadUri
            m.contains("surprise")||m.contains("惊")->AppState.petSurpriseUri
            a.contains("talk")||a.contains("说")||m.contains("talk")->AppState.petTalkUri
            m.contains("happy")||m.contains("开心")||m.contains("高兴")->AppState.petHappyUri
            else->AppState.petImageUri
        }
    }

    private fun attachPetTouch(target:View, params:WindowManager.LayoutParams){
        target.setOnTouchListener(object:View.OnTouchListener{
            var dx=0;var dy=0;var sx=0;var sy=0;var moved=false
            override fun onTouch(v:View,e:MotionEvent):Boolean=when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{dx=e.rawX.roundToInt();dy=e.rawY.roundToInt();sx=params.x;sy=params.y;moved=false;true}
                MotionEvent.ACTION_MOVE->{
                    val mx=e.rawX.roundToInt()-dx
                    val my=e.rawY.roundToInt()-dy
                    if(abs(mx)>8||abs(my)>8)moved=true
                    params.x=sx+mx
                    params.y=sy+my
                    runCatching{wm.updateViewLayout(petRoot,params)}
                    true
                }
                MotionEvent.ACTION_UP->{if(!moved)openChatPanel();true}
                else->true
            }
        })
    }

    private fun refreshVisual(){
        if(!::petRoot.isInitialized)return
        val index=petRoot.indexOfChild(visual)
        if(index<0)return
        val old=visual
        val next=createVisual()
        visual=next
        val lp=FrameLayout.LayoutParams(170,170).apply{
            gravity=Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        petRoot.removeView(old)
        petRoot.addView(next,index,lp)
        val windowParams=petRoot.layoutParams as? WindowManager.LayoutParams
        if(windowParams!=null)attachPetTouch(next,windowParams)
    }

    fun setPetMood(mood:String){status.text="心情：$mood";refreshVisual()}
    fun setPetAction(action:String){status.text="动作：$action";refreshVisual()}

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).roundToInt()

    /** Opens a real interactive chat panel beside the pet. Its size is independent
     *  from the pet image: plus/minus buttons and pinch gesture change only the
     *  panel scale, never the character. */
    fun openChatPanel() {
        if(!::petRoot.isInitialized) return
        chatRoot?.let { petRoot.removeView(it) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f),dp(12f),dp(14f),dp(12f))
            background = bg(Color.argb(248,255,249,252),30f)
            elevation = dp(8f).toFloat()
            isClickable = true
            isFocusable = true
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = "Clawd"
            textSize = 16f
            setTextColor(Color.rgb(170,105,135))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0,dp(30f),1f))
        val minus = Button(this).apply { text="−"; setTextSize(16f); setOnClickListener{setChatScale(chatScale-0.1f)} }
        val plus = Button(this).apply { text="＋"; setTextSize(16f); setOnClickListener{setChatScale(chatScale+0.1f)} }
        titleRow.addView(minus,LinearLayout.LayoutParams(dp(42f),dp(38f)))
        titleRow.addView(plus,LinearLayout.LayoutParams(dp(42f),dp(38f)))
        panel.addView(titleRow)

        val history = TextView(this).apply {
            text = if(ClawdPetController.lastMessage.isBlank()) "在这里和 Clawd 聊天。\n你也可以让 Operit 通过 MCP 让 Clawd 回复。" else ClawdPetController.lastMessage
            textSize = 14f
            setTextColor(Color.rgb(82,63,73))
            setPadding(0,dp(6f),0,dp(8f))
            setBackgroundColor(Color.TRANSPARENT)
        }
        panel.addView(history,LinearLayout.LayoutParams(-1,dp(100f)))

        val inputRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val input = EditText(this).apply {
            hint="和 Clawd 说点什么…"
            textSize=14f
            singleLine=false
            maxLines=3
            setPadding(dp(12f),dp(8f),dp(12f),dp(8f))
            background=bg(Color.argb(220,255,255,255),24f)
        }
        inputRow.addView(input,LinearLayout.LayoutParams(0,dp(52f),1f))
        inputRow.addView(Button(this).apply {
            text="发送"
            setOnClickListener {
                val text=input.text.toString().trim()
                if(text.isNotBlank()) {
                    ClawdPetController.lastMessage=text
                    ClawdPetController.lastUpdatedAt=System.currentTimeMillis()
                    history.text="你：$text\n\n已发送给 Clawd。\n（AI 回复仍由 Operit MCP 负责。）"
                    input.text.clear()
                }
            }
        },LinearLayout.LayoutParams(dp(68f),dp(52f)))
        panel.addView(inputRow)

        val gesture = android.view.ScaleGestureDetector(this,object:android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
            override fun onScale(detector: android.view.ScaleGestureDetector):Boolean {
                setChatScale(chatScale * detector.scaleFactor)
                return true
            }
        })
        panel.setOnTouchListener { _, event ->
            // Always feed the complete gesture stream so ScaleGestureDetector
            // receives the initial ACTION_DOWN before the second finger arrives.
            gesture.onTouchEvent(event)
            event.pointerCount >= 2
        }
        val lp = FrameLayout.LayoutParams(dp(330f),dp(250f)).apply {
            gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin=dp(10f)
        }
        petRoot.addView(panel,lp)
        chatRoot=panel
        panel.bringToFront()
        setChatScale(chatScale)
    }

    private fun setChatScale(value:Float) {
        chatScale=value.coerceIn(0.65f,1.7f)
        chatRoot?.animate()?.scaleX(chatScale)?.scaleY(chatScale)?.setDuration(80)?.start()
    }

    fun showMcpSpeech(text:String,seconds:Int=10){
        if(!::petRoot.isInitialized)return
        // Bubble is a child of petRoot, so it follows the same WindowManager
        // position as the character during dragging.
        bubbleRoot?.let{petRoot.removeView(it)}
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(16,12,16,12)
            background=bg(Color.argb(248,255,249,252),30f)
            elevation=8f
        }
        root.addView(TextView(this).apply{
            this.text="Clawd"
            textSize=11f
            setTextColor(Color.rgb(210,125,157))
        })
        root.addView(TextView(this).apply{
            this.text=text
            textSize=14f
            setTextColor(Color.rgb(82,63,73))
            setPadding(0,5,0,0)
        })
        root.setOnClickListener{petRoot.removeView(root);if(bubbleRoot===root)bubbleRoot=null}
        val bubbleLp=FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply{
            gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin=8
            leftMargin=8
            rightMargin=8
        }
        petRoot.addView(root,bubbleLp)
        bubbleRoot=root
        main.postDelayed({
            if(bubbleRoot===root){
                petRoot.removeView(root)
                bubbleRoot=null
            }
        },seconds.coerceIn(2,60)*1000L)
    }

    override fun onDestroy(){
        if(::petRoot.isInitialized)remove(petRoot);bubbleRoot=null;chatRoot=null;ClawdPetController.detach(this);ClawdMcpServer.stop();super.onDestroy()
    }
    override fun onBind(intent:Intent?)=null
}
