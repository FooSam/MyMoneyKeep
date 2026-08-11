package com.example

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MyApplicationTest {

    @Test
    fun testCrashHandlerLaunchesCrashDisplayActivity() {
        val app = ApplicationProvider.getApplicationContext<MyApplication>()
        
        // 模擬捕捉到未預期的崩潰
        val exception = RuntimeException("Simulated crash for testing")
        val thread = Thread.currentThread()
        
        // 我們手動呼叫 UncaughtExceptionHandler，以避免 Robolectric 真的把測試 Process 殺掉
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull("UncaughtExceptionHandler should be set", handler)
        
        handler?.uncaughtException(thread, exception)
        
        // 驗證是否發出了 Intent 去啟動 CrashDisplayActivity
        val shadowApp = shadowOf(app)
        val nextIntent = shadowApp.nextStartedActivity
        
        assertNotNull("CrashDisplayActivity should be started", nextIntent)
        assertEquals(CrashDisplayActivity::class.java.name, nextIntent.component?.className)
        
        // 驗證 Intent 參數與 Flags
        val flags = nextIntent.flags
        assertTrue("Should have FLAG_ACTIVITY_NEW_TASK", (flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertTrue("Should have FLAG_ACTIVITY_CLEAR_TASK", (flags and Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0)
        
        val crashLog = nextIntent.getStringExtra("CRASH_LOG")
        assertNotNull("Crash log should be passed in Intent", crashLog)
        assertTrue(crashLog!!.contains("Simulated crash for testing"))
    }
}
