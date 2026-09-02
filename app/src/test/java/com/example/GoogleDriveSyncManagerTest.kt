package com.example

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.model.TransactionEntity
import com.example.data.sync.GoogleDriveSyncManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GoogleDriveSyncManagerTest {

    @Test
    fun testDefaultSheetTitle() {
        val title = GoogleDriveSyncManager.getDefaultSheetTitle()
        assertTrue(title.contains("MyMoneyKeep_記帳本"))
        assertTrue(title.length >= 17)
    }

    @Test
    fun testHexToSheetsColorConversion() {
        // 測試 6 位色碼 #4CAF50 -> Red: 0x4C/255 = 0.298, Green: 0xAF/255 = 0.686, Blue: 0x50/255 = 0.313
        val greenColor = GoogleDriveSyncManager.hexToSheetsColor("#4CAF50")
        assertEquals(76f / 255f, greenColor.red, 0.01f)
        assertEquals(175f / 255f, greenColor.green, 0.01f)
        assertEquals(80f / 255f, greenColor.blue, 0.01f)

        // 測試 8 位色碼 #FF4CAF50
        val argbColor = GoogleDriveSyncManager.hexToSheetsColor("#FF4CAF50")
        assertEquals(76f / 255f, argbColor.red, 0.01f)
        assertEquals(175f / 255f, argbColor.green, 0.01f)
        assertEquals(80f / 255f, argbColor.blue, 0.01f)

        // 測試無效色彩 fallback
        val fallbackColor = GoogleDriveSyncManager.hexToSheetsColor("invalid")
        assertNotNull(fallbackColor)
        assertEquals(0.15f, fallbackColor.red, 0.01f)
    }

    @Test
    fun testGenerateAndParseCsvContent() {
        val app = ApplicationProvider.getApplicationContext<MyApplication>()
        val syncManager = GoogleDriveSyncManager(app)

        val transactions = listOf(
            TransactionEntity(
                itemNo = 1,
                date = "2026/08/10",
                title = "薪資收入",
                category = "A",
                income = 50000.0,
                expense = null,
                subtotal = 50000.0,
                isSynced = false
            ),
            TransactionEntity(
                itemNo = 2,
                date = "2026/08/10",
                title = "午餐便當",
                category = "B",
                income = null,
                expense = 120.0,
                subtotal = 49880.0,
                isSynced = false
            )
        )

        // 模擬產生 CSV
        val csv = syncManager.generateCsvContent(transactions)
        assertTrue(csv.contains("薪資收入"))
        assertTrue(csv.contains("午餐便當"))
        assertTrue(csv.contains("50000"))
        assertTrue(csv.contains("120"))

        // 模擬解析 CSV
        val parsed = syncManager.parseCsvContent(csv)
        assertEquals(2, parsed.size)
        assertEquals("薪資收入", parsed[0].title)
        assertEquals(50000.0, parsed[0].income ?: 0.0, 0.001)
        assertEquals("午餐便當", parsed[1].title)
        assertEquals(120.0, parsed[1].expense ?: 0.0, 0.001)
        assertEquals(49880.0, parsed[1].subtotal, 0.001)
    }

    @Test
    fun testApiKeyAndFolderPersistence() {
        val app = ApplicationProvider.getApplicationContext<MyApplication>()
        val syncManager1 = GoogleDriveSyncManager(app)

        // 模擬使用者在設定頁面輸入 API Key 與設定資料夾
        syncManager1.updateGeminiApiKey("AIzaSyTestApiKey12345")
        syncManager1.updateSheetConfig("2026_MyMoneyKeep_記帳本", "sheet_123", "MyMoneyKeep_雲端記帳本")

        // 模擬 App 重啟，新建一個 SyncManager 實例
        val syncManager2 = GoogleDriveSyncManager(app)
        assertEquals("AIzaSyTestApiKey12345", syncManager2.accountState.value.geminiApiKey)
        assertEquals("MyMoneyKeep_雲端記帳本", syncManager2.accountState.value.driveFolder)
        assertEquals("2026_MyMoneyKeep_記帳本", syncManager2.accountState.value.sheetTitle)
        assertEquals("sheet_123", syncManager2.accountState.value.sheetId)
    }

    @Test
    fun testDateChronologicalSortingInSyncAndCsv() {
        val app = ApplicationProvider.getApplicationContext<MyApplication>()
        val syncManager = GoogleDriveSyncManager(app)

        // 模擬使用者在不同日期記帳，包含易產生字典序錯誤的日期字串
        val unsortedTransactions = listOf(
            TransactionEntity(itemNo = 1, date = "2026/8/10", title = "買鞋子", category = "C", income = null, expense = 1000.0, subtotal = 0.0, isSynced = false),
            TransactionEntity(itemNo = 2, date = "2026/8/1", title = "月初薪資", category = "A", income = 50000.0, expense = null, subtotal = 0.0, isSynced = false),
            TransactionEntity(itemNo = 3, date = "2026/8/21", title = "聚餐", category = "C", income = null, expense = 600.0, subtotal = 0.0, isSynced = false),
            TransactionEntity(itemNo = 4, date = "2026/8/2", title = "超市買菜", category = "C", income = null, expense = 500.0, subtotal = 0.0, isSynced = false),
            TransactionEntity(itemNo = 5, date = "2026/8/20", title = "加油", category = "B", income = null, expense = 800.0, subtotal = 0.0, isSynced = false),
            TransactionEntity(itemNo = 6, date = "2026/8/19", title = "買書", category = "C", income = null, expense = 350.0, subtotal = 0.0, isSynced = false)
        )

        // 匯出 CSV 並檢查排序是否為真實時間順序（8/1 -> 8/2 -> 8/10 -> 8/19 -> 8/20 -> 8/21）
        val csv = syncManager.generateCsvContent(unsortedTransactions)
        val parsed = syncManager.parseCsvContent(csv)

        assertEquals(6, parsed.size)
        assertEquals("2026/8/1", parsed[0].date)
        assertEquals("月初薪資", parsed[0].title)
        assertEquals(50000.0, parsed[0].subtotal, 0.001)

        assertEquals("2026/8/2", parsed[1].date)
        assertEquals("超市買菜", parsed[1].title)
        assertEquals(49500.0, parsed[1].subtotal, 0.001)

        assertEquals("2026/8/10", parsed[2].date)
        assertEquals("買鞋子", parsed[2].title)
        assertEquals(48500.0, parsed[2].subtotal, 0.001)

        assertEquals("2026/8/19", parsed[3].date)
        assertEquals("買書", parsed[3].title)
        assertEquals(48150.0, parsed[3].subtotal, 0.001)

        assertEquals("2026/8/20", parsed[4].date)
        assertEquals("加油", parsed[4].title)
        assertEquals(47350.0, parsed[4].subtotal, 0.001)

        assertEquals("2026/8/21", parsed[5].date)
        assertEquals("聚餐", parsed[5].title)
        assertEquals(46750.0, parsed[5].subtotal, 0.001)

        // 驗證項次均依排序後正確重設為 1..6
        for (i in parsed.indices) {
            assertEquals(i + 1, parsed[i].itemNo)
        }
    }

    @Test
    fun testDateUtilsParseYearMonth() {
        assertEquals("2026.08", com.example.util.DateUtils.parseYearMonth("2026/8/1"))
        assertEquals("2026.08", com.example.util.DateUtils.parseYearMonth("2026/08/10"))
        assertEquals("2026.09", com.example.util.DateUtils.parseYearMonth("2026-9-2"))
        assertEquals("2026.09", com.example.util.DateUtils.parseYearMonth("2026/09/30"))
        assertEquals("2025.12", com.example.util.DateUtils.parseYearMonth("2025/12/31"))
        assertEquals("2025.01", com.example.util.DateUtils.parseYearMonth("2025/1/5"))

        // 測試 null 與無效日期 fallback
        val currentYearMonth = java.text.SimpleDateFormat("yyyy.MM", java.util.Locale.getDefault()).format(java.util.Date())
        assertEquals(currentYearMonth, com.example.util.DateUtils.parseYearMonth(null))
        assertEquals(currentYearMonth, com.example.util.DateUtils.parseYearMonth("invalid"))
    }

    @Test
    fun testMonthlyGroupingAndSubtotalCalculation() {
        // 模擬跨 8 月與 9 月的多筆交易
        val multiMonthTransactions = listOf(
            TransactionEntity(itemNo = 1, date = "2026/8/5", title = "8月薪水", category = "A", income = 45000.0, expense = null, subtotal = 0.0, isSynced = false),
            TransactionEntity(itemNo = 2, date = "2026/8/10", title = "買衣服", category = "C", income = null, expense = 2000.0, subtotal = 0.0, isSynced = false),
            TransactionEntity(itemNo = 3, date = "2026/9/1", title = "9月薪水", category = "A", income = 45000.0, expense = null, subtotal = 0.0, isSynced = false),
            TransactionEntity(itemNo = 4, date = "2026/9/2", title = "早午餐", category = "B", income = null, expense = 250.0, subtotal = 0.0, isSynced = false)
        )

        val monthGroups = multiMonthTransactions.groupBy { com.example.util.DateUtils.parseYearMonth(it.date) }.toSortedMap()
        assertEquals(2, monthGroups.size)
        assertTrue(monthGroups.containsKey("2026.08"))
        assertTrue(monthGroups.containsKey("2026.09"))

        // 驗證 2026.08 月份獨立小計
        val augList = monthGroups["2026.08"]!!
        assertEquals(2, augList.size)
        var augSubtotal = 0.0
        augList.forEach {
            augSubtotal += (it.income ?: 0.0) - (it.expense ?: 0.0)
        }
        assertEquals(43000.0, augSubtotal, 0.001)

        // 驗證 2026.09 月份獨立小計 (從 0 開始獨立累計，不疊加 8 月結餘)
        val sepList = monthGroups["2026.09"]!!
        assertEquals(2, sepList.size)
        var sepSubtotal = 0.0
        sepList.forEach {
            sepSubtotal += (it.income ?: 0.0) - (it.expense ?: 0.0)
        }
        assertEquals(44750.0, sepSubtotal, 0.001)
    }

    @Test
    fun testCrossMonthRestorationAndGlobalSorting() {
        // 模擬從 8 月與 9 月兩個不同 Sheet 讀取的原始未排序交易
        val rawRestoredList = listOf(
            TransactionEntity(itemNo = 0, date = "2026/9/2", title = "早午餐", category = "B", income = null, expense = 250.0, subtotal = 0.0, isSynced = true),
            TransactionEntity(itemNo = 0, date = "2026/8/5", title = "8月薪水", category = "A", income = 45000.0, expense = null, subtotal = 0.0, isSynced = true),
            TransactionEntity(itemNo = 0, date = "2026/9/1", title = "9月薪水", category = "A", income = 45000.0, expense = null, subtotal = 0.0, isSynced = true),
            TransactionEntity(itemNo = 0, date = "2026/8/10", title = "買衣服", category = "C", income = null, expense = 2000.0, subtotal = 0.0, isSynced = true)
        )

        // 執行還原排序與流水號/小計重新計算
        val sortedList = rawRestoredList.sortedWith(
            compareBy(
                { com.example.util.DateUtils.parseDateToComparable(it.date) },
                { it.itemNo },
                { it.id }
            )
        )
        var currentSubtotal = 0.0
        val finalRestored = sortedList.mapIndexed { index, t ->
            if (t.income != null) currentSubtotal += t.income
            if (t.expense != null) currentSubtotal -= t.expense
            t.copy(itemNo = index + 1, subtotal = currentSubtotal)
        }

        assertEquals(4, finalRestored.size)
        assertEquals("2026/8/5", finalRestored[0].date)
        assertEquals(1, finalRestored[0].itemNo)
        assertEquals(45000.0, finalRestored[0].subtotal, 0.001)

        assertEquals("2026/8/10", finalRestored[1].date)
        assertEquals(2, finalRestored[1].itemNo)
        assertEquals(43000.0, finalRestored[1].subtotal, 0.001)

        assertEquals("2026/9/1", finalRestored[2].date)
        assertEquals(3, finalRestored[2].itemNo)
        assertEquals(88000.0, finalRestored[2].subtotal, 0.001)

        assertEquals("2026/9/2", finalRestored[3].date)
        assertEquals(4, finalRestored[3].itemNo)
        assertEquals(87750.0, finalRestored[3].subtotal, 0.001)
    }
}
