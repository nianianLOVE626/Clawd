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
    private lateinit var petParams: WindowManager.LayoutParams
    private lateinit var visual: View
    private lateinit var status: TextView
    private var bubbleRoot: View? = null
    private var scalePanel: View? = null
    private val main = Handler(Looper.getMainLooper())
    private var petSizeDp = 140

    private fun bg(color: Int, r: Float) = GradientDrawable().apply { setColor(color); cornerRadius = r }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    override fun onCreate() {
        super.onCreate(); AppState.init(this); startForeground(1004, notification())
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        petSizeDp = AppState.petSizeDp.coerceIn(60, 300)
        showPet(); ClawdPetController.attach(this)
        if (AppState.mcpServerEnabled) ClawdMcpServer.start(this)
    }

    private fun notification(): Notification {
        val id = "clawd"; val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(id, "Clawd 常驻陪伴", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, id).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Clawd 正在陪着你").setContentText("Operit AI 的虚拟身体正在运行").setOngoing(true).build()
    }

    private fun overlayLp(x: Int = 42, y: Int = 180) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }

    private fun showPet() {
        val sizePx = dp(petSizeDp)
        petRoot = FrameLayout(this).apply { setPadding(8, 8, 8, 8); background = bg(Color.argb(235, 255, 248, 251), 44f) }
        visual = createVisual()
        petRoot.addView(visual, FrameLayout.LayoutParams(sizePx, sizePx))
        status = TextView(this).apply { setTextColor(Color.rgb(145, 110, 128)); textSize = 10f; gravity = Gravity.CENTER; setPadding(4, 0, 4, 4); text = "Clawd · 待机" }
        petRoot.addView(status, FrameLayout.LayoutParams(sizePx, dp(24)).apply { gravity = Gravity.BOTTOM })

        petParams = overlayLp()
        setupTouch()
        wm.addView(petRoot, petParams)
    }

    private fun setupTouch() {
        visual.setOnTouchListener(object : View.OnTouchListener {
            var dx = 0; var dy = 0; var sx = 0; var sy = 0; var moved = false
            var downTime = 0L; val longPressMs = 500L
            val longPressCheck = Runnable { if (!moved) showScalePanel() }

            override fun onTouch(v: View, e: MotionEvent): Boolean = when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx = e.rawX.roundToInt(); dy = e.rawY.roundToInt()
                    sx = petParams.x; sy = petParams.y; moved = false
                    downTime = SystemClock.uptimeMillis()
                    main.postDelayed(longPressCheck, longPressMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val mx = e.rawX.roundToInt() - dx; val my = e.rawY.roundToInt() - dy
                    if (abs(mx) > 10 || abs(my) > 10) { moved = true; main.removeCallbacks(longPressCheck) }
                    petParams.x = sx + mx; petParams.y = sy + my
                    wm.updateViewLayout(petRoot, petParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(longPressCheck)
                    if (!moved && SystemClock.uptimeMillis() - downTime < longPressMs) {
                        showMcpSpeech("我在这里。", 5)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> { main.removeCallbacks(longPressCheck); true }
                else -> true
            }
        })
    }

    private fun showScalePanel() {
        scalePanel?.let { runCatching { wm.removeView(it) } }
        val ctx = this
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = bg(Color.argb(245, 255, 248, 252), dp(20).toFloat())
        }
        val label = TextView(ctx).apply { text = "大小：${petSizeDp}dp"; textSize = 13f; setTextColor(Color.rgb(90, 70, 80)); gravity = Gravity.CENTER }
        panel.addView(label)

        val seekBar = SeekBar(ctx).apply {
            max = 240; progress = petSizeDp - 60
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) { petSizeDp = p + 60; label.text = "大小：${petSizeDp}dp"; resizePet() }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) { AppState.petSizeDp = petSizeDp }
            })
        }
        panel.addView(seekBar, LinearLayout.LayoutParams(dp(200), LinearLayout.LayoutParams.WRAP_CONTENT))

        val closeBtn = TextView(ctx).apply { text = "收起"; textSize = 12f; setTextColor(Color.rgb(210, 125, 157)); gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }
        closeBtn.setOnClickListener { dismissScalePanel() }
        panel.addView(closeBtn)

        val lp = overlayLp(petParams.x, petParams.y + dp(petSizeDp) + dp(20))
        wm.addView(panel, lp)
        scalePanel = panel
        // auto dismiss after 8s
        main.postDelayed({ dismissScalePanel() }, 8000)
    }

    private fun dismissScalePanel() {
        scalePanel?.let { runCatching { wm.removeView(it) } }; scalePanel = null
    }

    private fun resizePet() {
        if (!::petRoot.isInitialized) return
        val sizePx = dp(petSizeDp)
        visual.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
        status.layoutParams = FrameLayout.LayoutParams(sizePx, dp(24)).apply { gravity = Gravity.BOTTOM }
        petRoot.requestLayout()
    }

    private fun createVisual(): View {
        val uri = uriForCurrentState()
        if (uri.isNotBlank()) return ImageView(this).apply {
            val bmp = runCatching { contentResolver.openInputStream(Uri.parse(uri)).use { BitmapFactory.decodeStream(it) } }.getOrNull()
            if (bmp != null) setImageBitmap(bmp) else setImageResource(android.R.drawable.ic_menu_gallery)
            scaleType = ImageView.ScaleType.CENTER_INSIDE; setPadding(8, 8, 8, 8)
        }
        return CutePetView(this)
    }

    private fun uriForCurrentState(): String {
        val m = ClawdPetController.lastMood.lowercase(); val a = ClawdPetController.lastAction.lowercase()
        return when {
            m.contains("sleep") || m.contains("困") || a.contains("sleep") -> AppState.petSleepUri
            m.contains("sad") || m.contains("难过") -> AppState.petSadUri
            m.contains("surprise") || m.contains("惊") -> AppState.petSurpriseUri
            a.contains("talk") || a.contains("说") || m.contains("talk") -> AppState.petTalkUri
            m.contains("happy") || m.contains("开心") || m.contains("高兴") -> AppState.petHappyUri
            else -> AppState.petImageUri
        }
    }

    private fun refreshVisual() {
        if (!::petRoot.isInitialized) return
        val sizePx = dp(petSizeDp)
        val index = petRoot.indexOfChild(visual); val old = visual
        visual = createVisual(); petRoot.removeView(old); petRoot.addView(visual, index, FrameLayout.LayoutParams(sizePx, sizePx))
        setupTouch()
    }

    fun setPetMood(mood: String) { status.text = "心情：$mood"; refreshVisual() }
    fun setPetAction(action: String) { status.text = "动作：$action"; refreshVisual() }

    fun showMcpSpeech(msg: String, seconds: Int = 10) {
        if (!::petRoot.isInitialized) return
        bubbleRoot?.let { runCatching { wm.removeView(it) } }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)); background = bg(Color.argb(248, 255, 249, 252), dp(16).toFloat()) }
        root.addView(TextView(this).apply { text = "Clawd"; textSize = 11f; setTextColor(Color.rgb(210, 125, 157)) })
        root.addView(TextView(this).apply { text = msg; textSize = 14f; setTextColor(Color.rgb(82, 63, 73)); setPadding(0, dp(4), 0, 0) })
        root.setOnClickListener { runCatching { wm.removeView(root) }; bubbleRoot = null }
        val bx = petParams.x + dp(petSizeDp) + dp(10)
        val by = petParams.y
        wm.addView(root, overlayLp(bx, by))
        bubbleRoot = root
        main.postDelayed({ if (bubbleRoot === root) { runCatching { wm.removeView(root) }; bubbleRoot = null } }, seconds.coerceIn(2, 60) * 1000L)
    }

    override fun onDestroy() {
        dismissScalePanel()
        if (::petRoot.isInitialized) runCatching { wm.removeView(petRoot) }
        bubbleRoot?.let { runCatching { wm.removeView(it) } }
        ClawdPetController.detach(this); ClawdMcpServer.stop(); super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}