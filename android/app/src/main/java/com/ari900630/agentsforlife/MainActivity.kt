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
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22,18,22,18) }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1,0,1f))
        val nav = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; setPadding(8,8,8,12); background=rounded(card,24f) }
        nav.addView(navButton("צ'אט\nעם הסוכנים") { showChat() }, weightParams())
        nav.addView(navButton("שיחה\nעם הסוכנים") { showVoice() }, weightParams())
        nav.addView(navButton("הגדרות") { showSettings() }, weightParams())
        root.addView(nav)
        setContentView(root)
    }

    private fun showHome() {
        content.removeAllViews()
        content.addView(title("Agents for Life",30f))
        content.addView(subtitle("מרכז שליטה אישי עם סוכני AI",16f))
        val hero=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,20,20,20);background=gradientCard()}
        hero.addView(text("סוכנים שעובדים בשבילך",22f,ink).apply{setTypeface(null,android.graphics.Typeface.BOLD)})
        hero.addView(text("צור, הפעל ונהל סוכני AI למשימות, שיחה ושליטה במכשיר.",14f,muted).apply{setPadding(0,8,0,14)})
        hero.addView(cardButton("✦ הסוכנים שלי","פתח את מרכז הסוכנים"){showAgentsDialog()})
        content.addView(hero,layoutParams(0,0,14,0))
        status=text("",16f,ink).apply{gravity=Gravity.CENTER;setPadding(18,18,18,18);background=rounded(card,16f)}
        content.addView(status,layoutParams(0,0,12,0))
        content.addView(section("הסוכנים שלך"))
        content.addView(cardButton("סוכני AI","10 סוגים מוכנים + יצירה חופשית"){showAgentsDialog()})
        content.addView(section("שליטה במכשיר"))
        content.addView(cardButton("Wi‑Fi","פתיחת הגדרות Wi‑Fi של Android"){AgentAction.openSettings(this,"wifi")})
        content.addView(cardButton("Bluetooth","פתיחת הגדרות Bluetooth של Android"){AgentAction.openSettings(this,"bluetooth")})
        content.addView(cardButton("צליל","פתיחת הגדרות הצליל של Android"){AgentAction.openSettings(this,"sound")})
        content.addView(cardButton("תצוגה","פתיחת הגדרות התצוגה של Android"){AgentAction.openSettings(this,"display")})
        content.addView(cardButton("שירות נגישות","מאפשר לסוכן לנהל אפליקציות לאחר אישור המשתמש"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})
        content.addView(cardButton("הרשאת אנשי קשר","גישה לאנשי הקשר לפי הרשאת Android"){requestContacts()})
        content.addView(cardButton("הגדרות והרשאות","פתיחת הגדרות Android של האפליקציה"){startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:" + packageName)))})
        content.addView(section("פעולות מהירות"))
        content.addView(cardButton("Wi-Fi","פתיחת הגדרות Wi-Fi"){AgentAction.openSettings(this,"wifi")})
        content.addView(cardButton("Bluetooth","פתיחת הגדרות Bluetooth"){AgentAction.openSettings(this,"bluetooth")})
        content.addView(cardButton("צליל ותצוגה","ניהול עוצמת קול ותצוגה"){AgentAction.openSettings(this,"sound")})
        content.addView(cardButton("אנשי קשר","פתיחת אנשי הקשר"){startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("content://contacts/people")))})
        content.addView(cardButton("חיוג","פתיחת לוח החיוג"){startActivity(Intent(Intent.ACTION_DIAL))})
        content.addView(section("אפליקציות"))
        content.addView(cardButton("ניהול אפליקציות","חסימה/פתיחה של אפליקציות באמצעות שירות הנגישות"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})
        getLaunchableApps().forEach { info ->
            val pkg=info.activityInfo.packageName
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(16,12,16,12);background=rounded(card,16f)}
            row.addView(text(info.loadLabel(packageManager).toString(),15f,ink),LinearLayout.LayoutParams(0,-2,1f))
            row.addView(Switch(this).apply{text="חסום";setTextColor(ink);isChecked=isAppBlocked(pkg);setOnCheckedChangeListener{_,b->setAppBlocked(pkg,b)}})
            row.setOnClickListener { startActivity(packageManager.getLaunchIntentForPackage(pkg) ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+pkg))) }
            content.addView(row,layoutParams(0,0,8,0))
        }
        content.addView(section("מספרים חסומים"))
        val input=EditText(this).apply{hint="מספר לחסימה";setHintTextColor(Color.rgb(150,155,165));setTextColor(ink);setSingleLine();background=rounded(card,14f);setPadding(18,12,18,12)}
        content.addView(input,layoutParams(0,0,8,0))
        content.addView(cardButton("חסום מספר","הוסף לרשימת החסימה"){
            val n=input.text.toString().trim()
            if(n.isNotEmpty()){addBlockedNumber(n);input.text.clear();Toast.makeText(this,"המספר נוסף",Toast.LENGTH_SHORT).show();showHome()}
        })
        prefs.getStringSet("blocked_numbers",emptySet()).orEmpty().sorted().forEach { n ->
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            row.addView(text(n,15f,ink),LinearLayout.LayoutParams(0,-2,1f))
            row.addView(Button(this).apply{text="הסר";setOnClickListener{removeBlockedNumber(n);showHome()}})
            content.addView(row)
        }
        refreshStatus()
    }

    private fun showChat() {
        content.removeAllViews()
        content.addView(title("צ'אט עם הסוכנים",27f))
        content.addView(subtitle("בחר סוכן, כתוב משימה וקבל תשובה.",15f))
        val agents=AgentStore.load(this).ifEmpty{AgentStore.seedTemplates(this);AgentStore.load(this)}
        val spinner=Spinner(this)
        spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,agents.map{it.name})
        content.addView(spinner,layoutParams(0,0,8,0))
        val modelSpinner=Spinner(this)
        val modelOptions=mutableListOf("Groq — openai/gpt-oss-120b","Groq — openai/gpt-oss-20b","Groq — groq/compound-mini","OpenRouter — openrouter/free","Gemini — gemini-2.5-flash-lite")
        modelSpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,modelOptions)
        content.addView(modelSpinner,layoutParams(0,0,10,0))
        val configuredServer=prefs.getString("agent_server","").orEmpty()
        loadModelsFromServer(configuredServer,modelSpinner)
        val task=EditText(this).apply{hint="כתוב כאן לסוכן...";setHintTextColor(Color.rgb(150,155,165));setTextColor(ink);minLines=5;gravity=48;background=rounded(card,14f);setPadding(16,12,16,12)}
        content.addView(task,layoutParams(0,0,10,0))
        val result=text("התשובה תופיע כאן.",15f,ink).apply{setPadding(16,16,16,16);background=rounded(card,16f)}
        content.addView(result,layoutParams(0,0,12,0))
        content.addView(cardButton("שלח לסוכן","הפעל את הסוכן שבחרת"){
            val a=agents[spinner.selectedItemPosition]
            val key=prefs.getString("ai_key","") ?: ""
            val selected=modelSpinner.selectedItem?.toString().orEmpty()
            val provider=when {
                selected.startsWith("Groq") -> "Groq"
                selected.startsWith("OpenRouter") -> "OpenRouter"
                selected.startsWith("Gemini") -> "Gemini"
                else -> prefs.getString("ai_provider","Server") ?: "Server"
            }
            val model=selected.substringAfter(" — ","").trim()
            val request=task.text.toString().trim()
            if(request.isEmpty()){result.text="כתוב משימה לסוכן.";return@cardButton}
            if(provider!="Server" && key.isEmpty()){result.text="המפתח של הספק חסר בהגדרות.";return@cardButton}
            if(provider=="Server" && prefs.getString("agent_server","").orEmpty().isBlank()){result.text="הגדר כתובת שרת AI בהגדרות.";return@cardButton}
            result.text="מפעיל את " + a.name + "..."
            Thread{
                val r=AgentApiClient.run(prefs.getString("agent_server","").orEmpty(),a.name,a.instructions,request,provider,key,model)
                runOnUiThread{r.fold({answer->AgentActionBridge.offerActions(this,answer){result.text=it}},{e->result.text="שגיאה: " + e.message})}
            }.start()
        })
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
                val provider=prefs.getString("ai_provider","Gemini") ?: "Gemini"
                val key=prefs.getString("ai_key","") ?: ""
                if(a==null||(provider!="Server"&&key.isEmpty())||(provider=="Server"&&base.isBlank())){transcript.text="הגדר ספק AI ומפתח API בהגדרות. אין צורך בשרת עבור Gemini או OpenRouter.";return}
                Thread{
                    val r=AgentApiClient.run(base,a.name,a.instructions,spoken,provider,key)
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
            override fun onItemSelected(parent:AdapterView<*>?,view:android.view.View?,position:Int,id:Long){endpoint.visibility=if(position==2)ViewGroup.VISIBLE else ViewGroup.GONE}
        }
        content.addView(cardButton("קבלת מפתח Gemini","פתיחת Google AI Studio ליצירת מפתח API"){startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://aistudio.google.com/apikey")))})
        content.addView(cardButton("שמור חיבור AI","Gemini ו-OpenRouter עובדים ישירות מהטלפון; שרת נדרש רק לשרת עצמי"){val p=when(provider.selectedItemPosition){1->"OpenRouter";2->"Server";else->"Gemini"};prefs.edit().putString("ai_provider",p).putString("ai_key",apiKey.text.toString().trim()).putString("agent_server",endpoint.text.toString().trim()).apply();Toast.makeText(this,"חיבור ה-AI נשמר",Toast.LENGTH_SHORT).show()})
        content.addView(cardButton("שירות נגישות","פתיחת הגדרות Android"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})
        content.addView(cardButton("הרשאות האפליקציה","פתיחת הרשאות Android"){startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:" + packageName)))})
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
