package com.example.data.network

import com.example.data.model.CategoryType
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
import java.util.regex.Pattern

enum class ParserEngineType(val displayName: String, val badge: String) {
    CLOUD_GEMINI("雲端 Gemini 1.5 Flash", "✨ 雲端 AI"),
    EDGE_NANO("Gemini Nano 地端神經網路", "🧠 地端 Nano"),
    LOCAL_NLP("本地高階語意引擎", "⚡ 離線智能")
}

data class ParsedTransaction(
    val date: String,
    val title: String,
    val category: String,
    val income: Double?,
    val expense: Double?,
    val aiResponse: String,
    val isValid: Boolean = true,
    val warningMessage: String? = null,
    val engineType: ParserEngineType = ParserEngineType.LOCAL_NLP
)

object ValidationStrings {
    fun getNoApiKeyWarning(lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.TRADITIONAL_CHINESE ->
                "⚠️ 未設定 Gemini API Key 且輸入格式不符合規則！\n如需使用 AI 自然語言自動解析，請至『雲端與設定』填入 API Key。\n非 AI 模式請依格式輸入：『[日期] 項目 金額』(例如：12/5 早餐 80 或 晚餐 120元)。"
            AppLanguage.SIMPLIFIED_CHINESE ->
                "⚠️ 未设置 Gemini API Key 且输入格式不符合规则！\n如需使用 AI 自然语言自动解析，请至『云端与设置』填入 API Key。\n非 AI 模式请依格式输入：『[日期] 项目 金额』(例如：12/5 早餐 80 或 晚餐 120元)。"
            AppLanguage.ENGLISH ->
                "⚠️ Gemini API Key is not configured and the input format is invalid!\nTo use AI natural language recognition, please enter an API Key in 'Sync & Settings'.\nFor manual mode, please use format: '[Date] Item Amount' (e.g., 12/5 Breakfast 80 or Dinner 120)."
            AppLanguage.JAPANESE ->
                "⚠️ Gemini API Keyが未設定で、入力形式が正しくありません！\nAI自然言語認識を使用するには『設定』でAPI Keyを入力してください。\n通常モードの形式：『[日付] 項目 金額』（例：12/5 朝食 80）。"
            AppLanguage.KOREAN ->
                "⚠️ Gemini API Key가 설정되지 않았으며 입력 형식이 올바르지 않습니다!\nAI 자연어 인식을 사용하려면 '설정'에서 API Key를 입력하세요.\n수동 입력 형식: '[날짜] 항목 금액' (예: 12/5 점심 80)."
        }
    }

    fun getMissingAmountWarning(lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.TRADITIONAL_CHINESE ->
                "⚠️ 輸入格式錯誤：未檢測到有效金額！\n請輸入包含數字金額的內容（例如：晚餐 120元 或 2026/8/5 加油 500）。"
            AppLanguage.SIMPLIFIED_CHINESE ->
                "⚠️ 输入格式错误：未检测到有效金额！\n请输入包含数字金额的内容（例如：晚餐 120元 或 2026/8/5 加油 500）。"
            AppLanguage.ENGLISH ->
                "⚠️ Invalid format: No valid numeric amount detected!\nPlease include an amount (e.g., Dinner 120 or 2026/8/5 Gas 500)."
            AppLanguage.JAPANESE ->
                "⚠️ 入力形式エラー：有効な金額が見つかりません！\n金額を含めて入力してください（例：夕食 120円）。"
            AppLanguage.KOREAN ->
                "⚠️ 입력 형식 오류: 유효한 금액이 감지되지 않았습니다!\n금액을 포함하여 입력해주세요 (예: 저녁 12000)."
        }
    }

    fun getMissingTitleWarning(lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.TRADITIONAL_CHINESE ->
                "⚠️ 輸入格式錯誤：缺少項目名稱/標題！\n請填寫記帳項目名稱（例如：早餐 80 或 洗車 300）。"
            AppLanguage.SIMPLIFIED_CHINESE ->
                "⚠️ 输入格式错误：缺少项目名称/标题！\n请填写记账项目名称（例如：早餐 80 或 洗车 300）。"
            AppLanguage.ENGLISH ->
                "⚠️ Invalid format: Item name is missing!\nPlease provide an item name (e.g., Breakfast 80 or Carwash 300)."
            AppLanguage.JAPANESE ->
                "⚠️ 入力形式エラー：項目名が見つかりません！\n項目名を入力してください（例：朝食 80）。"
            AppLanguage.KOREAN ->
                "⚠️ 입력 형식 오류: 항목명이 누락되었습니다!\n항목명을 입력해주세요 (예: 아침 8000)."
        }
    }

    fun getSuccessResponse(
        lang: AppLanguage,
        dateStr: String,
        title: String,
        amount: Double,
        categoryCode: String,
        isIncome: Boolean,
        isLocal: Boolean = true
    ): String {
        val typeLabel = if (isIncome) {
            when (lang) {
                AppLanguage.TRADITIONAL_CHINESE -> "收入"
                AppLanguage.SIMPLIFIED_CHINESE -> "收入"
                AppLanguage.ENGLISH -> "Income"
                AppLanguage.JAPANESE -> "収入"
                AppLanguage.KOREAN -> "수입"
            }
        } else {
            when (lang) {
                AppLanguage.TRADITIONAL_CHINESE -> "支出"
                AppLanguage.SIMPLIFIED_CHINESE -> "支出"
                AppLanguage.ENGLISH -> "Expense"
                AppLanguage.JAPANESE -> "支出"
                AppLanguage.KOREAN -> "지출"
            }
        }
        val amountInt = if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
        val prefix = if (!isLocal) "" else if (lang == AppLanguage.TRADITIONAL_CHINESE || lang == AppLanguage.SIMPLIFIED_CHINESE) "⚡ [離線極速辨識] " else "⚡ [Offline] "
        
        return when (lang) {
            AppLanguage.TRADITIONAL_CHINESE -> "${prefix}已成功記錄：$dateStr $typeLabel 「$title」 $amountInt 元 (類別: $categoryCode)"
            AppLanguage.SIMPLIFIED_CHINESE -> "${prefix}已成功记录：$dateStr $typeLabel 『$title』 $amountInt 元 (类别: $categoryCode)"
            AppLanguage.ENGLISH -> "${prefix}Recorded successfully: $dateStr $typeLabel '$title' $$amountInt (Cat: $categoryCode)"
            AppLanguage.JAPANESE -> "${prefix}記録完了：$dateStr $typeLabel 「$title」 $amountInt 円 (分類: $categoryCode)"
            AppLanguage.KOREAN -> "${prefix}기록 완료: $dateStr $typeLabel '$title' $amountInt 원 (분류: $categoryCode)"
        }
    }
}

