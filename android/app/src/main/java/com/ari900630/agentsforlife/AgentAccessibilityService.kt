package com.ari900630.agentsforlife

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import java.util.Locale

class AgentAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var preview: ImageView? = null
    private var previewContainer: LinearLayout? = null
    private var statusText: TextView? = null
    private var inputText: EditText? = null
    private var live = false
    private var listening = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val wm by lazy { getSystemService(WindowManager::class.java) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("he", "IL") }
    }

    override fun onDestroy() {
        stopLivePreview()
        speechRecognizer?.destroy()
        tts?.shutdown()
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

        val title = TextView(this).apply {
            text = "Agents for Life"
            textSize = 14f
            setTextColor(Color.rgb(35, 31, 42))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 0, 4, 0)
        }

        val close = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.rgb(60, 55, 70))
                cornerRadius = 18f
            }
            setOnClickListener { stopLivePreview() }
        }

        statusText = TextView(this).apply {
            text = "מוכן — דבר איתי"
            textSize = 12f
            setTextColor(Color.rgb(107, 106, 211))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 4, 10, 4)
        }

        inputText = EditText(this).apply {
            hint = "מה לעשות?"
            textSize = 13f
            singleLine = false
            maxLines = 2
            setPadding(10, 2, 10, 2)
            setTextColor(Color.rgb(35, 31, 42))
            setHintTextColor(Color.rgb(150, 148, 160))
            background = GradientDrawable().apply {
                setColor(Color.rgb(248, 248, 252))
                cornerRadius = 18f
            }
        }

        val mic = Button(this).apply {
            text = "🎙"
            textSize = 18f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(107, 106, 211))
                cornerRadius = 22f
            }
            setOnClickListener { toggleVoice() }
        }

        val send = Button(this).apply {
            text = "שלח"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(151, 160, 239))
                cornerRadius = 20f
            }
            setOnClickListener { sendOverlayTask() }
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, 42, 1f))
            addView(close, LinearLayout.LayoutParams(38, 38))
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6, 4, 6, 6)
            addView(inputText, LinearLayout.LayoutParams(0, 54, 1f))
            addView(mic, LinearLayout.LayoutParams(54, 48).apply { leftMargin = 6 })
            addView(send, LinearLayout.LayoutParams(54, 48).apply { leftMargin = 6 })
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 4, 4, 4)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 24f
                setStroke(2, Color.rgb(107, 106, 211))
            }
            addView(top)
            addView(image, LinearLayout.LayoutParams(-1, 300))
            addView(statusText, LinearLayout.LayoutParams(-1, 30))
            addView(controls)
        }

        val params = WindowManager.LayoutParams(
            330, 480,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 18
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
                val bmp = screenshot.hardwareBuffer?.let {
                    Bitmap.wrapHardwareBuffer(it, screenshot.colorSpace)
                }
                screenshot.hardwareBuffer?.close()
                if (bmp != null) preview?.setImageBitmap(bmp)
            }
            override fun onFailure(errorCode: Int) {
                statusText?.text = "תצוגה חיה לא זמינה כרגע"
            }
        })
    }

    private fun toggleVoice() {
        if (listening) {
            speechRecognizer?.stopListening()
            listening = false
            statusText?.text = "מעבד את הדיבור…"
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            statusText?.text = "יש לאשר גישה למיקרופון במסך האפליקציה"
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText?.text = "זיהוי דיבור אינו זמין במכשיר"
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { statusText?.text = "מקשיב לך…" }
            override fun onBeginningOfSpeech() { statusText?.text = "מקשיב…" }
            override fun onRmsChanged(rmsdB: Float) {
                preview?.scaleX = 1f + (rmsdB.coerceIn(0f, 10f) / 60f)
                preview?.scaleY = preview?.scaleX ?: 1f
            }
            override fun onEndOfSpeech() {
                listening = false
                preview?.scaleX = 1f
                preview?.scaleY = 1f
                statusText?.text = "מעבד…"
            }
            override fun onError(error: Int) {
                listening = false
                statusText?.text = "לא הצלחתי להבין. נסה שוב."
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (heard.isBlank()) {
                    statusText?.text = "לא התקבל טקסט"
                    return
                }
                inputText?.setText(heard)
                sendOverlayTask()
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        listening = true
        speechRecognizer?.startListening(intent)
        statusText?.text = "מקשיב לך…"
    }

    private fun sendOverlayTask() {
        val task = inputText?.text?.toString()?.trim().orEmpty()
        if (task.isBlank()) {
            statusText?.text = "כתוב או אמור לי מה לעשות"
            return
        }
        val agents = AgentStore.load(this).ifEmpty {
            AgentStore.seedTemplates(this)
            AgentStore.load(this)
        }
        val agent = agents.firstOrNull { it.type == "assistant" } ?: agents.firstOrNull()
        val server = getSharedPreferences("agents_settings", MODE_PRIVATE)
            .getString("agent_server", "https://agents-for-life.onrender.com").orEmpty()

        if (agent == null || server.isBlank()) {
            statusText?.text = "הסוכן עדיין לא מוגדר"
            return
        }

        statusText?.text = "הסוכן חושב…"
        Thread {
            val result = AgentApiClient.run(
                server, agent.name, agent.instructions, task,
                "Server", "", "openai/gpt-oss-20b", "Groq"
            )
            runOnServiceThread {
                result.fold(
                    { answer ->
                        val clean = answer.replace(Regex("\[\[DEVICE_ACTION:.*?\]\]"), "").trim()
                        statusText?.text = if (clean.isBlank()) "בוצע" else clean.take(180)
                        speak(clean)
                        AgentActionBridge.offerActions(this, answer) {
                            statusText?.text = if (it.isBlank()) "בוצע" else it.take(180)
                        }
                    },
                    { error ->
                        statusText?.text = "שגיאה: " + (error.message ?: "לא ידועה")
                        speak(statusText?.text.toString())
                    }
                )
            }
        }.start()
    }

    private fun runOnServiceThread(action: () -> Unit) {
        handler.post(action)
    }

    private fun speak(text: String) {
        if (text.isNotBlank()) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent-overlay")
    }

    fun installFromPlayStore(packageName: String, appLabel: String = packageName): Boolean {
        if (packageName.isBlank()) return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Uri.encode(packageName)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            handler.postDelayed({ clickPlayStoreInstall(0) }, 1800)
            true
        } catch (_: Exception) { false }
    }

    private fun clickPlayStoreInstall(attempt: Int) {
        if (attempt > 18) return
        val root = rootInActiveWindow
        if (root != null && clickNodeByLabels(root, listOf("התקנה", "התקן", "Install", "INSTALL", "עדכון", "Update", "UPDATE"))) return
        handler.postDelayed({ clickPlayStoreInstall(attempt + 1) }, 800)
    }

    private fun clickNodeByLabels(node: AccessibilityNodeInfo, labels: List<String>): Boolean {
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (labels.any { it.equals(text, true) || it.equals(desc, true) }) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            val parent = node.parent
            if (parent != null && parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
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
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 80))
            .build()
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
                handler.postDelayed(this, 700)
            }
        })
    }

    fun stopLivePreview() {
        live = false
        listening = false
        speechRecognizer?.cancel()
        handler.removeCallbacksAndMessages(null)
        preview = null
        previewContainer?.let { runCatching { wm.removeView(it) } }
        previewContainer = null
        statusText = null
        inputText = null
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
