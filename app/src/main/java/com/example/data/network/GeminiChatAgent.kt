package com.example.data.network

import com.example.data.model.ChatSender
import com.example.data.model.FinancialChatMessage
import com.example.data.model.TransactionEntity
import com.example.ui.viewmodel.AppCurrency
import com.example.ui.viewmodel.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class GeminiChatAgent {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun askFinancialAdvisor(
        userQuestion: String,
        customApiKey: String?,
        transactions: List<TransactionEntity>,
        currency: AppCurrency,
        language: AppLanguage,
        chatHistory: List<FinancialChatMessage>
    ): String = withContext(Dispatchers.IO) {
        val apiKey = customApiKey?.trim() ?: ""
        var apiErrorMessage: String? = null

        // 1. 若有 API Key，嘗試呼叫雲端 Gemini 模型
        if (apiKey.isNotBlank()) {
            try {
                val financialContext = buildFinancialContext(transactions, currency)
                val systemPrompt = """
                    你是一位專業、親切且具備敏銳財務洞察力的《MyMoneyKeep 雲端記帳》AI 財務顧問。
                    你的任務是根據使用者的真實記帳數據，回答其財務收支問題、統計特定品項金額（如早餐、午餐、晚餐、飲食外食、交通購物等）、診斷消費習慣，並提供具體可執行的理財與省錢建議。
                    
                    【核心回答原則】：
                    1. 必須一律使用繁體中文（台灣 zh-TW）親切回答。
                    2. 若使用者詢問特定品項、日期或月份之花費（例如「8月的午餐總共多少」、「早午晚餐總共花了多少」）：
                       - 必須仔細比對下方真實記帳明細中所有相關項目，精準計算總金額。
                       - 清楚列出計算出的加總金額，並條列列出具體的消費明細或天數筆數。
                    3. 排版保持簡潔清晰，善用 Emoji、條列式與加粗重點（例如 **NT${'$'}1,234**）。
                    4. 嚴禁輸出任何英文思考過程、草稿或中斷不完整的語句，請直接輸出完整的繁體中文回覆。
                    5. 若使用者詢問省錢或理財建議，請給予 3 點具體、可行且具建設性的步驟。
                    
                    【使用者真實記帳數據 Context】：
                    $financialContext
                """.trimIndent()

                val contentsArray = JSONArray()

                // 加入過去最近 4 輪對話歷史 (避免 Context 超長)
                val recentHistory = chatHistory.takeLast(8)
                for (msg in recentHistory) {
                    val role = if (msg.sender == ChatSender.USER) "user" else "model"
                    contentsArray.put(JSONObject().apply {
                        put("role", role)
                        put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                    })
                }

                // 加入當前使用者的問題
                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userQuestion)))
                })

                val jsonReq = JSONObject().apply {
                    put("contents", contentsArray)
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.3)
                        put("maxOutputTokens", 4096)
                        put("thinkingConfig", JSONObject().apply {
                            put("thinkingBudget", 0)
                        })
                    })
                }

                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
                val requestBody = jsonReq.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseStr = response.body?.string()
                if (response.isSuccessful && !responseStr.isNullOrBlank()) {
                    val root = JSONObject(responseStr)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val content = candidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val textBuilder = StringBuilder()
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                // 過濾內部思考過程 (thought: true)，只提取真正對話文字
                                if (!part.optBoolean("thought", false)) {
                                    val textPart = part.optString("text", "")
                                    textBuilder.append(textPart)
                                }
                            }
                            val finalText = textBuilder.toString().trim()
                            if (finalText.isNotBlank()) {
                                return@withContext finalText
                            }
                        }
                    }
                } else {
                    apiErrorMessage = "HTTP ${response.code}: ${responseStr ?: "Unknown Error"}"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                apiErrorMessage = e.localizedMessage ?: e.javaClass.simpleName
            }
        }

        // 2. 離線或無 Key 模式：本地智能統計分析回應
        buildLocalOfflineAdvice(userQuestion, transactions, currency, hasApiKey = apiKey.isNotBlank(), apiErrorMessage = apiErrorMessage)
    }

    /**
     * 結構化提取使用者的財務上下文摘要 (Context RAG)
     */
    fun buildFinancialContext(transactions: List<TransactionEntity>, currency: AppCurrency): String {
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1
        val todayStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)

        var allIncome = 0.0
        var allExpense = 0.0

        var monthIncome = 0.0
        var monthExpense = 0.0
        val monthCategoryExpense = mutableMapOf<String, Double>()

        // 7 天內花費
        val sevenDaysAgoCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -7) }
        val sevenDaysAgoTime = sevenDaysAgoCal.timeInMillis
        var last7DaysExpense = 0.0

        val monthTransactions = mutableListOf<TransactionEntity>()

        for (tx in transactions) {
            val inc = tx.income ?: 0.0
            val exp = tx.expense ?: 0.0
            allIncome += inc
            allExpense += exp

            val isThisMonth = tx.date.startsWith("$currentYear/$currentMonth/") ||
                    tx.date.startsWith("$currentYear/${String.format(Locale.US, "%02d", currentMonth)}/")

            if (isThisMonth) {
                monthTransactions.add(tx)
                monthIncome += inc
                monthExpense += exp
                if (exp > 0) {
                    val cat = tx.category
                    monthCategoryExpense[cat] = (monthCategoryExpense[cat] ?: 0.0) + exp
                }
            }

            if (tx.timestamp >= sevenDaysAgoTime) {
                last7DaysExpense += exp
            }
        }

        val monthBalance = monthIncome - monthExpense
        val totalBalance = allIncome - allExpense

        val categoryNames = mapOf(
            "A" to "收入",
            "B" to "固定支出",
            "C" to "一般支出",
            "D" to "特別支出"
        )

        val catBreakdown = monthCategoryExpense.entries
            .sortedByDescending { it.value }
            .joinToString("\n") { (cat, amount) ->
                val name = categoryNames[cat] ?: cat
                val pct = if (monthExpense > 0) (amount / monthExpense * 100).toInt() else 0
                "- $name ($cat): ${currency.format(amount)} ($pct%)"
            }.ifBlank { "- 本月尚無分類支出" }

        // 本月常見餐飲/生活品項快速聚合 (方便 AI 快速掌握早午晚餐/交通等數據)
        var breakfastSum = 0.0
        var lunchSum = 0.0
        var dinnerSum = 0.0
        for (tx in monthTransactions) {
            val exp = tx.expense ?: 0.0
            if (exp > 0) {
                val t = tx.title
                if (t.contains("早餐") || t.contains("早點") || t.contains("早午餐")) breakfastSum += exp
                if (t.contains("午餐") || t.contains("中午") || t.contains("便當") || t.contains("麵") || t.contains("飯")) lunchSum += exp
                if (t.contains("晚餐") || t.contains("宵夜") || t.contains("晚飯")) dinnerSum += exp
            }
        }

        val mealBreakdown = """
            - 本月早餐相關加總：${currency.format(breakfastSum)}
            - 本月午餐/便當相關加總：${currency.format(lunchSum)}
            - 本月晚餐/宵夜相關加總：${currency.format(dinnerSum)}
            - 本月早午晚餐合計：${currency.format(breakfastSum + lunchSum + dinnerSum)}
        """.trimIndent()

        // 提供當月全量明細 (若過多則取最新 100 筆)
        val monthTxsList = monthTransactions.takeLast(100).joinToString("\n") { tx ->
            val typeStr = if ((tx.income ?: 0.0) > 0) "收入 ${currency.format(tx.income ?: 0.0)}" else "支出 ${currency.format(tx.expense ?: 0.0)}"
            val catName = categoryNames[tx.category] ?: tx.category
            "- ${tx.date} | $catName | ${tx.title} | $typeStr"
        }.ifBlank { "- 本月尚無記帳明細" }

        // 全局最新 30 筆明細
        val recentTxs = transactions.takeLast(30).joinToString("\n") { tx ->
            val typeStr = if ((tx.income ?: 0.0) > 0) "收入 ${currency.format(tx.income ?: 0.0)}" else "支出 ${currency.format(tx.expense ?: 0.0)}"
            val catName = categoryNames[tx.category] ?: tx.category
            "- ${tx.date} | $catName | ${tx.title} | $typeStr"
        }.ifBlank { "- 尚無近期記帳明細" }

        return """
            【當前基準時間】：$todayStr
            【記帳總筆數】：${transactions.size} 筆
            【歷史累計收支】：總收入 ${currency.format(allIncome)} | 總支出 ${currency.format(allExpense)} | 累計結餘 ${currency.format(totalBalance)}
            【本月 ($currentYear/$currentMonth) 收支總計】：
            - 本月總收入：${currency.format(monthIncome)}
            - 本月總支出：${currency.format(monthExpense)}
            - 本月淨結餘：${currency.format(monthBalance)}
            - 最近 7 天支出總計：${currency.format(last7DaysExpense)}
            【本月各類別支出佔比】：
            $catBreakdown
            【本月主要飲食生活品項速查】：
            $mealBreakdown
            【本月完整記帳明細 (${monthTransactions.size} 筆)】：
            $monthTxsList
            【全期最近 30 筆明細】：
            $recentTxs
        """.trimIndent()
    }

    /**
     * 離線規則智慧財務摘要
     */
    private fun buildLocalOfflineAdvice(
        question: String,
        transactions: List<TransactionEntity>,
        currency: AppCurrency,
        hasApiKey: Boolean,
        apiErrorMessage: String? = null
    ): String {
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1

        var monthIncome = 0.0
        var monthExpense = 0.0
        val monthCategoryExpense = mutableMapOf<String, Double>()

        for (tx in transactions) {
            val inc = tx.income ?: 0.0
            val exp = tx.expense ?: 0.0
            val isThisMonth = tx.date.startsWith("$currentYear/$currentMonth/") ||
                    tx.date.startsWith("$currentYear/${String.format(Locale.US, "%02d", currentMonth)}/")

            if (isThisMonth) {
                monthIncome += inc
                monthExpense += exp
                if (exp > 0) {
                    val cat = tx.category
                    monthCategoryExpense[cat] = (monthCategoryExpense[cat] ?: 0.0) + exp
                }
            }
        }

        val topCategoryEntry = monthCategoryExpense.maxByOrNull { it.value }
        val topCategoryName = when (topCategoryEntry?.key) {
            "B" -> "固定支出 (水電/房租/電信)"
            "C" -> "一般支出 (飲食/日常交通)"
            "D" -> "特別支出 (購物/娛樂/旅遊)"
            else -> "一般支出"
        }
        val topCategoryAmount = topCategoryEntry?.value ?: 0.0

        val apiKeyNotice = if (!hasApiKey) {
            "\n\n💡 *提示：目前處於離線規則模式。若至「帳號設定」填入 Gemini API Key，即可啟用完整的自然語言深度財務問答與客製化省錢建議！*"
        } else {
            val errorDetail = if (apiErrorMessage != null) " (錯誤詳情: $apiErrorMessage)" else ""
            "\n\n⚠️ *注意：雲端 Gemini 連線失敗或逾時，目前為您降級顯示「離線統計模式」。請檢查網路連線或 API Key 是否有效。$errorDetail*"
        }

        return """
            📊 **本月 ($currentYear/$currentMonth) 財務速覽**
            
            • **本月總支出**：${currency.format(monthExpense)}
            • **本月總收入**：${currency.format(monthIncome)}
            • **本月淨結餘**：${currency.format(monthIncome - monthExpense)}
            • **最大支出類別**：$topCategoryName (${currency.format(topCategoryAmount)})
            
            🎯 **理財小叮嚀**：
            1. 建議將固定支出與非必要娛樂支出控制在收入的 50% 以內。
            2. 本月已記錄 ${transactions.size} 筆收支，持續維持記帳是累積財富的最佳起點！
            3. 若有大額非必要開銷，可設定預算上限以防止月末超支。$apiKeyNotice
        """.trimIndent()
    }
}