class GeminiBookkeepingParser {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 支援可插拔的地端 Edge AI / Gemini Nano 接口 (Android AICore)
    var onDeviceNanoProvider: ((String, AppLanguage) -> ParsedTransaction?)? = null

    suspend fun parseUserInput(
        inputText: String,
        customApiKey: String? = null,
        language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE
    ): ParsedTransaction = withContext(Dispatchers.IO) {
        val apiKey = customApiKey?.trim() ?: ""
        var apiErrorMessage: String? = null

        // 第一軌：雲端 Gemini 3.5 Flash (BYOK 模式)
        if (apiKey.isNotBlank()) {
            try {
                val cal = Calendar.getInstance()
                val todayStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH) + 1
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SUNDAY -> "星期日"
                    Calendar.MONDAY -> "星期一"
                    Calendar.TUESDAY -> "星期二"
                    Calendar.WEDNESDAY -> "星期三"
                    Calendar.THURSDAY -> "星期四"
                    Calendar.FRIDAY -> "星期五"
                    Calendar.SATURDAY -> "星期六"
                    else -> ""
                }

                val systemPrompt = """
                    你是一個極度精準的多語系智慧記帳 AI 助手。請分析使用者的自然語言記帳輸入，解析出日期、項目標題、記帳類別、金額，並輸出 JSON 格式。
                    今天的基準時間為：${year}年${month}月${day}日 ($todayStr)，$dayOfWeek。

                    【重要解析規則】：
                    1. date (日期，格式必須為 YYYY/M/D，例如 2026/8/11)：
                       - 若使用者提及相對時間（如「今天」、「明天」、「昨天」、「前天」、「大前天」、「後天」、「大後天」、「上週五」、「這禮拜三」、「8月15日」、「12/5」等），請務必以今天的基準日期 ($todayStr) 進行精確計算，輸出計算後的實際西元日期！
                       - 若未提及任何時間，預設為今天的日期：$todayStr。
                    2. title (項目標題)：
                       - ⚠️【核心要求】：標題必須只保留「乾淨的純粹品項或事項名稱」（例如：「早餐」、「午餐」、「晚餐」、「加油」、「高鐵車票」、「衣服」、「飲料」）。
                       - ⚠️【嚴禁包含】：絕對不可包含任何時間詞（如「今天」、「明天」、「昨天」、「前天」、「後天」）、贅字或動作語助詞（如「的」、「是」、「吃了」、「喝了」、「買了」、「花了」、「付了」、「去」、「大概」、「總共」）！
                       - 範例 1：「今天早餐60」-> date: "$todayStr", title: "早餐", expense: 60
                       - 範例 2：「今天的午餐是95」-> date: "$todayStr", title: "午餐", expense: 95
                       - 範例 3：「明天早餐40」-> date: (明天的實際西元日期), title: "早餐", expense: 40
                       - 範例 4：「昨天晚上去吃火鍋花了580元」-> date: (昨天的實際西元日期), title: "火鍋", expense: 580
                    3. category (類別代碼，必須為 A, B, C, D 其中之一)：
                       - A: 收入 (薪水, 獎金, 投資, 利息, 退費, 兼差)
                       - B: 固定支出 (電話費, 卡費, 水電費, 房租, 寬頻, 保險, 驗車費, 瓦斯, 學費, 貸款)
                       - C: 一般支出 (早餐, 午餐, 晚餐, 宵夜, 加油, 咖啡, 飲料, 買菜, 日用品, 超商)
                       - D: 特別支出 (停車費, 罰單, 娛樂, 維修, 剪髮, 購物, 衣服, 旅行, 醫療, 看診, 禮金)
                    4. income: 收入金額 (純數值，若為支出則填 null)
                    5. expense: 支出金額 (純數值，若為收入則填 null)
                    6. summary: 簡短回覆語句 (${language.displayName})
                """.trimIndent()

                val jsonReq = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", inputText)))
                    }))
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                    })
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                        put("temperature", 0.1)
                        put("maxOutputTokens", 2048)
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
                                if (!part.optBoolean("thought", false)) {
                                    textBuilder.append(part.optString("text", ""))
                                }
                            }
                            val text = textBuilder.toString().trim()
                            if (text.isNotBlank()) {
                                val parsedJson = JSONObject(text)
                                val date = if (parsedJson.has("date") && !parsedJson.isNull("date")) parsedJson.getString("date") else todayStr
                                val rawTitle = if (parsedJson.has("title") && !parsedJson.isNull("title")) parsedJson.getString("title") else inputText
                                val title = cleanTitleStopWords(rawTitle).ifBlank { rawTitle }
                                val income = if (parsedJson.has("income") && !parsedJson.isNull("income")) parsedJson.getDouble("income") else null
                                val expense = if (parsedJson.has("expense") && !parsedJson.isNull("expense")) parsedJson.getDouble("expense") else null
                                val rawCategory = if (parsedJson.has("category") && !parsedJson.isNull("category")) parsedJson.getString("category") else null

                                val hasAmount = (income != null && income > 0) || (expense != null && expense > 0)
                                if (hasAmount && title.isNotBlank()) {
                                    val category = rawCategory?.uppercase(Locale.ROOT) ?: CategoryType.inferCode(income != null, title)
                                    val summary = if (parsedJson.has("summary") && !parsedJson.isNull("summary")) {
                                        parsedJson.getString("summary")
                                    } else {
                                        ValidationStrings.getSuccessResponse(language, date, title, income ?: expense ?: 0.0, category, income != null, isLocal = false)
                                    }

                                    return@withContext ParsedTransaction(
                                        date = date,
                                        title = title,
                                        category = category,
                                        income = income,
                                        expense = expense,
                                        aiResponse = summary,
                                        isValid = true,
                                        engineType = ParserEngineType.CLOUD_GEMINI
                                    )
                                }
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

        // 第二軌：地端 Edge AI / Gemini Nano (Android AICore 適配)
        try {
            val nanoResult = onDeviceNanoProvider?.invoke(inputText, language)
            if (nanoResult != null && nanoResult.isValid) {
                return@withContext nanoResult.copy(engineType = ParserEngineType.EDGE_NANO)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 第三軌：本地高階自然語言規則引擎 (Enhanced Local NLP Engine - 100% 離線可用)
        parseLocalNlp(inputText, hasApiKey = apiKey.isNotBlank(), language = language, apiErrorMessage = apiErrorMessage)
    }

    private fun cleanTitleStopWords(input: String): String {
        var clean = input
            .replace("今天", "")
            .replace("明天", "")
            .replace("昨天", "")
            .replace("前天", "")
            .replace("大前天", "")
            .replace("後天", "")
            .replace("大後天", "")
            .replace("今日", "")
            .replace("明日", "")
            .replace("昨日", "")
            .replace("早上", "")
            .replace("中午", "")
            .replace("晚上", "")
            .replace("下午", "")
            .replace("上午", "")
            .replace("夜間", "")
            .replace("半夜", "")
            .replace("去吃", "")
            .replace("去買", "")
            .replace("吃了", "")
            .replace("喝了", "")
            .replace("買了", "")
            .replace("花了", "")
            .replace("付了", "")
            .replace("給了", "")
            .replace("去了", "")
            .replace("的", "")
            .replace("是", "")
            .replace("去", "")
            .trim()

        // 去除品項前贅詞動詞 (例如：吃牛肉麵 -> 牛肉麵, 繳房租 -> 房租, 買飲料 -> 飲料, 發薪水 -> 薪水)
        val leadingVerbs = listOf("去吃", "去買", "去喝", "去", "吃", "喝", "買", "繳", "付", "發")
        for (v in leadingVerbs) {
            if (clean.startsWith(v) && clean.length > v.length) {
                clean = clean.removePrefix(v).trim()
            }
        }
        return clean
    }

    fun fallbackLocalParse(input: String, hasApiKey: Boolean, language: AppLanguage): ParsedTransaction {
        return parseLocalNlp(input, hasApiKey, language, null)
    }

    /**
     * 高階本地 NLP 解析引擎：
     * 1. 支援中文大寫數字轉換 (例如：一百八 -> 180, 兩千五 -> 2500, 1.5萬 -> 15000)
     * 2. 支援相對日期與星期 (今天, 昨天, 上週三, 這禮拜五等)
     * 3. 智慧類別關鍵字推斷與分數打分
     */
    fun parseLocalNlp(input: String, hasApiKey: Boolean, language: AppLanguage, apiErrorMessage: String? = null): ParsedTransaction {
        val cal = Calendar.getInstance()
        var dateStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)

        var workingText = convertChineseNumbers(input)

        // 1. 相對時間詞辨識
        when {
            workingText.contains("大前天") -> {
                cal.add(Calendar.DAY_OF_MONTH, -3)
                dateStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)
                workingText = workingText.replace("大前天", "")
            }
            workingText.contains("前天") -> {
                cal.add(Calendar.DAY_OF_MONTH, -2)
                dateStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)
                workingText = workingText.replace("前天", "")
            }
            workingText.contains("昨天") || workingText.contains("昨日") -> {
                cal.add(Calendar.DAY_OF_MONTH, -1)
                dateStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)
                workingText = workingText.replace("昨天", "").replace("昨日", "")
            }
            workingText.contains("大後天") -> {
                cal.add(Calendar.DAY_OF_MONTH, 3)
                dateStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)
                workingText = workingText.replace("大後天", "")
            }
            workingText.contains("後天") -> {
                cal.add(Calendar.DAY_OF_MONTH, 2)
                dateStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)
                workingText = workingText.replace("後天", "")
            }
            workingText.contains("明天") || workingText.contains("明日") -> {
                cal.add(Calendar.DAY_OF_MONTH, 1)
                dateStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)
                workingText = workingText.replace("明天", "").replace("明日", "")
            }
            workingText.contains("今天") || workingText.contains("今日") -> {
                workingText = workingText.replace("今天", "").replace("今日", "")
            }
            // 星期 / 禮拜 (如：上週五、這禮拜三)
            workingText.contains("上週") || workingText.contains("上星期") || workingText.contains("上禮拜") -> {
                val targetDay = extractDayOfWeek(workingText)
                if (targetDay != null) {
                    cal.add(Calendar.WEEK_OF_YEAR, -1)
                    cal.set(Calendar.DAY_OF_WEEK, targetDay)
                    dateStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)
                    workingText = workingText.replace(Regex("(上週|上星期|上禮拜)[一二三四五六日天]"), "")
                }
            }
            workingText.contains("這週") || workingText.contains("本週") || workingText.contains("這星期") || workingText.contains("這禮拜") -> {
                val targetDay = extractDayOfWeek(workingText)
                if (targetDay != null) {
                    cal.set(Calendar.DAY_OF_WEEK, targetDay)
                    dateStr = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(cal.time)
                    workingText = workingText.replace(Regex("(這週|本週|這星期|這禮拜)[一二三四五六日天]"), "")
                }
            }
        }

        // 2. 絕對日期格式 (如 2026/8/10, 8/10, 8-10, 8月10日)
        val datePattern = Pattern.compile("(\\d{4}[/\\-年])?(\\d{1,2})[/\\-月](\\d{1,2})[日號]?")
        val dateMatcher = datePattern.matcher(workingText)
        if (dateMatcher.find()) {
            val rawYear = dateMatcher.group(1)?.replace("-", "")?.replace("/", "")?.replace("年", "")
            val year = if (!rawYear.isNullOrBlank()) rawYear else Calendar.getInstance().get(Calendar.YEAR).toString()
            val month = dateMatcher.group(2)
            val day = dateMatcher.group(3)
            dateStr = "$year/$month/$day"
            workingText = workingText.replace(datePattern.toRegex(), "")
        }

        // 3. 提取金額數字
        val numPattern = Pattern.compile("(\\d+(\\.\\d+)?)")
        val numMatcher = numPattern.matcher(workingText)
        val numbers = mutableListOf<Double>()
        while (numMatcher.find()) {
            val matched = numMatcher.group(1)
            if (matched != null) {
                matched.toDoubleOrNull()?.let { if (it > 0) numbers.add(it) }
            }
        }

        val amount = numbers.lastOrNull() ?: 0.0

        // 4. 清理項目標題
        var title = workingText
            .replace(numPattern.toRegex(), "")
            .replace("收入", "")
            .replace("支出", "")
            .replace("元", "")
            .replace("塊錢", "")
            .replace("塊", "")
            .replace("NT$", "")
            .replace("$", "")
            .replace("yen", "", ignoreCase = true)
            .replace("円", "")
            .replace("台幣", "")

        title = cleanTitleStopWords(title)

        // 檢查 1: 金額是否存在
        if (amount <= 0) {
            val warning = if (!hasApiKey) {
                ValidationStrings.getNoApiKeyWarning(language)
            } else {
                ValidationStrings.getMissingAmountWarning(language) + (if (apiErrorMessage != null) "\n(API連線失敗: $apiErrorMessage)" else "")
            }
            return ParsedTransaction(
                date = dateStr,
                title = title,
                category = "C",
                income = null,
                expense = null,
                aiResponse = warning,
                isValid = false,
                warningMessage = warning,
                engineType = ParserEngineType.LOCAL_NLP
            )
        }

        // 檢查 2: 標題是否存在
        if (title.isBlank()) {
            val warning = ValidationStrings.getMissingTitleWarning(language) + (if (apiErrorMessage != null) "\n(API連線失敗: $apiErrorMessage)" else "")
            return ParsedTransaction(
                date = dateStr,
                title = "",
                category = "C",
                income = null,
                expense = null,
                aiResponse = warning,
                isValid = false,
                warningMessage = warning,
                engineType = ParserEngineType.LOCAL_NLP
            )
        }

        // 5. 收入/支出與類別精準推斷
        val isIncome = input.contains("收入") || input.contains("發薪") || input.contains("退費") ||
                input.contains("退款") || input.contains("賺") || input.contains("獎金") ||
                input.contains("紅包") || input.contains("利息") || input.contains("股息") ||
                input.contains("income", ignoreCase = true)

        val categoryCode = inferCategorySmartly(title, isIncome)
        val incomeVal = if (isIncome) amount else null
        val expenseVal = if (!isIncome) amount else null

        val responseMsg = ValidationStrings.getSuccessResponse(
            lang = language,
            dateStr = dateStr,
            title = title,
            amount = amount,
            categoryCode = categoryCode,
            isIncome = isIncome
        ) + (if (apiErrorMessage != null) "\n(⚠️ 雲端API連線失敗，已自動降級離線模式: $apiErrorMessage)" else "")

        return ParsedTransaction(
            date = dateStr,
            title = title,
            category = categoryCode,
            income = incomeVal,
            expense = expenseVal,
            aiResponse = responseMsg,
            isValid = true,
            engineType = ParserEngineType.LOCAL_NLP
        )
    }

    private fun extractDayOfWeek(text: String): Int? {
        return when {
            text.contains("日") || text.contains("天") -> Calendar.SUNDAY
            text.contains("一") -> Calendar.MONDAY
            text.contains("二") -> Calendar.TUESDAY
            text.contains("三") -> Calendar.WEDNESDAY
            text.contains("四") -> Calendar.THURSDAY
            text.contains("五") -> Calendar.FRIDAY
            text.contains("六") -> Calendar.SATURDAY
            else -> null
        }
    }

    /**
     * 智能推斷類別代碼 (A: 收入, B: 固定支出, C: 一般支出, D: 特別支出)
     */
    private fun inferCategorySmartly(title: String, isIncome: Boolean): String {
        if (isIncome) return "A"

        val lower = title.lowercase(Locale.ROOT)

        // B: 固定支出 (水電瓦斯房租卡費通信等)
        val bKeywords = listOf(
            "房租", "水費", "電費", "瓦斯", "電話費", "話費", "寬頻", "網路費", "卡費", "信用卡",
            "保險", "健保", "勞保", "貸款", "車貸", "房貸", "學費", "驗車", "管理費", "訂閱",
            "netflix", "spotify", "youtube", "icloud", "chatgpt"
        )
        if (bKeywords.any { lower.contains(it) }) return "B"

        // D: 特別支出 (娛樂購物旅行醫療大額等)
        val dKeywords = listOf(
            "衣服", "褲子", "鞋子", "包包", "購物", "網購", "蝦皮", "淘寶", "電影", "遊戲",
            "唱歌", "ktv", "旅行", "旅遊", "門票", "飯店", "住宿", "聚會", "喝酒", "玩具",
            "展覽", "健身", "演唱會", "看診", "醫藥", "醫療", "醫院", "診所", "掛號", "剪髮",
            "美髮", "禮金", "維修", "保養", "罰單", "機票", "高鐵"
        )
        if (dKeywords.any { lower.contains(it) }) return "D"

        // 預設與一般食衣住行日常皆為 C (一般支出)
        return "C"
    }

    /**
     * 中文自然語言數字轉換 (如「一百八」-> 180, 「兩百五」-> 250, 「三千」-> 3000, 「1.5萬」-> 15000, 「50k」-> 50000)
     */
    private fun convertChineseNumbers(text: String): String {
        var result = text

        // 支援 k/K 縮寫 (例如 50k -> 50000)
        val kPattern = Pattern.compile("(\\d+(\\.\\d+)?)[kK]")
        val kMatcher = kPattern.matcher(result)
        val kBuffer = StringBuffer()
        while (kMatcher.find()) {
            val num = kMatcher.group(1)?.toDoubleOrNull() ?: 0.0
            kMatcher.appendReplacement(kBuffer, (num * 1000).toLong().toString())
        }
        kMatcher.appendTail(kBuffer)
        result = kBuffer.toString()

        // 支援 萬 縮寫 (例如 1.5萬 -> 15000, 2萬 -> 20000)
        val wanPattern = Pattern.compile("(\\d+(\\.\\d+)?)萬")
        val wanMatcher = wanPattern.matcher(result)
        val wanBuffer = StringBuffer()
        while (wanMatcher.find()) {
            val num = wanMatcher.group(1)?.toDoubleOrNull() ?: 0.0
            wanMatcher.appendReplacement(wanBuffer, (num * 10000).toLong().toString())
        }
        wanMatcher.appendTail(wanBuffer)
        result = wanBuffer.toString()

        // 常見口語百十轉換
        val directReplacements = mapOf(
            "一百八" to "180", "一百五" to "150", "一百二" to "120", "一百" to "100",
            "兩百五" to "250", "兩百八" to "280", "兩百" to "200", "二百" to "200",
            "三百五" to "350", "三百" to "300", "四百" to "400", "五百" to "500",
            "六百" to "600", "七百" to "700", "八百" to "800", "九百" to "900",
            "一千兩百" to "1200", "一千五" to "1500", "一千" to "1000",
            "兩千" to "2000", "三千" to "3000", "五千" to "5000", "一萬" to "10000",
            "兩萬" to "20000", "三萬" to "30000", "五萬" to "50000"
        )
        for ((k, v) in directReplacements) {
            result = result.replace(k, v)
        }

        return result
    }
}
