package com.example

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.util.EdgeToEdgeHelper
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EdgeToEdgeVerificationTest {

    @Test
    fun testMainActivityLaunchesWithEdgeToEdgeHelper() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            assertNotNull("MainActivity should not be null", activity)
            // 驗證呼叫 EdgeToEdgeHelper 不會拋出異常
            EdgeToEdgeHelper.applyEdgeToEdge(activity, isDarkTheme = false)
            EdgeToEdgeHelper.updateSystemBarsTheme(activity, isDarkTheme = true)
        }
        scenario.close()
    }

    @Test
    fun testCrashDisplayActivityLaunchesWithEdgeToEdgeAndInsets() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), CrashDisplayActivity::class.java).apply {
            putExtra("CRASH_LOG", "Test Crash Log Details for Edge-to-Edge Verification")
        }
        val scenario = ActivityScenario.launch<CrashDisplayActivity>(intent)
        scenario.onActivity { activity ->
            assertNotNull("CrashDisplayActivity should launch as ComponentActivity", activity)
            // 驗證 EdgeToEdge 配置
            EdgeToEdgeHelper.applyEdgeToEdge(activity, isDarkTheme = false)
        }
        scenario.close()
    }
}
