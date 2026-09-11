package com.example.util

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat

/**
 * 現代化邊到邊 (Edge-to-Edge) 輔助工具
 *
 * 遵循 Android 15 規範：
 * 1. 使用 WindowCompat.setDecorFitsSystemWindows(window, false) 提供全版本邊到邊相容性。
 * 2. 針對 API 30+ 採用官方標準之 LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS，
 *    完全避免使用已在 Android 15 淘汰的 LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES。
 * 3. 設置透明狀態列與導航列，API 29+ 關閉系統強制對比遮罩。
 * 4. 支援動態依據主題切換狀態列與導航列圖示之明暗外觀。
 */
object EdgeToEdgeHelper {

    fun applyEdgeToEdge(activity: Activity, isDarkTheme: Boolean = false) {
        val window = activity.window

        // 啟用邊到邊繪製 (讓視窗內容延伸至系統列下方)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 設定狀態列與導航列背景為完全透明
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // Android 10 (API 29)+: 停用系統預設強制加上的半透明遮罩
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        // Android 11 (API 30)+: 使用官方推薦的 ALWAYS 挖孔模式，取代已淘汰之 SHORT_EDGES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        // 更新狀態列與導航列圖示顏色 (淺色主題顯示深色圖示，深色主題顯示淺色圖示)
        updateSystemBarsTheme(activity, isDarkTheme)
    }

    fun updateSystemBarsTheme(activity: Activity, isDarkTheme: Boolean) {
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        // isAppearanceLightStatusBars = true 表示狀態列顯示深色文字/圖示 (適用淺色背景)
        insetsController.isAppearanceLightStatusBars = !isDarkTheme
        insetsController.isAppearanceLightNavigationBars = !isDarkTheme
    }
}
