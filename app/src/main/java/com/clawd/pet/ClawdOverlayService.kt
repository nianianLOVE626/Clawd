package com.clawd.pet

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.concurrent.Executors

class ClawdOverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var petRoot: FrameLayout
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var visionCompanion: VisionCompanion
    private var chatRoot: LinearLayout? = null
    private var chatParams: WindowManager.LayoutParams? = null
    private var bubbleRoot: LinearLayout? = null
    private val main = Handler(Looper.getMainLooper())

    private fun bg(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
    }

    override fun onCreate() {
        super.onCreate()
        AppState.init(this)
        startForeground(1004, notification())
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showPet()
        visionCompanion = VisionCompanion(this) { showProactive(it) }
        visionCompanion.start()
    }

    private fun notification(): Notification {
        val id = "clawd"
        val n = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) n.createNotificationChannel(
            NotificationChannel(id, "Clawd 常驻陪伴", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Clawd 正在陪着你")
            .setContentText("悬浮陪伴与智能视觉正在运行")
            .setOngoing(true).build()
    }

    private fun petLp(x: Int = 42, y: Int = 180): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }
    }

    /** 聊天面板用可聚焦的参数，这样 EditText 能弹键盘 */
    private fun chatLp(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val dm = resources.displayMetrics
        val w = (dm.widthPixels * 0.88).toInt()
        val h = (dm.heightPixels * 0.55).toInt()
        return WindowManager.LayoutParams(w, h, type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    // ========== 桌宠形象 ==========

    private fun showPet() {
        val density = resources.displayMetrics.density
        val petSizePx = (AppState.petSize.coerceIn(60, 200) * density).toInt()

        petRoot = FrameLayout(this)
        val avatar = ImageView(this).apply {
            val customPath = AppState.petImagePath
            if (customPath.isNotBlank()) {
                val f = java.io.File(customPath)
                if (f.exists()) {
                    setImageURI(android.net.Uri.fromFile(f))
                } else {
                    setImageResource(R.drawable.clawd_pet)
                }
            } else {
                setImageResource(R.drawable.clawd_pet)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        petRoot.addView(avatar, FrameLayout.LayoutParams(petSizePx, petSizePx))

        val params = petLp()

        petRoot.setOnTouchListener(object : View.OnTouchListener {
            var dx = 0; var dy = 0; var sx = 0; var sy = 0; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dx = e.rawX.roundToInt(); dy = e.rawY.roundToInt()
                        sx = params.x; sy = params.y; moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val mx = e.rawX.roundToInt() - dx; val my = e.rawY.roundToInt() - dy
                        if (abs(mx) > 18 || abs(my) > 18) moved = true
                        if (moved) { params.x = sx + mx; params.y = sy + my; wm.updateViewLayout(petRoot, params) }
                    }
                    MotionEvent.ACTION_UP -> { if (!moved) toggleChat() }
                }
                return true
            }
        })
        wm.addView(petRoot, params)
    }

    // ========== 聊天面板 ==========

    private fun toggleChat() {
        if (chatRoot != null) { dismissChat(); return }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 16)
            background = bg(Color.argb(252, 255, 249, 252), 38f)
            elevation = 12f
        }

        // 顶栏：标题 + 关闭
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            this.text = "Clawd"
            textSize = 20f; setTextColor(Color.rgb(92, 65, 77))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val closeBtn = TextView(this).apply {
            this.text = "✕"; textSize = 18f; setTextColor(Color.rgb(180, 140, 160))
            setPadding(16, 0, 0, 0)
            setOnClickListener { dismissChat() }
        }
        topBar.addView(title, LinearLayout.LayoutParams(0, -2).apply { weight = 1f })
        topBar.addView(closeBtn)
        root.addView(topBar)

        // 消息列表
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list); isFillViewport = true }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f; topMargin = 12 })

        // 输入栏
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 0)
        }
        val input = EditText(this).apply {
            hint = "说点什么…"; setTextColor(Color.rgb(80, 62, 72)); setHintTextColor(Color.rgb(180, 160, 170))
            background = bg(Color.argb(235, 247, 237, 243), 26f); setPadding(20, 14, 20, 14)
            isFocusable = true; isFocusableInTouchMode = true; maxLines = 3
            textSize = 15f
        }
        val send = Button(this).apply {
            this.text = "发送"; background = bg(Color.rgb(224, 150, 176), 24f); setTextColor(Color.WHITE)
            textSize = 14f; setPadding(16, 0, 16, 0)
        }
        row.addView(input, LinearLayout.LayoutParams(0, -2).apply { weight = 1f })
        row.addView(send, LinearLayout.LayoutParams(-2, (44 * resources.displayMetrics.density).toInt()).apply { leftMargin = 10 })
        root.addView(row)

        // 加载历史
        ClawdChatStore.load(this).takeLast(12).forEach { appendBubble(list, it.role, it.text) }

        // 发送
        send.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isBlank()) return@setOnClickListener
            input.setText("")
            appendBubble(list, "user", text)
            send.isEnabled = false
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            ClawdConversation.send(this@ClawdOverlayService, text) { reply ->
                main.post {
                    appendBubble(list, "assistant", reply)
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                    send.isEnabled = true
                    // 桌宠头上弹小气泡
                    showReplyBubble(reply)
                }
            }
        }

        val cp = chatLp()
        wm.addView(root, cp)
        chatRoot = root
        chatParams = cp
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

        // 点击面板外部关闭
        root.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) { dismissChat(); true }
            else false
        }
    }

    private fun dismissChat() {
        chatRoot?.let { runCatching { wm.removeView(it) } }
        chatRoot = null; chatParams = null
    }

    private fun appendBubble(list: LinearLayout, role: String, text: String) {
        val density = resources.displayMetrics.density
        val tv = TextView(this).apply {
            this.text = text; textSize = 14f; setTextColor(Color.rgb(82, 63, 73))
            setPadding(18, 14, 18, 14)
            background = bg(
                if (role == "user") Color.rgb(248, 235, 242) else Color.rgb(255, 255, 255), 22f
            )
        }
        list.addView(tv, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = (6 * density).toInt()
            leftMargin = if (role == "user") (50 * density).toInt() else 0
            rightMargin = if (role == "user") 0 else (50 * density).toInt()
        })
    }

    // ========== 回复小气泡 ==========

    private var replyBubble: LinearLayout? = null

    private fun showReplyBubble(text: String) {
        replyBubble?.let { runCatching { wm.removeView(it) } }
        val density = resources.displayMetrics.density
        val maxW = (220 * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            background = bg(Color.argb(248, 255, 249, 252), 20f * density)
            elevation = 6f
        }
        val body = TextView(this).apply {
            this.text = if (text.length > 80) text.take(80) + "…" else text
            textSize = 13f; setTextColor(Color.rgb(82, 63, 73))
            maxLines = 4
        }
        root.addView(body)
        root.setOnClickListener { toggleChat() }

        val type = if (android.os.Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            maxW, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = (50 * density).toInt(); y = (60 * density).toInt() }

        wm.addView(root, lp)
        replyBubble = root
        main.postDelayed({ replyBubble?.let { if (it === root) { runCatching { wm.removeView(it) }; replyBubble = null } } }, 8000)
    }

    // ========== 主动陪伴气泡 ==========

    private fun showProactive(text: String) {
        if (!AppState.proactiveEnabled) return
        bubbleRoot?.let { runCatching { wm.removeView(it) } }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(18, 14, 18, 14)
            background = bg(Color.argb(250, 255, 249, 252), 28f)
            elevation = 8f
        }
        val label = TextView(this).apply { this.text = "Clawd"; textSize = 11f; setTextColor(Color.rgb(210, 125, 157)) }
        val body = TextView(this).apply { this.text = text; textSize = 14f; setTextColor(Color.rgb(82, 63, 73)) }
        root.addView(label); root.addView(body)
        body.setOnClickListener { toggleChat() }
        wm.addView(root, petLp(320, 155))
        bubbleRoot = root
        main.postDelayed({ if (bubbleRoot === root) { runCatching { wm.removeView(root) }; bubbleRoot = null } }, 12000)
    }

    // ========== 生命周期 ==========

    override fun onDestroy() {
        if (::petRoot.isInitialized) runCatching { wm.removeView(petRoot) }
        dismissChat()
        replyBubble?.let { runCatching { wm.removeView(it) } }
        bubbleRoot?.let { runCatching { wm.removeView(it) } }
        io.shutdownNow()
        if (::visionCompanion.isInitialized) visionCompanion.stop()
        ScreenVision.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}