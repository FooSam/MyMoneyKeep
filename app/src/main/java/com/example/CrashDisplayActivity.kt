package com.example

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class CrashDisplayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val crashLog = intent.getStringExtra("CRASH_LOG") ?: "No crash log found."
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        val title = TextView(this).apply {
            text = "⚠️ App Crashed"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        
        val copyButton = Button(this).apply {
            text = "Copy Crash Log"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Crash Log", crashLog)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@CrashDisplayActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        
        val scrollView = ScrollView(this).apply {
            setPadding(0, 32, 0, 0)
        }
        
        val logText = TextView(this).apply {
            text = crashLog
            textSize = 12f
        }
        
        scrollView.addView(logText)
        
        layout.addView(title)
        layout.addView(copyButton)
        layout.addView(scrollView)
        
        setContentView(layout)
    }
}
