package com.example

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
        // 設定基礎遙測資訊
        com.example.util.CrashReporter.setCustomKey("app_package", packageName)
        // 初始化 AdMob 廣告並背景預載插頁廣告
        com.example.util.AdManager.init(this)
    }

    private fun setupCrashHandler() {
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            // 回報 Firebase Crashlytics
            com.example.util.CrashReporter.recordException(exception, tag = "UncaughtCrash")

            var crashReport = "Unknown Crash"
            try {
                // 將 Stack Trace 轉為字串
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                exception.printStackTrace(pw)
                val stackTraceString = sw.toString()

                // 加入時間戳記
                val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                crashReport = "Time: $timeStamp\n\nException: $stackTraceString"

                // 寫入到內部檔案空間
                val crashFile = File(filesDir, "crash_log.txt")
                crashFile.writeText(crashReport)
                Log.e("CrashHandler", "Crash log saved to ${crashFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("CrashHandler", "Failed to save crash log", e)
            } finally {
                // 啟動 CrashDisplayActivity
                val intent = android.content.Intent(applicationContext, CrashDisplayActivity::class.java).apply {
                    putExtra("CRASH_LOG", crashReport)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                applicationContext.startActivity(intent)

                // 終止當前有問題的 Process (若非測試環境)
                if (!android.os.Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(1)
                }
            }
        }
    }
}
