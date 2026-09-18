package com.example.util

import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat

/**
 * 現代化邊到邊 (Edge-to-Edge) 輔助工具
 *
 * 遵循 Android 15 (API 35+) 規範：
 * 1. 全面採用官方標準推薦之 androidx.activity.enableEdgeToEdge()，確保跨 Android 各版本穩定向後相容。
 * 2. 徹底移除已在 Android 15 淘汰的 Window.setStatusBarColor、Window.setNavigationBarColor
 *    與 Window.isStatusBarContrastEnforced / Window.isNavigationBarContrastEnforced。
 * 3. 針對 API 30+ 採用官方標準之 LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS，
 *    完全避免使用已淘汰之 LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES。
 * 4. 支援動態依據主題安全切換狀態列與導航列圖示之明暗外觀。
 */
object EdgeToEdgeHelper {

    fun applyEdgeToEdge(activity: ComponentActivity, isDarkTheme: Boolean = false) {
        val statusBarStyle = if (isDarkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }

        val navigationBarStyle = if (isDarkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }

        // 調用官方標準推薦的 enableEdgeToEdge API
        activity.enableEdgeToEdge(
            statusBarStyle = statusBarStyle,
            navigationBarStyle = navigationBarStyle
        )

        // Android 11 (API 30)+: 使用官方推薦的 ALWAYS 挖孔模式，取代已淘汰之 SHORT_EDGES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    fun updateSystemBarsTheme(activity: ComponentActivity, isDarkTheme: Boolean) {
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        // isAppearanceLightStatusBars = true 表示狀態列顯示深色文字/圖示 (適用淺色背景)
        insetsController.isAppearanceLightStatusBars = !isDarkTheme
        insetsController.isAppearanceLightNavigationBars = !isDarkTheme
    }
}
