package com.ari900630.agentsforlife

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.graphics.drawable.GradientDrawable
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private var currentChatId: String? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var listening = false
    private var sending = false

    private val serverUrl = "https://agents-for-life.onrender.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("agents_settings", MODE_PRIVATE)
        runCatching {
            buildChatOnlyScreen()
        }.onFailure {
            android.util.Log.e("AgentsForLife", "Startup failed", it)
            showError(it)
        }
    }

    private fun buildChatOnlyScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 10, 18))
            setPadding(12, 12, 12, 10)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 4, 8, 10)
        }
        header.addView(TextView(this).apply {
            text = "Agents for Life"
            textSize = 21f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, 48, 1f))
        header.addView(TextView(this).apply {
            text = "מוכן"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(160, 220, 178))
            background = rounded(Color.rgb(18, 46, 30), 18f, Color.rgb(60, 120, 78))
        }, LinearLayout.LayoutParams(64, 36))

        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2, 8, 2, 8)
        }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(messages)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, 8, 0, 0)
        }

        input = EditText(this).apply {
            hint = "דבר עם הבינה המלאכותית…"
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(135, 143, 168))
            gravity = Gravity.TOP or Gravity.RIGHT
            minLines = 1
            maxLines = 5
            setSingleLine(false)
            setPadding(16, 14, 16, 14)
            background = rounded(Color.rgb(20, 23, 32), 22f, Color.rgb(62, 72, 100))
        }
        composer.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        composer.addView(iconButton("🎙") {
            startVoice()
        }, LinearLayout.LayoutParams(54, 56).apply { leftMargin = 7 })

        composer.addView(iconButton("➤") {
            sendMessage()
        }, LinearLayout.LayoutParams(54, 56).apply { leftMargin = 7 })

        root.addView(composer)
        setContentView(root)

        loadCurrentChat()
    }

    private fun loadCurrentChat() {
        val savedId = prefs.getString("current_chat_id", null)
        val session = savedId?.let { ChatStore.find(this, it) } ?: ChatStore.create(this)
        currentChatId = session.id
        prefs.edit().putString("current_chat_id", session.id).apply()
        renderMessages(session)
    }

    private fun renderMessages(session: ChatSession) {
        messages.removeAllViews()
        if (session.messages.isEmpty()) {
            addBubble(
                "שלום. אני הבינה המלאכותית שלך.\n\nאפשר לדבר איתי או לכתוב לי מה לעשות. אני יכול לעזור במשימות, לפתוח אפליקציות, לפתוח הגדרות, לבצע פעולות במכשיר ועוד — לפי ההרשאות שאישרת ב-Android.",
                false
            )
        } else {
            session.messages.forEach { addBubble(it.text, it.role == "user") }
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addBubble(value: String, user: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (user) Gravity.END else Gravity.START
            setPadding(4, 4, 4, 4)
        }
        val bubble = TextView(this).apply {
            text = value
            textSize = 16f
            setTextColor(if (user) Color.WHITE else Color.rgb(235, 238, 247))
            setPadding(16, 13, 16, 13)
            background = rounded(
                if (user) Color.rgb(30, 55, 182) else Color.rgb(20, 23, 32),
                20f,
                if (user) Color.rgb(62, 91, 218) else Color.rgb(55, 64, 84)
            )
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            width = (resources.displayMetrics.widthPixels * 0.86f).toInt()
        }
        row.addView(bubble, lp)
        messages.addView(row)
    }

    private fun sendMessage() {
        if (sending) return
        val request = input.text.toString().trim()
        if (request.isBlank()) return

        val sessionId = currentChatId ?: return
        sending = true
        input.setText("")
        addBubble(request, true)
        ChatStore.addMessage(this, sessionId, "user", request)
        addBubble("חושב…", false)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

        val agents = AgentStore.load(this).ifEmpty {
            AgentStore.seedTemplates(this)
            AgentStore.load(this)
        }
        val agent = agents.firstOrNull { it.type == "assistant" }
            ?: agents.firstOrNull { it.name == "מנהל טלפון" }
            ?: agents.firstOrNull()

        if (agent == null) {
            finishReply(sessionId, "לא נמצא סוכן AI.")
            return
        }

        Thread {
            val result = AgentApiClient.run(
                serverUrl,
                agent.name,
                agent.instructions,
                request,
                "Server",
                "",
                "openai/gpt-oss-20b",
                "Groq"
            )
            runOnUiThread {
                sending = false
                removeThinkingBubble()
                result.fold(
                    { answer ->
                        AgentActionBridge.offerActions(this, answer) { finalText ->
                            val clean = finalText.trim()
                            val shown = if (clean.isBlank()) "בוצע." else clean
                            ChatStore.addMessage(this, sessionId, "assistant", shown)
                            addBubble(shown, false)
                            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                            speak(shown)
                        }
                    },
                    { error ->
                        finishReply(sessionId, "שגיאה בחיבור לבינה המלאכותית: " + (error.message ?: "שגיאה לא ידועה"))
                    }
                )
            }
        }.start()
    }

    private fun finishReply(sessionId: String, answer: String) {
        sending = false
        removeThinkingBubble()
        ChatStore.addMessage(this, sessionId, "assistant", answer)
        addBubble(answer, false)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        speak(answer)
    }

    private fun removeThinkingBubble() {
        if (messages.childCount == 0) return
        val last = messages.getChildAt(messages.childCount - 1)
        val bubble = if (last is ViewGroup && last.childCount > 0) last.getChildAt(0) else null
        if (bubble is TextView && bubble.text.toString() == "חושב…") {
            messages.removeView(last)
        }
    }

    private fun startVoice() {
        if (listening) {
            speechRecognizer?.cancel()
            listening = false
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "זיהוי דיבור אינו זמין במכשיר.", Toast.LENGTH_LONG).show()
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 701)
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                Toast.makeText(this@MainActivity, "מקשיב…", Toast.LENGTH_SHORT).show()
            }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                Toast.makeText(this@MainActivity, "לא הצלחתי להבין. נסה שוב.", Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (heard.isNotBlank()) {
                    input.setText(heard)
                    input.setSelection(input.text.length)
                    sendMessage()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        speechRecognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        })
    }

    private fun speak(value: String) {
        if (value.isBlank()) return
        val clean = value.replace(Regex("\[\[DEVICE_ACTION:.*?\]\]"), "").trim()
        if (clean.isBlank()) return
        if (tts == null) {
            tts = TextToSpeech(this) { result ->
                if (result == TextToSpeech.SUCCESS) {
                    runCatching { tts?.language = Locale("he", "IL") }
                    runCatching { tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "agent-answer") }
                }
            }
        } else {
            runCatching { tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "agent-answer") }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 701 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startVoice()
        }
    }

    private fun iconButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 19f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(Color.rgb(30, 55, 182), 20f, Color.rgb(62, 91, 218))
        setOnClickListener { action() }
        minHeight = 52
    }

    private fun rounded(color: Int, radius: Float, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * resources.displayMetrics.density
            setStroke(1, stroke)
        }

    private fun showError(error: Throwable) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(8, 10, 18))
        }
        box.addView(TextView(this).apply {
            text = "Agents for Life"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        })
        box.addView(TextView(this).apply {
            text = "אירעה תקלה בהפעלת מסך הצ׳אט:\n\n" + (error.message ?: error.javaClass.simpleName)
            textSize = 15f
            setTextColor(Color.rgb(220, 222, 232))
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 18)
        })
        setContentView(box)
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
