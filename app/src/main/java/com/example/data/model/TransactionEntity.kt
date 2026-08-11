package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemNo: Int = 0,             // 項目 (e.g., 1, 2, 3...)
    val date: String,                // 日期 (e.g., "2025/12/5")
    val title: String,               // 標題 (e.g., "發薪日", "早餐")
    val category: String,            // 類別 ("A", "B", "C", "D")
    val income: Double? = null,      // 收入
    val expense: Double? = null,     // 支出
    val subtotal: Double = 0.0,      // 小計
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false    // 雲端同步標記
)

enum class CategoryType(val code: String, val label: String, val defaultType: String) {
    A("A", "收入", "INCOME"),
    B("B", "固定支出", "EXPENSE"),
    C("C", "一般支出", "EXPENSE"),
    D("D", "特別支出", "EXPENSE");

    companion object {
        fun fromCode(code: String): CategoryType {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: C
        }

        fun inferCode(isIncome: Boolean, title: String): String {
            if (isIncome) return "A"
            val lowerTitle = title.lowercase()
            return when {
                lowerTitle.contains("電話") || lowerTitle.contains("水費") || lowerTitle.contains("電費") ||
                        lowerTitle.contains("寬頻") || lowerTitle.contains("卡費") || lowerTitle.contains("瓦斯") ||
                        lowerTitle.contains("保險") || lowerTitle.contains("驗車") || lowerTitle.contains("房租") -> "B"
                lowerTitle.contains("早餐") || lowerTitle.contains("午餐") || lowerTitle.contains("晚餐") ||
                        lowerTitle.contains("加油") || lowerTitle.contains("保養") || lowerTitle.contains("飲料") -> "C"
                else -> "D"
            }
        }
    }
}
