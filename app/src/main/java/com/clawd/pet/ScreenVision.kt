package com.clawd.pet

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import kotlin.math.abs

object ScreenVision {
    const val REQUEST_CODE = 4801
    private var consent: Intent? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var density = 1
    private var width = 720
    private var height = 1280
    private var lastFingerprint: LongArray? = null

    fun requestPermission(activity: Activity) {
        val mgr = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        activity.startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_CODE)
    }

    fun acceptResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) consent = data
    }

    fun hasConsent() = consent != null
    fun clearConsent() { stop(); consent = null }
    fun start(context: Context): Boolean {
        if (projection != null || consent == null) return projection != null
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels.coerceAtMost(1440)
        height = metrics.heightPixels.coerceAtMost(2560)
        density = metrics.densityDpi
        projection = mgr.getMediaProjection(Activity.RESULT_OK, consent!!)
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        display = projection!!.createVirtualDisplay(
            "ClawdScreenVision", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, Handler(Looper.getMainLooper())
        )
        return true
    }

    private fun bitmapFromLatest(context: Context): Bitmap? {
        if (!start(context)) return null
        val image = reader?.acquireLatestImage() ?: return null
        image.use {
            val plane = it.planes[0]
            val bitmap = Bitmap.createBitmap(
                it.width + (plane.rowStride - plane.pixelStride * it.width) / plane.pixelStride,
                it.height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(plane.buffer)
            return if (bitmap.width != it.width) {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, it.width, it.height)
                bitmap.recycle()
                cropped
            } else bitmap
        }
    }

    fun captureJpeg(context: Context, quality: Int = 68): ByteArray? {
        val bitmap = bitmapFromLatest(context) ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    /**
     * Cheap perceptual-ish fingerprint. It intentionally downsamples heavily so
     * we can decide whether a video scene changed before paying for vision API calls.
     */
    fun captureIfChanged(context: Context): Pair<ByteArray, Double>? {
        val bitmap = bitmapFromLatest(context) ?: return null
        val small = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
        val fp = LongArray(6)
        for (y in 0 until 24) for (x in 0 until 24) {
            val px = small.getPixel(x, y)
            val lum = ((px shr 16 and 255) * 299 + (px shr 8 and 255) * 587 + (px and 255) * 114) / 1000
            val bucket = (x + y * 24) % fp.size
            fp[bucket] = (fp[bucket] * 31 + lum) and 0x7fffffff
        }
        var distance = 1.0
        lastFingerprint?.let { old ->
            var sum = 0.0
            for (i in fp.indices) sum += abs(fp[i] - old[i]).toDouble() / 0x7fffffff
            distance = (sum / fp.size).coerceIn(0.0, 1.0)
        }
        lastFingerprint = fp
        small.recycle()

        // Only export the full frame if the scene changed enough.
        return if (distance >= 0.035) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 68, out)
            bitmap.recycle()
            out.toByteArray() to distance
        } else {
            bitmap.recycle()
            null
        }
    }

    fun resetChangeDetector() { lastFingerprint = null }

    fun stop() {
        display?.release(); display = null
        reader?.close(); reader = null
        projection?.stop(); projection = null
        lastFingerprint = null
    }

    fun describeMode() =
        if (hasConsent()) "已授权，可按需或低频观察屏幕" else "尚未授权屏幕视觉"
}
