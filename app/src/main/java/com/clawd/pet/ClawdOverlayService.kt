package com.clawd.pet

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.concurrent.Executors

class ClawdOverlayService:Service() {
    private lateinit var wm:WindowManager
    private lateinit var petRoot:LinearLayout
    private val io=Executors.newSingleThreadExecutor()
    private lateinit var visionCompanion:VisionCompanion
    private var chatRoot:LinearLayout?=null
    private var bubbleRoot:LinearLayout?=null
    private val main=Handler(Looper.getMainLooper())

    private fun bg(color:Int, radius:Float)=GradientDrawable().apply{
        setColor(color);cornerRadius=radius
    }

    override fun onCreate(){
        super.onCreate()
        AppState.init(this)
        startForeground(1004,notification())
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)) return
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        showPet()
        visionCompanion=VisionCompanion(this){showProactive(it)}
        visionCompanion.start()
    }

    private fun notification():Notification{
        val id="clawd"
        val n=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT>=26)n.createNotificationChannel(
            NotificationChannel(id,"Clawd 常驻陪伴",NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this,id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Clawd 正在陪着你")
            .setContentText("悬浮陪伴与智能视觉正在运行")
            .setOngoing(true).build()
    }

    private fun lp(x:Int=42,y:Int=180):WindowManager.LayoutParams{
        val type=if(Build.VERSION.SDK_INT>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply{gravity=Gravity.TOP or Gravity.START;this.x=x;this.y=y}
    }

    private fun showPet(){
        petRoot=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(4,4,4,4)
            gravity=Gravity.CENTER_HORIZONTAL
            // 不加背景色，让桌宠形象直接浮在屏幕上
        }
        val petSize=160 // dp转px由系统density决定，这里用像素近似
        val avatar=ImageView(this).apply{
            setImageResource(R.drawable.clawd_pet)
            scaleType=ImageView.ScaleType.FIT_CENTER
        }
        val status=TextView(this).apply{
            textSize=9f;setTextColor(Color.rgb(145,110,128));gravity=Gravity.CENTER
            setPadding(0,2,0,0)
            setShadowLayer(3f,1f,1f,Color.WHITE)
        }
        petRoot.addView(avatar,LinearLayout.LayoutParams(petSize,petSize))
        petRoot.addView(status)
        val params=lp()

        petRoot.setOnTouchListener(object:View.OnTouchListener{
            var dx=0;var dy=0;var sx=0;var sy=0;var moved=false
            override fun onTouch(v:View,e:MotionEvent):Boolean{
                when(e.actionMasked){
                    MotionEvent.ACTION_DOWN->{dx=e.rawX.roundToInt();dy=e.rawY.roundToInt();sx=params.x;sy=params.y;moved=false}
                    MotionEvent.ACTION_MOVE->{
                        val mx=e.rawX.roundToInt()-dx;val my=e.rawY.roundToInt()-dy
                        if(abs(mx)>8||abs(my)>8)moved=true
                        params.x=sx+mx;params.y=sy+my
                        wm.updateViewLayout(petRoot,params)
                    }
                    MotionEvent.ACTION_UP->{if(!moved)showChat()}
                }
                return true
            }
        })
        wm.addView(petRoot,params)
        val h=Handler(Looper.getMainLooper())
        val r=object:Runnable{
            override fun run(){
                val m=DeviceSense.nowPlaying(this@ClawdOverlayService)
                status.text=if(m!=null)"♪ ${m.title}" else ""
                h.postDelayed(this,5000)
            }
        }
        h.post(r)
    }

    private fun showChat(){
        if(chatRoot!=null){remove(chatRoot!!);chatRoot=null;return}
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(18,16,18,14)
            background=bg(Color.argb(248,255,249,252),34f)
        }
        val title=TextView(this).apply{
            text=CacheTracker.detailLabel(this@ClawdOverlayService).let{if(it=="暂无缓存数据")"Clawd" else "Clawd · $it"}
            textSize=19f;setTextColor(Color.rgb(92,65,77))
        }
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val scroll=ScrollView(this).apply{addView(list)}
        root.addView(title,LinearLayout.LayoutParams(-1,-2))
        root.addView(scroll,LinearLayout.LayoutParams(720,-2).apply{weight=1f})
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val input=EditText(this).apply{
            hint="和 Clawd 说点什么";setTextColor(Color.rgb(80,62,72))
            background=bg(Color.argb(235,247,237,243),26f);setPadding(16,8,16,8)
        }
        val send=Button(this).apply{text="发送";background=bg(Color.rgb(224,150,176),24f);setTextColor(Color.WHITE)}
        row.addView(input,LinearLayout.LayoutParams(0,58).apply{weight=1f})
        row.addView(send,LinearLayout.LayoutParams(96,58).apply{leftMargin=8})
        val voice=Button(this).apply{text="语音";background=bg(Color.rgb(205,157,181),24f);setTextColor(Color.WHITE)}
        row.addView(voice,LinearLayout.LayoutParams(82,58).apply{leftMargin=8})
        root.addView(row)
        voice.setOnClickListener{
            val last=ClawdChatStore.load(this).lastOrNull{it.role=="assistant"}
            if(last!=null && AppState.ttsEnabled){
                val bubble=VoiceBubble.render(this,last.text){ }
                addOverlay(bubble,420,90)
                main.postDelayed({remove(bubble)},30000)
            } else if(!AppState.ttsEnabled){
                Toast.makeText(this,"请先在语音 API 中开启语音气泡",Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(row)
        val memory=Button(this).apply{text="记住刚才这句话";setTextColor(Color.rgb(145,90,115))}
        val clear=Button(this).apply{text="清空本地对话";setTextColor(Color.rgb(145,90,115))}
        root.addView(memory)
        root.addView(clear)

        ClawdChatStore.load(this).takeLast(12).forEach{appendBubble(list,it.role,it.text,it.cacheHitRate)}
        send.setOnClickListener{
            val text=input.text.toString().trim()
            if(text.isBlank())return@setOnClickListener
            input.setText("")
            appendBubble(list,"user",text,0.0)
            send.isEnabled=false
            ClawdConversation.send(this@ClawdOverlayService,text){ reply ->
                main.post{
                    appendBubble(list,"assistant",reply,0.0)
                    scroll.post{scroll.fullScroll(View.FOCUS_DOWN)}
                    send.isEnabled=true
                }
            }
        }
        memory.setOnClickListener{
            val last=ClawdChatStore.load(this).lastOrNull()
            if(last!=null){
                ClawdAgent.remember(last.text){r ->
                    main.post{
                        Toast.makeText(this,
                            r.fold({"已交给 Ombre Brain"}, {"记忆写入失败：${it.message}"}),
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        clear.setOnClickListener{ClawdChatStore.clear(this);list.removeAllViews()}
        addOverlay(root,250,110)
        chatRoot=root
        scroll.post{scroll.fullScroll(View.FOCUS_DOWN)}
    }

    private fun appendBubble(list:LinearLayout,role:String,text:String,cacheHitRate:Double=0.0){
        val tv=TextView(this).apply{
            this.text=text;textSize=14f;setTextColor(Color.rgb(82,63,73))
            setPadding(16,12,16,12)
            background=bg(if(role=="user")Color.rgb(248,235,242) else Color.rgb(255,255,255),28f)
        }
        val block=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        block.addView(tv)
        if(role=="assistant" && cacheHitRate>0){
            val cache=TextView(this).apply{text=CacheTracker.label(cacheHitRate);textSize=10f;setTextColor(Color.rgb(171,116,139));setPadding(10,3,10,0)}
            block.addView(cache)
        }
        list.addView(block,LinearLayout.LayoutParams(-1,-2).apply{
            topMargin=7;leftMargin=if(role=="user")70 else 0;rightMargin=if(role=="user")0 else 70
        })
    }

    private fun showProactive(text:String){
        if(!AppState.proactiveEnabled)return
        bubbleRoot?.let{remove(it)}
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(15,12,15,12)
            background=bg(Color.argb(250,255,249,252),30f)
        }
        val label=TextView(this).apply{text="Clawd";textSize=11f;setTextColor(Color.rgb(210,125,157))}
        val body=TextView(this).apply{text=text;textSize=14f;setTextColor(Color.rgb(82,63,73))}
        root.addView(label);root.addView(body)
        body.setOnClickListener{showChat()}
        addOverlay(root,320,155)
        bubbleRoot=root
        main.postDelayed({if(bubbleRoot===root){remove(root);bubbleRoot=null}},12000)
    }

    private fun addOverlay(view:View,x:Int,y:Int){
        wm.addView(view,lp(x,y))
    }

    private fun remove(view:View){
        runCatching{wm.removeView(view)}
    }

    override fun onDestroy(){
        if(::petRoot.isInitialized)remove(petRoot)
        chatRoot?.let{remove(it)}
        bubbleRoot?.let{remove(it)}
        io.shutdownNow()
        if(::visionCompanion.isInitialized)visionCompanion.stop()
        ScreenVision.stop()
        super.onDestroy()
    }
    override fun onBind(intent:Intent?)=null
}
