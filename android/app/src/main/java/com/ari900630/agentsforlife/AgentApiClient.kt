package com.ari900630.agentsforlife

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

object AgentApiClient {
    fun run(baseUrl: String, agentName: String, instructions: String, task: String): Result<String> {
        return try {
            val url = URL(baseUrl.trimEnd('/') + "/api/run")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 60000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val body = JSONObject().apply {
                put("agent", JSONObject().apply {
                    put("name", agentName)
                    put("instructions", instructions)
                    put("webSearch", false)
                })
                put("task", task)
            }.toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val text = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = JSONObject(text.ifBlank { "{}" })
            if (connection.responseCode in 200..299 && json.optBoolean("ok")) {
                Result.success(json.optString("output"))
            } else {
                Result.failure(IllegalStateException(json.optString("error", "שגיאה בהפעלת הסוכן")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
