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
    private var previewParams: WindowManager.LayoutParams? = null
    private var live = false
    private var listening = false
    private var lastPublishedPackage: String? = null
    private val diagnostics = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var lastActionName: String = "none"
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val wm by lazy { getSystemService(WindowManager::class.java) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        loadDiagnostics()
        rootInActiveWindow?.packageName?.toString()?.takeIf { it.isNotBlank() }?.let { publishCurrentApp(it) }
    }

    private fun loadDiagnostics() {
        val saved = getSharedPreferences("agents_runtime", android.content.Context.MODE_PRIVATE)
            .getString("diagnostics", "").orEmpty()
        if (saved.isNotBlank()) {
            diagnostics.clear()
            diagnostics.addAll(saved.lines().takeLast(100))
        }
    }

    private fun ensureTts() {
        if (tts != null) return
        runCatching {
            tts = TextToSpeech(this) { result ->
                if (result == TextToSpeech.SUCCESS) {
                    runCatching { tts?.language = Locale("he", "IL") }
                }
            }
        }.onFailure {
            android.util.Log.e("AgentsForLife", "Service TTS init failed", it)
        }
    }

    override fun onDestroy() {
        stopLivePreview()
        speechRecognizer?.destroy()
        tts?.shutdown()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        val e = event ?: return
        if (e.eventType != android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            e.eventType != android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val pkg = e.packageName?.toString()?.trim().orEmpty()
        if (pkg.isNotBlank() && pkg != lastPublishedPackage) publishCurrentApp(pkg)
    }

    override fun onInterrupt() {
        recordDiagnostic("SERVICE_INTERRUPTED", "Accessibility service interrupted")
    }

    fun recordDiagnostic(action: String, message: String) {
        val entry = System.currentTimeMillis().toString() + "|" + action + "|" + message
        diagnostics.add(entry)
        while (diagnostics.size > 100) diagnostics.removeAt(0)
        getSharedPreferences("agents_runtime", android.content.Context.MODE_PRIVATE)
            .edit().putString("diagnostics", diagnostics.joinToString("\n")).apply()
    }

    fun getDiagnostics(): List<String> = diagnostics.toList()

    fun diagnosticsSnapshot(): List<String> = diagnostics.takeLast(20)

    fun moveOverlay(deltaY: Int): Boolean {
        val params = previewParams ?: return false
        val container = previewContainer ?: return false
        params.y = (params.y + deltaY).coerceIn(0, resources.displayMetrics.heightPixels - dp(120))
        return runCatching { wm.updateViewLayout(container, params); true }.getOrDefault(false)
    }

    fun moveOverlayToY(y: Int): Boolean {
        val params = previewParams ?: return false
        val container = previewContainer ?: return false
        params.y = y.coerceIn(0, resources.displayMetrics.heightPixels - dp(120))
        return runCatching { wm.updateViewLayout(container, params); true }.getOrDefault(false)
    }

    private fun rootNode(): AccessibilityNodeInfo? = rootInActiveWindow

    private fun matchingNodes(node: AccessibilityNodeInfo?, target: String): List<AccessibilityNodeInfo> {
        if (node == null) return emptyList()
        val alternatives = target.split("|").map { it.trim() }.filter { it.isNotBlank() }
        val out = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo) {
            val text = n.text?.toString()?.trim().orEmpty()
            val desc = n.contentDescription?.toString()?.trim().orEmpty()
            if (alternatives.any { it.equals(text, true) || it.equals(desc, true) || text.contains(it, true) || desc.contains(it, true) }) out += n
            for (i in 0 until n.childCount) n.getChild(i)?.let(::walk)
        }
        walk(node)
        return out
    }

    fun typeText(text: String): Boolean {
        val root = rootNode() ?: return false
        fun walk(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (n.isEditable && n.isFocused) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { val found = walk(it); if (found != null) return found }
            return null
        }
        val node = walk(root) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        })
    }

    fun sendText(text: String): Boolean {
        val typed = if (text.isNotBlank()) typeText(text) else true
        if (!typed) return false
        return clickTextOrDescription("Send|שלח|שליחה|➤|✓")
    }

    fun clickTextOrDescription(target: String): Boolean {
        for (node in matchingNodes(rootNode(), target)) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            node.parent?.let { if (it.isClickable && it.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true }
            val r = Rect()
            node.getBoundsInScreen(r)
            if (!r.isEmpty && dispatchTap(r.centerX().toFloat(), r.centerY().toFloat())) return true
        }
        return false
    }

    fun longClickText(target: String): Boolean {
        for (node in matchingNodes(rootNode(), target)) {
            if (node.isLongClickable && node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) return true
            val r = Rect()
            node.getBoundsInScreen(r)
            if (!r.isEmpty && dispatchLongPress(r.centerX().toFloat(), r.centerY().toFloat())) return true
        }
        return false
    }

    fun scrollDirection(direction: String): Boolean {
        val root = rootNode() ?: return false
        val action = if (direction.equals("up", true)) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        fun walk(n: AccessibilityNodeInfo): Boolean {
            if (n.isScrollable && n.performAction(action)) return true
            for (i in 0 until n.childCount) n.getChild(i)?.let { if (walk(it)) return true }
            return false
        }
        return walk(root)
    }

    fun swipeDirection(direction: String): Boolean {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val h = resources.displayMetrics.heightPixels.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val dx = when (direction.lowercase()) { "left" -> -w * .35f; "right" -> w * .35f; else -> 0f }
        val dy = when (direction.lowercase()) { "up" -> -h * .28f; "down" -> h * .28f; else -> 0f }
        return dispatchSwipe(cx - dx, cy - dy, cx + dx, cy + dy)
    }

    private fun dispatchLongPress(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 650))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 420))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun clickCurrentLike(): Boolean = clickTextOrDescription("Like|אהבתי|לייק|👍")
    fun clickCurrentFollow(): Boolean = clickTextOrDescription("Follow|עקוב|עוקב|Follow back")
    fun clickApprove(): Boolean = clickTextOrDescription("Approve|אשר|אישור|Allow|אפשר|Confirm|כן")

    fun openNotificationsAndClick(target: String, longClick: Boolean = false): Boolean {
        if (!performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)) return false
        handler.postDelayed({ if (longClick) longClickText(target) else clickTextOrDescription(target) }, 450)
        return true
    }

    fun openQuickSettingsAndClick(target: String, longClick: Boolean = false): Boolean {
        if (!performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)) return false
        handler.postDelayed({ if (longClick) longClickText(target) else clickTextOrDescription(target) }, 450)
        return true
    }

    fun performActionWithFallback(type: String, target: String = "", direction: String = ""): Boolean {
        val started = System.currentTimeMillis()
        var ok = false
        var reason = "target unavailable"
        var attempts = 0
        for (attempt in 0..2) {
            attempts = attempt + 1
            ok = when (type) {
                "TYPE_TEXT" -> typeText(target)
                "SEND_TEXT" -> sendText(target)
                "CLICK_TEXT", "CLICK_CONTENT_DESCRIPTION", "CLICK_ROLE" -> clickTextOrDescription(target)
                "LONG_CLICK_TEXT" -> longClickText(target)
                "SCROLL" -> scrollDirection(direction.ifBlank { "down" })
                "SWIPE" -> swipeDirection(direction.ifBlank { "up" })
                "LIKE" -> clickCurrentLike()
                "FOLLOW" -> clickCurrentFollow()
                "APPROVE" -> clickApprove()
                else -> false
            }
            if (ok) { reason = "completed on attempt " + attempts; break }
            if (attempt < 2) {
                Thread.sleep(120L * (attempt + 1))
                rootInActiveWindow?.refresh()
            }
        }
        recordDiagnostic(type, (if (ok) "SUCCESS" else "FAILURE") + "|attempts=" + attempts + "|durationMs=" + (System.currentTimeMillis() - started) + "|reason=" + reason)
        return ok
    }

    private fun publishCurrentApp(packageName: String) {
        lastPublishedPackage = packageName
        getSharedPreferences("agents_runtime", android.content.Context.MODE_PRIVATE).edit()
            .putString("current_package", packageName)
            .apply()
        sendBroadcast(Intent(ACTION_CURRENT_APP).apply {
            putExtra(EXTRA_PACKAGE, packageName)
            setPackage(packageNameForBroadcastTarget())
        })
    }

    private fun packageNameForBroadcastTarget(): String = applicationContext.packageName

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
            setSingleLine(false)
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
            addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(statusText, LinearLayout.LayoutParams(-1, 30))
            addView(controls)
        }

        val params = WindowManager.LayoutParams(
            dp(230), WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 18
        }

        title.setOnTouchListener(object : android.view.View.OnTouchListener {
            var downY = 0f
            var startY = 0
            override fun onTouch(v: android.view.View, event: android.view.MotionEvent): Boolean {
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> { downY = event.rawY; startY = params.y; return true }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        params.y = (startY + (event.rawY - downY).toInt()).coerceIn(0, resources.displayMetrics.heightPixels - dp(120))
                        runCatching { wm.updateViewLayout(box, params) }
                        return true
                    }
                }
                return true
            }
        })

        runCatching { wm.addView(box, params) }.onSuccess {
            preview = image
            previewContainer = box
            previewParams = params
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
            listening = false
            speechRecognizer?.cancel()
            statusText?.text = "מפסיק להקשיב…"
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            statusText?.text = "יש לאשר מיקרופון פעם אחת בהגדרות"
            val intent = Intent(this, VoiceCaptureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return
        }
        listening = true
        statusText?.text = "מקשיב לך…"
        val intent = Intent(this, VoiceCaptureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    fun receiveVoiceResult(text: String) {
        listening = false
        if (text.isBlank()) {
            statusText?.text = "לא התקבל טקסט"
            return
        }
        inputText?.setText(text)
        sendOverlayTask()
    }

    fun receiveVoiceError(message: String) {
        listening = false
        statusText?.text = message
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
                        val clean = answer.replace(Regex("\\[\\[DEVICE_ACTION:.*?\\]\\]"), "").trim()
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun runOnServiceThread(action: () -> Unit) {
        handler.post(action)
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        ensureTts()
        runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent-overlay") }
            .onFailure { android.util.Log.e("AgentsForLife", "Service TTS speak failed", it) }
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
        previewParams = null
        statusText = null
        inputText = null
    }

    companion object {
        const val ACTION_CURRENT_APP = "com.ari900630.agentsforlife.CURRENT_APP_CHANGED"
        const val EXTRA_PACKAGE = "package_name"
        @Volatile private var instance: AgentAccessibilityService? = null
        fun performGlobal(action: Int): Boolean = instance?.performGlobalAction(action) == true
        fun isEnabled(): Boolean = instance != null
        fun recordActionDiagnostic(action: String, success: Boolean, reason: String, durationMs: Long) { instance?.recordDiagnostic(action, (if (success) "SUCCESS" else "FAILURE") + "|durationMs=" + durationMs + "|reason=" + reason) }
        fun moveOverlay(deltaY: Int): Boolean = instance?.moveOverlay(deltaY) == true
        fun moveOverlayToY(y: Int): Boolean = instance?.moveOverlayToY(y) == true
        fun diagnosticsSnapshot(): List<String> = instance?.diagnosticsSnapshot() ?: emptyList()
        fun currentPackageName(): String? = instance?.rootInActiveWindow?.packageName?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: instance?.getSharedPreferences("agents_runtime", MODE_PRIVATE)
                ?.getString("current_package", null)
        fun startLivePreview(): Boolean {
            val s = instance ?: return false
            s.startLivePreview()
            return true
        }
        fun stopLivePreview() { instance?.stopLivePreview() }
        fun instanceReceiveVoiceResult(text: String) { instance?.receiveVoiceResult(text) }
        fun instanceReceiveVoiceError(message: String) { instance?.receiveVoiceError(message) }
        fun instanceReceiveVoiceStatus(message: String) { instance?.statusText?.post { instance?.statusText?.text = message } }
        fun installFromPlayStore(packageName: String, appLabel: String = packageName): Boolean =
            instance?.installFromPlayStore(packageName, appLabel) == true
    }
}
