package com.ari900630.agentsforlife

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

class AgentAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var preview: ImageView? = null
    private var previewContainer: LinearLayout? = null
    private var live = false
    private val wm by lazy { getSystemService(WindowManager::class.java) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        stopLivePreview()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    private fun addLivePreview() {
        if (previewContainer != null) return
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.WHITE)
        }
        val close = TextView(this).apply {
            text = "✕"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(60, 55, 70))
            setOnClickListener { stopLivePreview() }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2,2,2,2)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 22f
                setStroke(2, Color.rgb(107,106,211))
            }
            addView(close, LinearLayout.LayoutParams(42,42).apply { gravity = Gravity.END })
            addView(image, LinearLayout.LayoutParams(260, 430))
        }
        val params = WindowManager.LayoutParams(
            270, 480,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = 80
        }
        runCatching { wm.addView(box, params) }.onSuccess {
            preview = image
            previewContainer = box
        }
    }

    private fun captureFrame() {
        if (!live || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val bmp = screenshot.hardwareBuffer?.let { android.graphics.Bitmap.wrapHardwareBuffer(it, screenshot.colorSpace) }
                screenshot.hardwareBuffer?.close()
                if (bmp != null) {
                    preview?.setImageBitmap(bmp)
                }
            }
            override fun onFailure(errorCode: Int) = Unit
        })
    }

    fun startLivePreview() {
        if (live) return
        live = true
        addLivePreview()
        handler.post(object : Runnable {
            override fun run() {
                if (!live) return
                captureFrame()
                handler.postDelayed(this, 450)
            }
        })
    }

    fun stopLivePreview() {
        live = false
        handler.removeCallbacksAndMessages(null)
        preview = null
        previewContainer?.let { runCatching { wm.removeView(it) } }
        previewContainer = null
    }

    companion object {
        @Volatile private var instance: AgentAccessibilityService? = null
        fun performGlobal(action: Int): Boolean = instance?.performGlobalAction(action) == true
        fun isEnabled(): Boolean = instance != null
        fun startLivePreview(): Boolean {
            val s = instance ?: return false
            s.startLivePreview()
            return true
        }
        fun stopLivePreview() { instance?.stopLivePreview() }
    }
}
