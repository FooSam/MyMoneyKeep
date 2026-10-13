package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.network.GeminiBookkeepingParser
import com.example.data.network.ParserEngineType
import com.example.ui.viewmodel.AppCurrency
import com.example.ui.viewmodel.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("C", res1.category)
        assertEquals(ParserEngineType.LOCAL_NLP, res1.engineType)

        // 測試 2：「今天的午餐是95」
        val res2 = parser.fallbackLocalParse("今天的午餐是95", hasApiKey = true, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res2.isValid)
        assertEquals(todayStr, res2.date)
        assertEquals("午餐", res2.title)
        assertEquals(95.0, res2.expense ?: 0.0, 0.001)
        assertEquals("C", res2.category)

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
        assertEquals("C", res4.category)

        // 測試 5：「前天加油1200元」
        val res5 = parser.fallbackLocalParse("前天加油1200元", hasApiKey = true, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res5.isValid)
        assertEquals(dayBeforeYesterdayStr, res5.date)
        assertEquals("加油", res5.title)
        assertEquals(1200.0, res5.expense ?: 0.0, 0.001)
        assertEquals("C", res5.category)
    }

    @Test
    fun testEnhancedChineseNumeralsAndCategories() {
        // 測試中文數字轉換：一百八
        val res1 = parser.parseLocalNlp("吃牛肉麵一百八", hasApiKey = false, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res1.isValid)
        assertEquals("牛肉麵", res1.title)
        assertEquals(180.0, res1.expense ?: 0.0, 0.001)
        assertEquals("C", res1.category)

        // 測試萬與K縮寫：房租1.5萬 (固定支出類別 B)
        val res2 = parser.parseLocalNlp("繳房租1.5萬", hasApiKey = false, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res2.isValid)
        assertEquals("房租", res2.title)
        assertEquals(15000.0, res2.expense ?: 0.0, 0.001)
        assertEquals("B", res2.category)

        // 測試收入類別 A：發薪水45000
        val res3 = parser.parseLocalNlp("發薪水45000", hasApiKey = false, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res3.isValid)
        assertEquals("薪水", res3.title)
        assertEquals(45000.0, res3.income ?: 0.0, 0.001)
        assertEquals("A", res3.category)

        // 測試特別支出類別 D：買衣服兩千
        val res4 = parser.parseLocalNlp("買衣服兩千", hasApiKey = false, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res4.isValid)
        assertEquals("衣服", res4.title)
        assertEquals(2000.0, res4.expense ?: 0.0, 0.001)
        assertEquals("D", res4.category)
    }

    @Test
    fun testCurrencyFormatting() {
        assertEquals("-NT$60", AppCurrency.TWD.format(-60.0))
        assertEquals("-NT$155", AppCurrency.TWD.format(-155.0))
        assertEquals("NT$500", AppCurrency.TWD.format(500.0))
        assertEquals("-$15.50", AppCurrency.USD.format(-15.5))
    }

    @Test
    fun testCandidateModelsAndEngineType() {
        assertEquals("雲端 Gemini 智能引擎 (3.8+ 階梯備援)", ParserEngineType.CLOUD_GEMINI.displayName)
        val models = GeminiBookkeepingParser.CANDIDATE_MODELS
        assertEquals("gemini-3.8-flash", models.first())
        assertEquals("gemini-3.0-flash", models.last())
        assertTrue(models.contains("gemini-3.7-flash"))
        assertTrue(models.contains("gemini-3.6-flash"))
        assertTrue(models.contains("gemini-3.5-flash"))
        assertTrue(models.contains("gemini-3.1-flash"))
        assertFalse(models.contains("gemini-2.5-flash"))
        assertEquals(6, models.size)
    }

    @Test
    fun testSpecificVehicleAndSpeechPrefixParsing() {
        // 1. 測試「今天機車加油125元」：確保標題完整保留「機車加油」，不被過度精簡為「加油」
        val res1 = parser.parseLocalNlp("今天機車加油125元", hasApiKey = false, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res1.isValid)
        assertEquals("機車加油", res1.title)
        assertEquals(125.0, res1.expense ?: 0.0, 0.001)
        assertEquals("C", res1.category)

        // 2. 測試「昨天汽車加油800元」：確保標題完整保留「汽車加油」
        val res2 = parser.parseLocalNlp("昨天汽車加油800元", hasApiKey = false, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res2.isValid)
        assertEquals("汽車加油", res2.title)
        assertEquals(800.0, res2.expense ?: 0.0, 0.001)
        assertEquals("C", res2.category)

        // 3. 測試常見語音口語前綴「幫我記剛才機車加油125」：口語前綴濾除且品項精確保留
        val res3 = parser.parseLocalNlp("幫我記剛才機車加油125", hasApiKey = false, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res3.isValid)
        assertEquals("機車加油", res3.title)
        assertEquals(125.0, res3.expense ?: 0.0, 0.001)
        assertEquals("C", res3.category)

        // 4. 測試「去骨雞腿便當110元」：驗證「去骨」之「去」未被誤當成贅詞去除
        val res4 = parser.parseLocalNlp("去骨雞腿便當110元", hasApiKey = false, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res4.isValid)
        assertEquals("去骨雞腿便當", res4.title)
        assertEquals(110.0, res4.expense ?: 0.0, 0.001)
        assertEquals("C", res4.category)

        // 5. 測試車輛保養「換機油250元」：類別精準歸入特別支出/保養 D
        val res5 = parser.parseLocalNlp("換機油250元", hasApiKey = false, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res5.isValid)
        assertEquals("換機油", res5.title)
        assertEquals(250.0, res5.expense ?: 0.0, 0.001)
        assertEquals("D", res5.category)
    }

    @Test
    fun testModifyIntentParsing() {
        // 1. 意圖檢測
        assertTrue(parser.isModifyIntent("午餐改100"))
        assertTrue(parser.isModifyIntent("今天的午餐改100"))
        assertTrue(parser.isModifyIntent("改100"))
        assertTrue(parser.isModifyIntent("剛剛的晚餐更正為250"))
        org.junit.Assert.assertFalse(parser.isModifyIntent("午餐95"))
        org.junit.Assert.assertFalse(parser.isModifyIntent("早餐 60 元"))

        // 2. 本地解析「午餐改100」
        val res1 = parser.parseLocalNlp("午餐改100", hasApiKey = false, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res1.isValid)
        assertTrue(res1.isUpdate)
        assertEquals("午餐", res1.title)
        assertEquals(100.0, res1.expense ?: 0.0, 0.001)

        // 3. 本地解析「今天的午餐改100」
        val res2 = parser.parseLocalNlp("今天的午餐改100", hasApiKey = false, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res2.isValid)
        assertTrue(res2.isUpdate)
        assertEquals("午餐", res2.title)
        assertEquals(100.0, res2.expense ?: 0.0, 0.001)

        // 4. 本地解析「改100」（無指定品項，修改上一筆）
        val res3 = parser.parseLocalNlp("改100", hasApiKey = false, language = AppLanguage.TRADITIONAL_CHINESE)
        assertTrue(res3.isValid)
        assertTrue(res3.isUpdate)
        assertEquals("", res3.title)
        assertEquals(100.0, res3.expense ?: 0.0, 0.001)
    }
}
