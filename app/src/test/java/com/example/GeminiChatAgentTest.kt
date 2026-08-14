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

    @Test
    fun testBuildFinancialContextChronologicalSorting() {
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1

        // 提供亂序日期 (如 8/13, 8/3, 8/11, 8/5)
        val txList = listOf(
            TransactionEntity(id = 1, itemNo = 1, date = "$currentYear/$currentMonth/13", title = "午餐13", category = "C", income = null, expense = 100.0, subtotal = 100.0),
            TransactionEntity(id = 2, itemNo = 2, date = "$currentYear/$currentMonth/3", title = "午餐3", category = "C", income = null, expense = 95.0, subtotal = 195.0),
            TransactionEntity(id = 3, itemNo = 3, date = "$currentYear/$currentMonth/11", title = "午餐11", category = "C", income = null, expense = 95.0, subtotal = 290.0),
            TransactionEntity(id = 4, itemNo = 4, date = "$currentYear/$currentMonth/5", title = "午餐5", category = "C", income = null, expense = 95.0, subtotal = 385.0)
        )

        val contextStr = chatAgent.buildFinancialContext(txList, AppCurrency.TWD)

        // 驗證生成的 Context 中，明細是否按日期順向排列 (3 -> 5 -> 11 -> 13)
        val idx3 = contextStr.indexOf("午餐3")
        val idx5 = contextStr.indexOf("午餐5")
        val idx11 = contextStr.indexOf("午餐11")
        val idx13 = contextStr.indexOf("午餐13")

        assertTrue(idx3 != -1 && idx5 != -1 && idx11 != -1 && idx13 != -1)
        assertTrue("午餐3 應在 午餐5 之前", idx3 < idx5)
        assertTrue("午餐5 應在 午餐11 之前", idx5 < idx11)
        assertTrue("午餐11 應在 午餐13 之前", idx11 < idx13)
    }
}
