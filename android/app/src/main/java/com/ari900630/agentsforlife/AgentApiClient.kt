package com.ari900630.agentsforlife

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

object AgentApiClient {
    private const val ACTION_PROTOCOL = "If the task explicitly asks you to control the Android device, you may request actions using markers like [[DEVICE_ACTION:{\\\"type\\\":\\\"HOME\\\"}]]. Allowed types: OPEN_SETTINGS (wifi, bluetooth, sound, display, accessibility), CALL (number), OPEN_URL (http/https), LAUNCH_APP (package), HOME, BACK, RECENTS, NOTIFICATIONS. Never request an action unless the user explicitly asked for it or it is necessary to complete the stated task. The Android app asks for confirmation before execution."
    fun run(baseUrl: String, agentName: String, instructions: String, task: String, provider: String = "Gemini", apiKey: String = "", model: String = ""): Result<String> {
        return try {
            when (provider) {
                "Gemini" -> if (apiKey.isNotBlank()) {
                    runGemini(apiKey, agentName, instructions, task, model)
                } else {
                    Result.failure(IllegalStateException("חסר מפתח Gemini API. אין צורך בשרת. הוסף מפתח חינמי בהגדרות."))
                }
                "OpenRouter" -> if (apiKey.isNotBlank()) {
                    runOpenRouter(apiKey, agentName, instructions, task, model)
                } else {
                    Result.failure(IllegalStateException("חסר מפתח OpenRouter. אין צורך בשרת. הוסף מפתח בהגדרות."))
                }
                "Server" -> runServer(baseUrl, agentName, instructions, task, model, provider)
                else -> Result.failure(IllegalStateException("ספק AI לא מוכר"))
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun runGemini(key:String,name:String,instructions:String,task:String,model:String):Result<String>{
        val selected=if(model.isBlank()) "gemini-2.5-flash-lite" else model.removePrefix("models/")
        val url=URL("https://generativelanguage.googleapis.com/v1beta/models/$selected:generateContent")
        val prompt="You are the agent named \"$name\". Role: $instructions\n\nUser task:\n$task\n\nAnswer directly and honestly." + ACTION_PROTOCOL
        val body=JSONObject().put("contents",JSONArray().put(JSONObject().put("parts",JSONArray().put(JSONObject().put("text",prompt))))).toString()
        return post(url,body,key){json->json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()}
    }

    private fun runOpenRouter(key:String,name:String,instructions:String,task:String,model:String):Result<String>{
        val url=URL("https://openrouter.ai/api/v1/chat/completions")
        val prompt="You are the agent named \"$name\". Role: $instructions\n\nUser task:\n$task\n\nAnswer directly and honestly."
        val body=JSONObject().put("model",if(model.isBlank()) "openrouter/free" else model).put("messages",JSONArray().put(JSONObject().put("role","user").put("content",prompt))).toString()
        return post(url,body,key,{json->json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()})
    }

    private fun runServer(base:String,name:String,instructions:String,task:String,model:String,provider:String):Result<String>{
        if(base.isBlank()) return Result.failure(IllegalStateException("בחר Gemini או OpenRouter והזן מפתח API חינמי, או הגדר כתובת שרת."))
        val body=JSONObject().apply{put("agent",JSONObject().put("name",name).put("instructions",instructions).put("webSearch",false));put("task",task);put("model",model);put("provider",provider)}.toString()
        return post(URL(base.trimEnd('/')+"/api/run"),body){json->if(json.optBoolean("ok"))json.optString("output") else throw IllegalStateException(json.optString("error","שגיאה בהפעלת הסוכן"))}
    }

    private fun post(url:URL,body:String,key:String="",extract:(JSONObject)->String):Result<String>{
        val c=url.openConnection() as HttpURLConnection
        c.requestMethod="POST";c.connectTimeout=15000;c.readTimeout=60000;c.doOutput=true;c.setRequestProperty("Content-Type","application/json")
        if(key.isNotBlank()) c.setRequestProperty("Authorization","Bearer $key")
        c.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}
        val raw=(if(c.responseCode in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
        val json=JSONObject(raw.ifBlank{"{}"})
        if(c.responseCode !in 200..299) throw IllegalStateException(json.optJSONObject("error")?.optString("message") ?: json.optString("error","HTTP ${c.responseCode}"))
        val out=extract(json)
        if(out.isBlank()) throw IllegalStateException("הספק לא החזיר תשובה")
        return Result.success(out)
    }
}