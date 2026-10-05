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


    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && setNodeText(focused, text)) return true
        return setTextInEditable(root, text)
    }

    private fun setTextInEditable(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.isEditable && setNodeText(node, text)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (setTextInEditable(child, text)) return true
        }
        return false
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        return runCatching {
            node.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
            )
        }.getOrDefault(false)
    }

    fun clickText(labels: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        return clickNodeByLabels(root, labels)
    }

    fun longClickText(labels: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        return longClickNodeByLabels(root, labels)
    }

    private fun longClickNodeByLabels(node: AccessibilityNodeInfo, labels: List<String>): Boolean {
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (labels.any { it.equals(text, true) || it.equals(desc, true) }) {
            if (node.isLongClickable && node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) return true
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty && dispatchLongPress(bounds.centerX().toFloat(), bounds.centerY().toFloat())) return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (longClickNodeByLabels(child, labels)) return true
        }
        return false
    }

    private fun dispatchLongPress(x: Float, y: Float): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 700))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun openChatMenu(): Boolean =
        clickText(listOf("שלוש נקודות", "אפשרויות נוספות", "More options", "More", "עוד", "⋮", "︙"))

    fun pinItem(): Boolean =
        clickText(listOf("נעץ", "הצמד", "הצמדה", "Pin", "Pinned"))

    fun sendCurrentText(): Boolean =
        clickText(listOf("שלח", "Send", "שליחה", "Send message", "שלח הודעה"))

    fun likeCurrentItem(): Boolean =
        clickText(listOf("לייק", "אהבתי", "אהב", "Like", "Liked", "👍", "Thumbs up"))

    fun followCurrentItem(): Boolean =
        clickText(listOf("עקוב", "עוקב", "לעקוב", "Follow", "Following", "Follow back", "עקוב בחזרה"))

    fun openNotifications(): Boolean =
        performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)

    fun clickByContentDescription(labels: List<String>): Boolean =
        findNode { node -> labels.any { label -> node.contentDescription?.toString()?.contains(label, true) == true } }
            ?.let { it.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) } == true

    fun clickNearbyByRole(role: String): Boolean {
        val labels = when (role.lowercase()) {
            "comment", "תגובה" -> listOf("תגובה", "תגובות", "Comment", "Comments")
            "share", "שיתוף" -> listOf("שיתוף", "שתף", "Share")
            "save", "שמירה" -> listOf("שמור", "שמירה", "Save", "Saved")
            "message", "הודעה" -> listOf("הודעה", "שלח הודעה", "Message", "Messages")
            "search", "חיפוש" -> listOf("חיפוש", "Search")
            "back", "חזור" -> listOf("חזור", "Back", "Close", "סגור")
            else -> listOf(role)
        }
        return clickText(labels) || clickByContentDescription(labels)
    }

    fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = if (direction.lowercase() in listOf("up", "למעלה", "הבא", "next"))
            android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return root.performAction(action)
    }

    fun swipe(direction: String): Boolean {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val y1 = if (direction.lowercase() in listOf("up", "למעלה")) dm.heightPixels * .75f else dm.heightPixels * .25f
        val y2 = if (direction.lowercase() in listOf("up", "למעלה")) dm.heightPixels * .25f else dm.heightPixels * .75f
        val path = android.graphics.Path().apply { moveTo(x, y1); lineTo(x, y2) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 350))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun approveCurrentAction(): Boolean =
        clickText(listOf(
            "אשר", "אישור", "אישור פעולה", "אישור בקשה", "אשר גישה",
            "אפשר", "אפשר גישה", "התר", "המשך", "אישור והמשך",
            "Confirm", "Approve", "Allow", "Accept", "Continue", "OK", "Yes",
            "אישור", "✓"
        ))

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

    fun openNotificationsAndClick(target: String, longClick: Boolean = false): Boolean {
        if (!performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)) return false
        handler.postDelayed({
            clickTextOrDescription(target, longClick)
        }, 700)
        return true
    }

    fun openQuickSettingsAndClick(target: String, longClick: Boolean = false): Boolean {
        if (!performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)) return false
        handler.postDelayed({
            clickTextOrDescription(target, longClick)
        }, 700)
        return true
    }

    private fun clickTextOrDescription(target: String, longClick: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val labels = target.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        val node = findNodeByLabels(root, labels) ?: return false
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        if (longClick) {
            if (node.isLongClickable) return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            return dispatchLongPress(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return dispatchTap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    private fun dispatchTap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /** Best-effort uninstall flow using the launcher: Home -> long-press icon -> drag to Uninstall -> confirm. */
    fun uninstallApp(packageName: String? = null, appLabel: String? = null): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return false
        val targetPackage = packageName?.takeIf { it.isNotBlank() } ?: rootInActiveWindow?.packageName?.toString().orEmpty()
        if (targetPackage.isBlank() || targetPackage == this.packageName) return false
        val label = appLabel?.takeIf { it.isNotBlank() } ?: runCatching {
            applicationContext.packageManager.getApplicationLabel(applicationContext.packageManager.getApplicationInfo(targetPackage, 0)).toString()
        }.getOrNull().orEmpty()
        stopLivePreview()
        if (!performGlobalAction(GLOBAL_ACTION_HOME)) return false
        handler.postDelayed({ findAndDragToUninstall(targetPackage, label, 0) }, 900)
        return true
    }

    private fun findAndDragToUninstall(targetPackage: String, label: String, attempt: Int) {
        if (attempt > 15) return
        val root = rootInActiveWindow
        val icon = root?.let { findAppIcon(it, targetPackage, label) }
        if (icon != null) {
            val b = Rect().also { icon.getBoundsInScreen(it) }
            val startX = b.centerX().toFloat()
            val startY = b.centerY().toFloat()
            if (dispatchLongPress(startX, startY)) {
                handler.postDelayed({ findUninstallTargetAndDrag(startX, startY, 0) }, 850)
                return
            }
        }
        handler.postDelayed({ findAndDragToUninstall(targetPackage, label, attempt + 1) }, 450)
    }

    private fun findUninstallTargetAndDrag(startX: Float, startY: Float, attempt: Int) {
        if (attempt > 12) return
        val root = rootInActiveWindow
        val target = root?.let { findNodeByLabels(it, listOf("הסר התקנה", "הסרת התקנה", "הסר", "Uninstall", "Remove", "Remove app")) }
        if (target != null) {
            val b = Rect().also { target.getBoundsInScreen(it) }
            val x = b.centerX().toFloat()
            val y = b.centerY().toFloat()
            if (dispatchDrag(startX, startY, x, y)) {
                handler.postDelayed({ clickUninstallConfirmation(0) }, 900)
                return
            }
        }
        handler.postDelayed({ findUninstallTargetAndDrag(startX, startY, attempt + 1) }, 350)
    }

    private fun clickUninstallConfirmation(attempt: Int) {
        if (attempt > 10) return
        val root = rootInActiveWindow
        if (root != null && clickNodeByLabels(root, listOf("הסר התקנה", "הסרת התקנה", "הסר", "Uninstall", "OK", "אישור"))) return
        handler.postDelayed({ clickUninstallConfirmation(attempt + 1) }, 450)
    }

    private fun findAppIcon(node: AccessibilityNodeInfo, packageName: String, label: String): AccessibilityNodeInfo? {
        val nodePackage = node.packageName?.toString().orEmpty()
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val matchesLabel = label.isNotBlank() && (text.equals(label, true) || desc.equals(label, true))
        val matchesPackage = nodePackage == packageName
        if ((matchesLabel || matchesPackage) && (node.isClickable || node.isLongClickable || node.childCount == 0)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findAppIcon(child, packageName, label)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByLabels(node: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (labels.any { it.equals(text, true) || it.equals(desc, true) }) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByLabels(child, labels)
            if (found != null) return found
        }
        return null
    }

    private fun dispatchLongPress(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 1100))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun dispatchDrag(startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
        val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 900))
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
        fun instanceReceiveVoiceResult(text: String) { instance?.receiveVoiceResult(text) }
        fun instanceReceiveVoiceError(message: String) { instance?.receiveVoiceError(message) }
        fun instanceReceiveVoiceStatus(message: String) { instance?.statusText?.post { instance?.statusText?.text = message } }
        fun installFromPlayStore(packageName: String, appLabel: String = packageName): Boolean =
            instance?.installFromPlayStore(packageName, appLabel) == true
        fun instanceUninstallApp(packageName: String?, appLabel: String?): Boolean =
            instance?.uninstallApp(packageName, appLabel) == true
        fun instanceOpenNotificationsAndClick(target: String, longClick: Boolean): Boolean =
            instance?.openNotificationsAndClick(target, longClick) == true
        fun instanceOpenQuickSettingsAndClick(target: String, longClick: Boolean): Boolean =
            instance?.openQuickSettingsAndClick(target, longClick) == true
    }
}
