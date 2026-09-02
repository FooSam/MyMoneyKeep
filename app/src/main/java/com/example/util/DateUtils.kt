package com.example.util

/**
 * 統一日期處理與數值比對工具模組
 */
object DateUtils {

    /**
     * 將各類常見日期字串（支援 yyyy/M/d, yyyy/MM/dd, yyyy-MM-dd, yyyy-M-d 等格式）
     * 解析為可直接進行數值比較的 Long 數值 (格式為 yyyyMMdd)。
     *
     * 例如：
     * - "2026/8/1" -> 20260801L
     * - "2026/8/2" -> 20260802L
     * - "2026/08/10" -> 20260810L
     * - "2026-8-19" -> 20260819L
     *
     * 若字串無法解析或格式異常，安全返回 0L，避免拋出例外。
     */
    fun parseDateToComparable(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            val clean = dateStr.trim().replace("-", "/")
            val parts = clean.split("/")
            if (parts.size >= 3) {
                val y = parts[0].trim().toLongOrNull() ?: 0L
                val m = parts[1].trim().toLongOrNull() ?: 0L
                val d = parts[2].trim().toLongOrNull() ?: 0L
                y * 10000L + m * 100L + d
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 將各類常見日期字串解析為標準「YYYY.MM」月份字串（如 "2026.08"、"2026.09"）。
     * 若字串無法解析，則以當前系統年月為預設值。
     */
    fun parseYearMonth(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) {
            return java.text.SimpleDateFormat("yyyy.MM", java.util.Locale.getDefault()).format(java.util.Date())
        }
        return try {
            val clean = dateStr.trim().replace("-", "/")
            val parts = clean.split("/")
            if (parts.size >= 2) {
                val y = parts[0].trim().toIntOrNull() ?: 0
                val m = parts[1].trim().toIntOrNull() ?: 0
                if (y in 1900..2200 && m in 1..12) {
                    String.format(java.util.Locale.US, "%04d.%02d", y, m)
                } else {
                    java.text.SimpleDateFormat("yyyy.MM", java.util.Locale.getDefault()).format(java.util.Date())
                }
            } else {
                java.text.SimpleDateFormat("yyyy.MM", java.util.Locale.getDefault()).format(java.util.Date())
            }
        } catch (e: Exception) {
            java.text.SimpleDateFormat("yyyy.MM", java.util.Locale.getDefault()).format(java.util.Date())
        }
    }
}
