package com.example.data.model

import androidx.compose.ui.graphics.Color

data class CustomCategory(
    val code: String,
    val name: String,
    val isIncome: Boolean,
    val colorHex: String
) {
    fun parseColor(): Color {
        return try {
            Color(android.graphics.Color.parseColor(colorHex))
        } catch (e: Exception) {
            Color(0xFF9E9E9E)
        }
    }

    companion object {
        val UNKNOWN = CustomCategory(
            code = "",
            name = "未知類別",
            isIncome = false,
            colorHex = "#9E9E9E"
        )

        val DEFAULT_CATEGORIES = listOf(
            CustomCategory("A", "收入", true, "#4CAF50"),
            CustomCategory("B", "固定支出", false, "#FF9800"),
            CustomCategory("C", "一般支出", false, "#2196F3"),
            CustomCategory("D", "特別支出", false, "#9C27B0")
        )

        val PRESET_COLORS = listOf(
            "#4CAF50", // 綠色 Green
            "#FF9800", // 橘色 Orange
            "#2196F3", // 藍色 Blue
            "#9C27B0", // 紫色 Purple
            "#F44336", // 紅色 Red
            "#E91E63", // 粉紅 Pink
            "#00BCD4", // 青色 Cyan
            "#009688", // 藍綠 Teal
            "#8BC34A", // 淺綠 Lime
            "#FFEB3B", // 黃色 Yellow
            "#FFC107", // 琥珀 Amber
            "#795548", // 棕色 Brown
            "#607D8B", // 藍灰 Slate
            "#3F51B5", // 靛藍 Indigo
            "#673AB7", // 深紫 Deep Purple
            "#FF5722"  // 深橘 Deep Orange
        )
    }
}
