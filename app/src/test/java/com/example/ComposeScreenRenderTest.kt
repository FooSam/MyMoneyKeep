package com.example

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ComposeScreenRenderTest {

    @Test
    fun testMainActivityLaunchesWithoutActivityResultRegistryCrash() {
        // 模擬啟動 MainActivity，驗證 CompositionLocalProvider 是否正確提供 LocalActivityResultRegistryOwner
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            assertNotNull("MainActivity should launch successfully", activity)
        }
        scenario.close()
    }
}
