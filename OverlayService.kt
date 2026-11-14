package com.sultan.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs

class OverlayService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val CHANNEL = "sultan"
        @Volatile
        var running = false
    }

    private lateinit var wm: WindowManager
    private var view: SultanView? = null
    private lateinit var params: WindowManager.LayoutParams
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val main = Handler(Looper.getMainLooper())
    private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val v = SultanView(this)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 300
        }
        v.setOnTouchListener(DragTapListener())
        try {
            wm.addView(v, params)
            view = v
        } catch (e: Exception) {
            Toast.makeText(this, "فعّل إذن العرض فوق التطبيقات أولاً", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        tts = TextToSpeech(this, this)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "السلطان", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("السلطان يعمل")
            .setContentText("اضغط على الإيموجي ليسمعك")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    // ---------- اللمس: سحب = تحريك، نقرة = تكلّم ----------
    private inner class DragTapListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    if (moved) {
                        params.x = startX + dx
                        params.y = startY + dy
                        try {
                            wm.updateViewLayout(v, params)
                        } catch (_: Exception) {
                        }
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) onTap()
            }
            return true
        }
    }

    private fun onTap() {
        val v = view ?: return
        if (busy) {
            // نقرة أثناء الاستماع/الرد = إلغاء
            recognizer?.cancel()
            tts?.stop()
            setIdle()
            return
        }
        startListening()
    }

    private fun setIdle() {
        busy = false
        view?.state = SultanView.State.IDLE
    }

    // ---------- الاستماع ----------
    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("افتح التطبيق وأعطِ إذن الميكروفون")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("خدمة التعرف على الصوت غير متوفرة في جوالك (تحتاج تطبيق Google)")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        busy = true
        view?.state = SultanView.State.LISTENING
        recognizer?.startListening(i)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            setIdle()
            // 7 = لم يفهم، 6 = لم يتكلم: لا داعي لرسالة
            if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                toast("خطأ في الاستماع (رمز $error)")
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (text.isNullOrBlank()) {
                setIdle()
            } else {
                ask(text)
            }
        }
    }

    // ---------- السؤال للسيرفر ----------
    private fun ask(text: String) {
        view?.state = SultanView.State.THINKING
        ApiClient.chat(text) { ok, reply ->
            main.post {
                if (ok) speak(reply) else {
                    toast(reply)
                    setIdle()
                }
            }
        }
    }

    // ---------- النطق ----------
    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            toast("تعذر تشغيل النطق")
            return
        }
        val r = tts?.setLanguage(Locale("ar"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            toast("صوت اللغة العربية غير مثبت في جوالك (إعدادات النطق)")
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                main.post { setIdle() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { setIdle() }
            }
        })
        ttsReady = true
    }

    private fun speak(text: String) {
        if (!ttsReady || text.isBlank()) {
            toast(text)
            setIdle()
            return
        }
        view?.state = SultanView.State.SPEAKING
        tts?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "sultan")
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        running = false
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        view?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }
}
