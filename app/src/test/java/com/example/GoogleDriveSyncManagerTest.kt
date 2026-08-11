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
        syncManager1.updateSheetConfig("MyMoneyKeep_記帳本", "sheet_123", "MyMoneyKeep_雲端記帳本")

        // 模擬 App 重啟，新建一個 SyncManager 實例
        val syncManager2 = GoogleDriveSyncManager(app)
        assertEquals("AIzaSyTestApiKey12345", syncManager2.accountState.value.geminiApiKey)
        assertEquals("MyMoneyKeep_雲端記帳本", syncManager2.accountState.value.driveFolder)
        assertEquals("MyMoneyKeep_記帳本", syncManager2.accountState.value.sheetTitle)
        assertEquals("sheet_123", syncManager2.accountState.value.sheetId)
    }
}
