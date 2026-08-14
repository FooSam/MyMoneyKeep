package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.model.TransactionEntity
import com.example.data.network.GeminiChatAgent
import com.example.ui.viewmodel.AppCurrency
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
class GeminiChatAgentTest {

    private val chatAgent = GeminiChatAgent()

    @Test
    fun testBuildFinancialContext() {
        val cal = Calendar.getInstance()
        val todayStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)

        val txList = listOf(
            TransactionEntity(
                id = 1,
                itemNo = 1,
                date = todayStr,
                title = "薪水",
                category = "A",
                income = 50000.0,
                expense = null,
                subtotal = 50000.0
            ),
            TransactionEntity(
                id = 2,
                itemNo = 2,
                date = todayStr,
                title = "午餐牛肉麵",
                category = "C",
                income = null,
                expense = 180.0,
                subtotal = 49820.0
            ),
            TransactionEntity(
                id = 3,
                itemNo = 3,
                date = todayStr,
                title = "房租",
                category = "B",
                income = null,
                expense = 15000.0,
                subtotal = 34820.0
            )
        )

        val contextStr = chatAgent.buildFinancialContext(txList, AppCurrency.TWD)

        assertTrue(contextStr.contains("3 筆"))
        assertTrue(contextStr.contains("NT$50000"))
        assertTrue(contextStr.contains("NT$15180"))
        assertTrue(contextStr.contains("午餐牛肉麵"))
        assertTrue(contextStr.contains("房租"))
        assertTrue(contextStr.contains("本月午餐/便當相關加總：NT$180"))
        assertTrue(contextStr.contains("本月早午晚餐合計：NT$180"))
    }

    @Test
    fun testAskFinancialAdvisorOfflineFallback() {
        kotlinx.coroutines.runBlocking {
            val response = chatAgent.askFinancialAdvisor(
                userQuestion = "幫我分析這個月的花費",
                customApiKey = null,
                transactions = emptyList(),
                currency = AppCurrency.TWD,
                language = com.example.ui.viewmodel.AppLanguage.TRADITIONAL_CHINESE,
                chatHistory = emptyList()
            )
            assertTrue(response.contains("財務速覽"))
            assertTrue(response.contains("離線規則模式"))
        }
    }
}
