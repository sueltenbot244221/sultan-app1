package com.sultan.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private var pendingEnable = false
    private var askedNotif = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "😎 السلطان"
            textSize = 28f
            gravity = Gravity.CENTER
        })

        status = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad / 2)
        }
        root.addView(status)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "تشغيل السلطان"
            setOnClickListener { enable() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "إيقاف"
            setOnClickListener {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
                OverlayService.running = false
                updateStatus()
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        log = TextView(this).apply {
            textSize = 16f
            setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
            textDirection = View.TEXT_DIRECTION_LOCALE
        }
        scroll = ScrollView(this).apply { addView(log) }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val sendRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply { hint = "اكتب للسلطان..." }
        sendRow.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        sendRow.addView(Button(this).apply {
            text = "إرسال"
            setOnClickListener { sendText() }
        })
        root.addView(sendRow)

        setContentView(root)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (pendingEnable && Settings.canDrawOverlays(this)) {
            pendingEnable = false
            enable()
        }
        updateStatus()
    }

    private fun updateStatus() {
        status.text = if (OverlayService.running) "السلطان يعمل ✅" else "السلطان متوقف"
    }

    // يطلب الأذونات واحداً تلو الآخر ثم يشغّل الخدمة
    private fun enable() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !askedNotif &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedNotif = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            pendingEnable = true
            Toast.makeText(this, "فعّل «العرض فوق التطبيقات» للسلطان ثم ارجع", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        OverlayService.running = true
        updateStatus()
        Toast.makeText(this, "اضغط على الإيموجي ليسمعك، واسحبه لتحريكه", Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 1 && results.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "الميكروفون ضروري ليسمعك السلطان", Toast.LENGTH_LONG).show()
            return
        }
        enable()
    }

    private fun sendText() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        append("أنت: $t")
        append("السلطان يفكر...")
        ApiClient.chat(t) { _, reply ->
            runOnUiThread {
                // استبدال سطر "يفكر" بالرد
                log.text = log.text.toString().removeSuffix("السلطان يفكر...\n")
                append("السلطان: $reply")
            }
        }
    }

    private fun append(line: String) {
        log.append(line + "\n")
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
}
