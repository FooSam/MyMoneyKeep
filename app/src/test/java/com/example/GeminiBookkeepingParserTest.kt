package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.network.GeminiBookkeepingParser
import com.example.ui.viewmodel.AppCurrency
import com.example.ui.viewmodel.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GeminiBookkeepingParserTest {

    private val parser = GeminiBookkeepingParser()

    @Test
    fun testRelativeDatesAndTitleCleaning() {
        val cal = Calendar.getInstance()
        val todayStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)

        cal.time = Date()
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val tomorrowStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)

        cal.time = Date()
        cal.add(Calendar.DAY_OF_MONTH, -1)
        val yesterdayStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)

        cal.time = Date()
        cal.add(Calendar.DAY_OF_MONTH, -2)
        val dayBeforeYesterdayStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)

        // 測試 1：「今天早餐60」
        val res1 = parser.fallbackLocalParse("今天早餐60", hasApiKey = true, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res1.isValid)
        assertEquals(todayStr, res1.date)
        assertEquals("早餐", res1.title)
        assertEquals(60.0, res1.expense ?: 0.0, 0.001)

        // 測試 2：「今天的午餐是95」
        val res2 = parser.fallbackLocalParse("今天的午餐是95", hasApiKey = true, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res2.isValid)
        assertEquals(todayStr, res2.date)
        assertEquals("午餐", res2.title)
        assertEquals(95.0, res2.expense ?: 0.0, 0.001)

        // 測試 3：「明天早餐40」
        val res3 = parser.fallbackLocalParse("明天早餐40", hasApiKey = true, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res3.isValid)
        assertEquals(tomorrowStr, res3.date)
        assertEquals("早餐", res3.title)
        assertEquals(40.0, res3.expense ?: 0.0, 0.001)

        // 測試 4：「昨天晚上去吃火鍋花了580元」
        val res4 = parser.fallbackLocalParse("昨天晚上去吃火鍋花了580元", hasApiKey = true, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res4.isValid)
        assertEquals(yesterdayStr, res4.date)
        assertEquals("火鍋", res4.title)
        assertEquals(580.0, res4.expense ?: 0.0, 0.001)

        // 測試 5：「前天加油1200元」
        val res5 = parser.fallbackLocalParse("前天加油1200元", hasApiKey = true, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res5.isValid)
        assertEquals(dayBeforeYesterdayStr, res5.date)
        assertEquals("加油", res5.title)
        assertEquals(1200.0, res5.expense ?: 0.0, 0.001)
    }

    @Test
    fun testCurrencyFormatting() {
        assertEquals("-NT$60", AppCurrency.TWD.format(-60.0))
        assertEquals("-NT$155", AppCurrency.TWD.format(-155.0))
        assertEquals("NT$500", AppCurrency.TWD.format(500.0))
        assertEquals("-$15.50", AppCurrency.USD.format(-15.5))
    }
}
