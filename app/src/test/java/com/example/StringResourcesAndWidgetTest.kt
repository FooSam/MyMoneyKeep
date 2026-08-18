package com.example

import android.content.Context
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.util.LocaleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StringResourcesAndWidgetTest {

    @Test
    fun testAllLanguagesHaveRequiredStrings() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val langCodes = listOf("zh-TW", "zh", "zh-CN", "en", "ja", "ko")

        for (lang in langCodes) {
            val localizedContext = LocaleHelper.applyLocale(baseContext, lang)
            val res = localizedContext.resources

            // 驗證報表切換鈕字串
            val weeklyStr = res.getString(R.string.reports_weekly)
            val quarterlyStr = res.getString(R.string.reports_quarterly)
            assertTrue("Weekly string should not be empty for $lang", weeklyStr.isNotBlank())
            assertTrue("Quarterly string should not be empty for $lang", quarterlyStr.isNotBlank())

            // 驗證設定頁面一般設定與架構說明字串
            val generalSettingsStr = res.getString(R.string.sync_general_settings)
            val archGuideStr = res.getString(R.string.sync_btn_architecture_guide)
            val archTitleStr = res.getString(R.string.sync_architecture_title)
            val csvBackupTitleStr = res.getString(R.string.sync_csv_backup_title)
            val csvCopyBtnStr = res.getString(R.string.sync_btn_copy_csv)

            assertTrue("General Settings string should not be empty for $lang", generalSettingsStr.isNotBlank())
            assertTrue("Architecture Guide string should not be empty for $lang", archGuideStr.isNotBlank())
            assertTrue("Architecture Title string should not be empty for $lang", archTitleStr.isNotBlank())
            assertTrue("CSV Backup title string should not be empty for $lang", csvBackupTitleStr.isNotBlank())
            assertTrue("CSV Copy button string should not be empty for $lang", csvCopyBtnStr.isNotBlank())

            // 驗證 Widget 按鈕字串
            val widgetCalcAction = res.getString(R.string.widget_btn_calc_action)
            val widgetRecordAction = res.getString(R.string.widget_btn_record_action)
            assertTrue("Widget calc action should not be empty for $lang", widgetCalcAction.isNotBlank())
            assertTrue("Widget record action should not be empty for $lang", widgetRecordAction.isNotBlank())
        }
    }

    @Test
    fun testTraditionalChineseSpecificStrings() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val localizedContext = LocaleHelper.applyLocale(baseContext, "zh-TW")
        val res = localizedContext.resources

        assertEquals("週報表", res.getString(R.string.reports_weekly))
        assertEquals("季度報表", res.getString(R.string.reports_quarterly))
        assertEquals("一般設定", res.getString(R.string.sync_general_settings))
        assertEquals("架構說明", res.getString(R.string.sync_btn_architecture_guide))
        assertEquals("雲端試算表儲存架構說明", res.getString(R.string.sync_architecture_title))
        assertEquals("試算表全表CSV備份(複製/匯入)", res.getString(R.string.sync_csv_backup_title))
        assertEquals("複製CSV試算表", res.getString(R.string.sync_btn_copy_csv))
        assertEquals("關閉", res.getString(R.string.diag_btn_close))
    }

    @Test
    fun testWidgetLayoutInflatesSuccessfully() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val views = RemoteViews(context.packageName, R.layout.widget_mymoneykeep)
        assertNotNull("Widget RemoteViews should inflate without error", views)
    }
}
