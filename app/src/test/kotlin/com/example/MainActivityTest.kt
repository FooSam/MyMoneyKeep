package com.example

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
@Config(application = MyApplication::class, sdk = [34])
class MainActivityTest {

    @Test
    fun `test MainActivity initializes without crashing`() {
        // 利用 Robolectric 建立 MainActivity，這會執行 onCreate 週期並初始化 ViewModel 與資料庫連線
        try {
            val activityController = Robolectric.buildActivity(MainActivity::class.java)
            val activity = activityController.create().start().resume().get()
            
            // 如果跑到這裡沒有拋出例外，代表基本的依賴注入、Room 與 Application 初始化皆已過關
            assertNotNull("MainActivity should not be null", activity)
        } catch (e: Exception) {
            fail("MainActivity crashed during initialization: ${e.message}")
        }
    }
}
