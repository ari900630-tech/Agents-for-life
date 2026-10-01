package com.ari900630.agentsforlife

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.provider.Settings
import android.app.role.RoleManager
import android.view.Gravity
import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.view.ViewGroup
import android.widget.*
import android.graphics.drawable.GradientDrawable
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private var speech: SpeechRecognizer? = null
    private val prefs by lazy { getSharedPreferences("agents_settings", MODE_PRIVATE) }
    private val bg = Color.rgb(10, 12, 22)
    private val card = Color.rgb(18, 21, 35)
    private val primary = Color.rgb(116, 92, 255)
    private val ink = Color.WHITE
    private val muted = Color.rgb(170, 176, 200)

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        outContent.title = "Agents for Life"
        outContent.webUri = android.net.Uri.parse("https://github.com/ari900630-tech/Agents-for-life")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildShell()
        showHome()
    }

    override fun onDestroy() {
        speech?.destroy()
        tts.shutdown()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18,18,18,18) }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1,0,1f))
        val nav = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; setPadding(10,10,10,12); background=rounded(card,26f) }
        nav.addView(navButton("בית") { showHome() }, weightParams())
        nav.addView(navButton("סוכנים") { showAgentsDialog() }, weightParams())
        nav.addView(navButton("צ׳אט") { showChat() }, weightParams())
        root.addView(nav)
        setContentView(root)
    }

    private fun showHome() {
        content.removeAllViews()
        content.addView(title("Agents for Life",30f))
        content.addView(subtitle("מרכז סוכנים חכם",15f))
        val hero=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(22,22,22,22);background=gradientCard()}
        hero.addView(text("מה המשימה שלך?",25f,ink).apply{setTypeface(null,android.graphics.Typeface.BOLD)})
        hero.addView(text("אין צורך לבחור סוכן או מודל. כתוב מה אתה רוצה והמערכת מפעילה את הסוכן המתאים.",14f,muted).apply{setPadding(0,8,0,16)})
        hero.addView(cardButton("✦ התחל משימה","המערכת מטפלת בשאר"){showChat()})
        content.addView(hero,layoutParams(0,0,14,0))
        content.addView(section("הסוכנים שלך"))
        content.addView(cardButton("מרכז הסוכנים","סוכני מחקר, כתיבה, תכנות, תכנון, שיווק ועוד"){showAgentsDialog()})
        content.addView(section("מצב המערכת"))
        status=text("",15f,ink).apply{gravity=Gravity.CENTER;setPadding(18,18,18,18);background=rounded(card,18f)}
        content.addView(status,layoutParams(0,0,12,0))
        content.addView(cardButton("הגדרות שרת","כתובת השרת בלבד — אין מפתחות או בחירת מודל בטלפון"){showServerInfo()})
        refreshStatus()
    }

    private fun showChat() {
        content.removeAllViews()
        content.addView(title("משימה חדשה",28f))
        content.addView(subtitle("הסוכן המתאים נבחר אוטומטית.",15f))
        val badge=text("AUTO • GROQ • GPT-OSS-20B",13f,muted).apply{setPadding(14,10,14,10);background=rounded(card,14f)}
        content.addView(badge,layoutParams(0,0,10,0))
        val task=EditText(this).apply{hint="מה אתה רוצה שהסוכן יעשה?";setHintTextColor(Color.rgb(150,155,165));setTextColor(ink);minLines=7;gravity=48;background=rounded(card,18f);setPadding(18,16,18,16)}
        content.addView(task,layoutParams(0,0,10,0))
        val result=text("התשובה תופיע כאן.",15f,ink).apply{setPadding(18,18,18,18);background=rounded(card,18f)}
        content.addView(result,layoutParams(0,0,12,0))
        content.addView(cardButton("הפעל סוכן","ללא בחירת סוכן או מודל"){
            val agents=AgentStore.load(this).ifEmpty{AgentStore.seedTemplates(this);AgentStore.load(this)}
            val a=agents.firstOrNull{it.type=="assistant"} ?: agents.firstOrNull()
            val request=task.text.toString().trim()
            val server=prefs.getString("agent_server","").orEmpty()
            if(request.isEmpty()){result.text="כתוב משימה.";return@cardButton}
            if(a==null){result.text="לא נמצא סוכן.";return@cardButton}
            if(server.isBlank()){result.text="שרת ה-AI עדיין לא מוגדר באפליקציה.";return@cardButton}
            result.text="הסוכן עובד…"
            Thread{
                val r=AgentApiClient.run(server,a.name,a.instructions,request,"Server","", "openai/gpt-oss-20b","Groq")
                runOnUiThread{r.fold({answer->AgentActionBridge.offerActions(this,answer){result.text=it}},{e->result.text="שגיאה: "+e.message})}
            }.start()
        })
    }

    private fun showServerInfo(){
        content.removeAllViews()
        content.addView(title("חיבור מערכת",27f))
        content.addView(subtitle("המשתמש לא צריך להגדיר מפתח API או לבחור מודל.",15f))
        val endpoint=EditText(this).apply{hint="כתובת שרת AI";setHintTextColor(Color.rgb(150,155,165));setTextColor(ink);setSingleLine();setText(prefs.getString("agent_server",""));background=rounded(card,14f);setPadding(16,12,16,12)}
        content.addView(endpoint,layoutParams(0,0,10,0))
        content.addView(cardButton("שמור כתובת שרת","זה הדבר היחיד שנדרש מהתקנה"){prefs.edit().putString("agent_server",endpoint.text.toString().trim()).putString("ai_provider","Server").remove("ai_key").apply();Toast.makeText(this,"נשמר — אין צורך במפתח API",Toast.LENGTH_SHORT).show()})
    }

    private fun showVoice() {
        content.removeAllViews()
        content.addView(title("שיחה עם הסוכנים",27f))
        content.addView(subtitle("דבר אל הסוכן וקבל תשובה קולית.",15f))
        val agents=AgentStore.load(this).ifEmpty{AgentStore.seedTemplates(this);AgentStore.load(this)}
        val spinner=Spinner(this)
        spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,agents.map{it.name})
        content.addView(spinner,layoutParams(0,0,10,0))
        val transcript=text("לחץ על המיקרופון והתחל לדבר.",16f,ink).apply{setPadding(18,18,18,18);background=rounded(card,16f)}
        content.addView(transcript,layoutParams(0,0,10,0))
        content.addView(cardButton("התחל שיחה","דבר אל הסוכן"){
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),700);return@cardButton}
            startListening(spinner,transcript)
        })
    }

    private fun startListening(spinner:Spinner,transcript:TextView){
        if(!SpeechRecognizer.isRecognitionAvailable(this)){transcript.text="זיהוי דיבור אינו זמין במכשיר.";return}
        speech?.destroy()
        speech=SpeechRecognizer.createSpeechRecognizer(this)
        speech?.setRecognitionListener(object:RecognitionListener{
            override fun onReadyForSpeech(p:Bundle?){transcript.text="מקשיב..."}
            override fun onBeginningOfSpeech(){transcript.text="אני מקשיב..."}
            override fun onRmsChanged(v:Float){}
            override fun onBufferReceived(b:ByteArray?){}
            override fun onEndOfSpeech(){}
            override fun onError(e:Int){transcript.text="לא הצלחתי לזהות דיבור. נסה שוב."}
            override fun onResults(b:Bundle?){
                val spoken=b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if(spoken.isBlank()){transcript.text="לא זוהה טקסט.";return}
                transcript.text="אתה: " + spoken + "\n\nמעבד..."
                val agents=AgentStore.load(this@MainActivity)
                val a=agents.getOrNull(spinner.selectedItemPosition)
                val base=prefs.getString("agent_server","").orEmpty()
                val serverBase=prefs.getString("agent_server","").orEmpty()
                val useServer=serverBase.isNotBlank()
                val provider=if(useServer) "Server" else (prefs.getString("ai_provider","Gemini") ?: "Gemini")
                val key=prefs.getString("ai_key","") ?: ""
                if(a==null||(provider!="Server"&&key.isEmpty())||(provider=="Server"&&serverBase.isBlank())){transcript.text="פתח הגדרות וחבר את הסוכן ל-AI.";return}
                Thread{
                    val voiceModel=if(provider=="Server") "openai/gpt-oss-20b" else ""
                    val voiceBackend=if(provider=="Server") "Groq" else provider
                    val r=AgentApiClient.run(base,a.name,a.instructions,spoken,provider,key,voiceModel,voiceBackend)
                    runOnUiThread{r.fold(
                        {answer->AgentActionBridge.offerActions(this@MainActivity,answer){clean->transcript.text="אתה: " + spoken + "\n\n" + a.name + ": " + clean;speak(clean)}},
                        {err->transcript.text="שגיאה: " + err.message}
                    )}
                }.start()
            }
            override fun onPartialResults(b:Bundle?){}
            override fun onEvent(t:Int,p:Bundle?){}
        })
        val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false)
        }
        speech?.startListening(intent)
    }

    private fun speak(value:String){if(::tts.isInitialized)tts.speak(value,TextToSpeech.QUEUE_FLUSH,null,"agent-answer")}
    private fun loadModelsFromServer(base:String, spinner:Spinner){
        if(base.isBlank()) return
        Thread{
            try{
                val c=java.net.URL(base.trimEnd('/')+"/api/models").openConnection() as java.net.HttpURLConnection
                c.requestMethod="GET";c.connectTimeout=10000;c.readTimeout=15000
                val raw=(if(c.responseCode in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
                val arr=org.json.JSONObject(raw).optJSONArray("models") ?: return@Thread
                val labels=mutableListOf<String>()
                for(i in 0 until arr.length()){
                    val o=arr.optJSONObject(i) ?: continue
                    val provider=o.optString("provider")
                    val id=o.optString("id")
                    if(id.isNotBlank()) labels.add(provider+" — "+id)
                }
                if(labels.isNotEmpty()) runOnUiThread{
                    spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,labels)
                    Toast.makeText(this,"נטענו "+labels.size+" מודלים",Toast.LENGTH_SHORT).show()
                }
            }catch(_:Exception){}
        }.start()
    }


    private fun showSettings(){
        content.removeAllViews()
        content.addView(title("הגדרות",27f))
        content.addView(subtitle("חיבור AI, הרשאות ושירותי Agents for Life.",15f))
        content.addView(section("מנוע הבינה המלאכותית"))
        val provider=Spinner(this)
        provider.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("Gemini — חינמי","OpenRouter — מודלים חינמיים","שרת עצמי"))
        val savedProvider=prefs.getString("ai_provider","Gemini") ?: "Gemini"
        provider.setSelection(if(savedProvider=="OpenRouter")1 else if(savedProvider=="Server")2 else 0)
        content.addView(provider,layoutParams(0,0,8,0))
        val apiKey=EditText(this).apply{hint="מפתח API";setHintTextColor(Color.rgb(150,155,165));setTextColor(ink);setSingleLine();inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;setText(prefs.getString("ai_key",""));background=rounded(card,14f);setPadding(16,12,16,12)}
        content.addView(apiKey,layoutParams(0,0,8,0))
        val endpoint=EditText(this).apply{hint="כתובת שרת AI — נדרש רק לשרת עצמי";setHintTextColor(Color.rgb(150,155,165));setTextColor(ink);setSingleLine();setText(prefs.getString("agent_server",""));background=rounded(card,14f);setPadding(16,12,16,12)}
        endpoint.visibility=if(savedProvider=="Server")ViewGroup.VISIBLE else ViewGroup.GONE
        content.addView(endpoint,layoutParams(0,0,10,0))
        provider.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent:AdapterView<*>?){}
            override fun onItemSelected(parent:AdapterView<*>?,view:android.view.View?,position:Int,id:Long){
                val server=position==2
                endpoint.visibility=if(server)ViewGroup.VISIBLE else ViewGroup.GONE
                apiKey.visibility=if(server)ViewGroup.GONE else ViewGroup.VISIBLE
            }
        }
        apiKey.visibility=if(savedProvider=="Server")ViewGroup.GONE else ViewGroup.VISIBLE
        content.addView(cardButton("קבלת מפתח Gemini","פתיחת Google AI Studio ליצירת מפתח API"){startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://aistudio.google.com/apikey")))})
        content.addView(cardButton("שמור חיבור AI","בשרת עצמי מפתח Groq נשאר בשרת ואינו נשמר בטלפון"){val p=when(provider.selectedItemPosition){1->"OpenRouter";2->"Server";else->"Gemini"};val edit=prefs.edit().putString("ai_provider",p).putString("agent_server",endpoint.text.toString().trim());if(p=="Server")edit.remove("ai_key")else edit.putString("ai_key",apiKey.text.toString().trim());edit.apply();Toast.makeText(this,if(p=="Server")"חיבור השרת נשמר — אין צורך במפתח בטלפון" else "חיבור ה-AI נשמר",Toast.LENGTH_SHORT).show()})
        content.addView(cardButton("שירות נגישות","פתיחת הגדרות Android"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})
        content.addView(cardButton("הרשאות האפליקציה","פתיחת הרשאות Android"){startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:" + packageName)))})
        content.addView(cardButton("הגדר כעוזר ברירת מחדל","הפעלת Agents for Life כמו Gemini דרך כפתור העוזר של Android"){requestAssistantRole()})
        content.addView(cardButton("ניהול חסימת שיחות","הגדרת Agents for Life כמסנן שיחות"){requestCallScreeningRole()})
        content.addView(cardButton("הגדרות שיחות","פתיחת הגדרות ברירת מחדל"){try{startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))}catch(_:Exception){startActivity(Intent(Settings.ACTION_SETTINGS))}})
        content.addView(cardButton("רענון מצב","בדיקת הרשאות ושירותים"){refreshStatus();Toast.makeText(this,"המצב עודכן",Toast.LENGTH_SHORT).show()})
    }

    private fun showAgentsDialog(){
        AgentStore.seedTemplates(this)
        val agents=AgentStore.load(this)
        val names=agents.map{it.name + " — " + it.type}.toMutableList()
        names.add(0,"+ צור סוכן חדש")
        AlertDialog.Builder(this).setTitle("הסוכנים שלי").setItems(names.toTypedArray()){_,which->
            if(which==0)showCreateAgentDialog() else Toast.makeText(this,"הסוכן " + agents[which-1].name + " נבחר",Toast.LENGTH_SHORT).show()
        }.setNegativeButton("סגור",null).show()
    }

    private fun showCreateAgentDialog(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(30,8,30,0)}
        val name=EditText(this).apply{hint="שם הסוכן"}
        val type=EditText(this).apply{hint="סוג הסוכן"}
        val ins=EditText(this).apply{hint="מה הסוכן צריך לעשות?";minLines=4}
        box.addView(name);box.addView(type);box.addView(ins)
        AlertDialog.Builder(this).setTitle("יצירת סוכן חדש").setView(box).setNegativeButton("ביטול",null).setPositiveButton("צור"){_,_->
            AgentStore.create(this,name.text.toString(),type.text.toString(),ins.text.toString())
            Toast.makeText(this,"הסוכן נוצר",Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun requestAssistantRole(){
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                Toast.makeText(this,"תפקיד העוזר אינו זמין במכשיר",Toast.LENGTH_SHORT).show()
                return
            }
            if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                Toast.makeText(this,"Agents for Life כבר מוגדר כעוזר",Toast.LENGTH_SHORT).show()
                return
            }
            startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), 902)
        } else {
            Toast.makeText(this,"הגדרת עוזר ברירת מחדל זמינה מ-Android 10 ומעלה",Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestCallScreeningRole(){
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                    startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 901)
                } else Toast.makeText(this,"סינון שיחות פעיל",Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this,"סינון שיחות אינו זמין במכשיר",Toast.LENGTH_SHORT).show()
        } else startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }

    private fun refreshStatus(){
        val active=isAccessibilityServiceEnabled()
        val ba=prefs.getStringSet("blocked_apps",emptySet()).orEmpty().size
        val bn=prefs.getStringSet("blocked_numbers",emptySet()).orEmpty().size
        if(::status.isInitialized)status.text=if(active)
            "● שירות הנגישות פעיל\nאפליקציות חסומות: " + ba + "  |  מספרים חסומים: " + bn
        else
            "○ שירות הנגישות אינו פעיל\nהפעל אותו כדי לאפשר שליטה באפליקציות"
    }

    private fun getLaunchableApps()=packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),PackageManager.MATCH_ALL).filter{it.activityInfo.packageName!=packageName}.distinctBy{it.activityInfo.packageName}.sortedBy{it.loadLabel(packageManager).toString()}
    private fun isAppBlocked(p:String)=prefs.getStringSet("blocked_apps",emptySet()).orEmpty().contains(p)
    private fun setAppBlocked(p:String,b:Boolean){val s=prefs.getStringSet("blocked_apps",emptySet()).orEmpty().toMutableSet();if(b)s.add(p)else s.remove(p);prefs.edit().putStringSet("blocked_apps",s).apply();refreshStatus()}
    private fun addBlockedNumber(n:String){val s=prefs.getStringSet("blocked_numbers",emptySet()).orEmpty().toMutableSet();s.add(n);prefs.edit().putStringSet("blocked_numbers",s).apply()}
    private fun removeBlockedNumber(n:String){val s=prefs.getStringSet("blocked_numbers",emptySet()).orEmpty().toMutableSet();s.remove(n);prefs.edit().putStringSet("blocked_numbers",s).apply()}
    private fun requestContacts(){if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS),42)else Toast.makeText(this,"הרשאת אנשי קשר כבר פעילה",Toast.LENGTH_SHORT).show()}
    private fun isAccessibilityServiceEnabled():Boolean{val m=getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager;val e=ComponentName(this,AgentAccessibilityService::class.java);return m.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any{val s=it.resolveInfo.serviceInfo;ComponentName(s.packageName,s.name)==e}}
    override fun onInit(status:Int){if(status==TextToSpeech.SUCCESS)tts.language=Locale.getDefault()}

    private fun title(v:String,s:Float)=text(v,s,ink).apply{setTypeface(null,android.graphics.Typeface.BOLD);setPadding(0,8,0,4)}
    private fun subtitle(v:String,s:Float)=text(v,s,muted).apply{setPadding(0,0,0,18)}
    private fun section(v:String)=text(v,20f,ink).apply{setTypeface(null,android.graphics.Typeface.BOLD);setPadding(0,18,0,10)}
    private fun text(v:String,s:Float,c:Int)=TextView(this).apply{text=v;textSize=s;setTextColor(c)}
    private fun cardButton(t:String,d:String,action:()->Unit)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,14,18,14);background=rounded(card,16f);setOnClickListener{action()};addView(text(t,17f,ink).apply{setTypeface(null,android.graphics.Typeface.BOLD)});addView(text(d,13f,muted).apply{setPadding(0,5,0,0)})}.also{it.isClickable=true}
    private fun navButton(label:String,action:()->Unit)=Button(this).apply{text=label;setTextColor(primary);textSize=13f;isAllCaps=false;setOnClickListener{action()};setBackgroundColor(Color.TRANSPARENT)}
    private fun gradientCard():GradientDrawable=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(40,35,88),Color.rgb(24,73,110))).apply{cornerRadius=24f;setStroke(1,Color.rgb(75,82,125))}
    private fun rounded(c:Int,r:Float):GradientDrawable=GradientDrawable().apply{setColor(c);cornerRadius=r;setStroke(1,Color.rgb(45,50,72))}
    private fun layoutParams(t:Int,l:Int,b:Int,r:Int)=LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT).apply{setMargins(l,t,r,b)}
    private fun weightParams()=LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)
}
