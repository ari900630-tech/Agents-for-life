package com.ari900630.agentsforlife

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
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

    fun installFromPlayStore(packageName: String, appLabel: String = packageName): Boolean {
        if (packageName.isBlank()) return false
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + android.net.Uri.encode(packageName))).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            handler.postDelayed({ clickPlayStoreInstall(0) }, 1800)
            true
        } catch (_: Exception) { false }
    }

    private fun clickPlayStoreInstall(attempt: Int) {
        if (attempt > 18) return
        val root = rootInActiveWindow
        if (root != null && clickNodeByLabels(root, listOf("התקנה","התקן","Install","INSTALL","עדכון","Update","UPDATE"))) return
        handler.postDelayed({ clickPlayStoreInstall(attempt + 1) }, 800)
    }

    private fun clickNodeByLabels(node: AccessibilityNodeInfo, labels: List<String>): Boolean {
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (labels.any { it.equals(text, true) || it.equals(desc, true) }) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            val parent = node.parent
            if (parent != null && parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            val bounds = Rect(); node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty && dispatchTap(bounds.centerX().toFloat(), bounds.centerY().toFloat())) return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickNodeByLabels(child, labels)) return true
        }
        return false
    }

    private fun dispatchTap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 80)).build()
        return dispatchGesture(gesture, null, null)
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
