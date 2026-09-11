package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.util.EdgeToEdgeHelper

class CrashDisplayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 統一啟用合規邊到邊體驗
        EdgeToEdgeHelper.applyEdgeToEdge(this, isDarkTheme = false)

        val crashLog = intent.getStringExtra("CRASH_LOG") ?: "No crash log found."

        val basePadding = (16 * resources.displayMetrics.density).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(basePadding, basePadding, basePadding, basePadding)
        }

        // 動態監聽 WindowInsets (包含狀態列、導航列與螢幕挖孔)，確保所有裝置皆合規且文字不被遮擋
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                insets.left + basePadding,
                insets.top + basePadding,
                insets.right + basePadding,
                insets.bottom + basePadding
            )
            windowInsets
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
