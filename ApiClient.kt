package com.sultan.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {
    const val BASE_URL = "https://sultan-server2-1.onrender.com"

    // المسار الذي يستقبل الرسائل في السيرفر. غيّره إذا كان سيرفرك يستخدم مساراً آخر.
    const val CHAT_PATH = "/chat"

    // اسم الحقل الذي يرسل فيه التطبيق رسالتك. غيّره إذا كان السيرفر يتوقع اسماً آخر.
    const val REQUEST_FIELD = "message"

    private val REPLY_KEYS = listOf("reply", "response", "message", "answer", "text", "content", "result")

    /** يرسل الرسالة في خيط خلفي، ثم يستدعي cb(نجح؟، النص) من الخيط الخلفي. */
    fun chat(message: String, cb: (Boolean, String) -> Unit) {
        Thread {
            try {
                val conn = URL(BASE_URL + CHAT_PATH).openConnection() as HttpURLConnection
                // مهلة طويلة لأن Render المجاني ينام ويحتاج حتى دقيقة ليصحو
                conn.connectTimeout = 90_000
                conn.readTimeout = 90_000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val body = JSONObject().put(REQUEST_FIELD, message).toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                conn.disconnect()

                if (code !in 200..299) {
                    cb(false, "خطأ من السيرفر ($code) " + text.take(200))
                } else {
                    cb(true, extractReply(text))
                }
            } catch (e: Exception) {
                cb(false, "تعذر الاتصال بالسيرفر: " + (e.message ?: e.javaClass.simpleName))
            }
        }.start()
    }

    private fun extractReply(raw: String): String {
        val t = raw.trim()
        if (t.startsWith("{")) {
            try {
                val o = JSONObject(t)
                for (k in REPLY_KEYS) {
                    if (o.has(k) && !o.isNull(k)) return o.get(k).toString()
                }
            } catch (_: Exception) {
            }
        }
        return t
    }
}
