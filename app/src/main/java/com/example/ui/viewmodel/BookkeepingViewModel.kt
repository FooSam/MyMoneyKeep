package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.CategoryType
import com.example.data.model.ChatSender
import com.example.data.model.CurrencyInfo
import com.example.data.model.CustomCategory
import com.example.data.model.ExchangeTimeRange
import com.example.data.model.FinancialChatMessage
import com.example.data.model.HistoricalRatePoint
import com.example.data.model.SupportedCurrencies
import com.example.data.model.TransactionEntity
import com.example.data.network.GeminiBookkeepingParser
import com.example.data.network.GeminiChatAgent
import com.example.data.network.ParsedTransaction
import com.example.data.repository.CurrencyRepository
import com.example.data.repository.TransactionRepository
import com.example.data.sync.GoogleDriveSyncManager
import com.example.util.CalculatorEngine
import com.example.util.DateUtils
import com.example.widget.MyMoneyKeepWidgetProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "USER" or "AI"
    val text: String,
    val parsedTransaction: ParsedTransaction? = null,
    val isAiQuestionResponse: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

enum class AppStyleTheme(val code: String, val displayName: String) {
    LIGHT("light", "淺色風格 (預設)"),
    DARK("dark", "深色風格"),
    MECHANICAL("mechanical", "機械風格"),
    CUTE("cute", "可愛風格"),
    SUNNY("sunny", "陽光風格"),
    STUDENT("student", "學生風格"),
    OFFICIAL("official", "公文風格")
}

enum class AppLanguage(val code: String, val displayName: String) {
    TRADITIONAL_CHINESE("zh-TW", "繁體中文"),
    SIMPLIFIED_CHINESE("zh-CN", "简体中文"),
    ENGLISH("en", "English"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            if (code == null) return TRADITIONAL_CHINESE
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: if (code.startsWith("zh")) TRADITIONAL_CHINESE else ENGLISH
        }
    }
}

enum class AppCurrency(
    val code: String,
    val symbol: String,
    val displayName: String,
    val decimalPlaces: Int
) {
    TWD("TWD", "NT$", "新台幣 (TWD)", 0),
    USD("USD", "$", "美元 (USD)", 2),
    JPY("JPY", "¥", "日圓 (JPY)", 0),
    KRW("KRW", "₩", "韓元 (KRW)", 0),
    CNY("CNY", "¥", "人民幣 (CNY)", 2);

    fun format(amount: Double): String {
        val isNegative = amount < 0
        val absAmount = kotlin.math.abs(amount)
        val formatted = if (decimalPlaces == 0) {
            "$symbol${absAmount.toLong()}"
        } else {
            "$symbol${String.format(java.util.Locale.US, "%.2f", absAmount)}"
        }
        return if (isNegative) "-$formatted" else formatted
    }
}

enum class ReportTimeRange(val label: String) {
    ALL("全時期"),
    WEEK("週報表"),
    MONTH("月報表"),
    QUARTER("季報表"),
    YEAR("年報表")
}

enum class LoginMode {
    UNSET,       // 尚未選擇模式 (彈出 Welcome Login 畫面)
    GUEST,       // 不使用帳號登入 (單機離線模式)
    GOOGLE_USER  // Google 帳號登入模式
}

data class CategorySummary(
    val code: String,
    val label: String,
    val totalAmount: Double,
    val percentage: Float,
    val itemCount: Int,
    val isIncome: Boolean,
    val colorHex: String = "#2196F3"
)

data class ReportAnalysisData(
    val timeRange: ReportTimeRange,
    val periodLabel: String,
    val totalIncome: Double,
    val totalExpense: Double,
    val netBalance: Double,
    val categorySummaries: List<CategorySummary>
)

class BookkeepingViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = TransactionRepository(db.transactionDao())
    val syncManager = GoogleDriveSyncManager(application)

    val googleAccountState = syncManager.accountState

    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val parser = GeminiBookkeepingParser()
    private val chatAgent = GeminiChatAgent()

    private fun isFinancialQuestion(text: String): Boolean {
        val q = text.trim()
        val questionKeywords = listOf("多少", "嗎", "建議", "分析", "結餘", "花費", "支出", "收入", "算", "統計", "理財", "怎麼辦", "太高", "太低", "最近")
        val hasQuestionMark = q.contains("？") || q.contains("?")
        // If it lacks numbers, it's very likely a question or chat (since transactions need amounts)
        val hasNumbers = q.any { it.isDigit() } || q.contains("萬") || q.contains("千") || q.contains("百") || q.contains("十")

        if (hasQuestionMark) return true
        if (!hasNumbers) return true

        for (kw in questionKeywords) {
            if (q.contains(kw)) return true
        }
        return false
    }

    private suspend fun handleFinancialQuestion(text: String) {
        try {
            val apiKey = syncManager.accountState.value.geminiApiKey
            
            // To provide context to the agent, we can convert past chatMessages (that are questions/answers) to FinancialChatMessage
            val history = _chatMessages.value.filter { it.isAiQuestionResponse || it.sender == "USER" }.map { 
                FinancialChatMessage(
                    sender = if (it.sender == "USER") ChatSender.USER else ChatSender.ASSISTANT,
                    text = it.text
                )
            }

            val answer = chatAgent.askFinancialAdvisor(
                userQuestion = text,
                customApiKey = apiKey,
                transactions = allTransactions.value,
                currency = _selectedCurrency.value,
                language = _selectedLanguage.value,
                chatHistory = history
            )
            val aiMsg = ChatMessage(
                sender = "AI", 
                text = answer,
                isAiQuestionResponse = true
            )
            _chatMessages.value = _chatMessages.value + aiMsg
        } catch (e: Exception) {
            val errorMsg = ChatMessage(
                sender = "AI",
                text = "抱歉，目前處理您的提問時發生錯誤：${e.message}。請稍後再試。",
                isAiQuestionResponse = true
            )
            _chatMessages.value = _chatMessages.value + errorMsg
        }
    }

    private fun notifyWidgetUpdate() {
        try {
            MyMoneyKeepWidgetProvider.updateAllWidgets(getApplication())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Login State Mode
    private val _loginMode = MutableStateFlow<LoginMode>(
        if (syncManager.accountState.value.isSignedIn) LoginMode.GOOGLE_USER else LoginMode.UNSET
    )
    val loginMode: StateFlow<LoginMode> = _loginMode

    private val prefs = application.getSharedPreferences("mymoneykeep_user_prefs", android.content.Context.MODE_PRIVATE)

    // UI States
    private val _showHomeBalance = MutableStateFlow(prefs.getBoolean("show_home_balance", true))
    val showHomeBalance: StateFlow<Boolean> = _showHomeBalance

    fun setShowHomeBalance(show: Boolean) {
        _showHomeBalance.value = show
        prefs.edit().putBoolean("show_home_balance", show).apply()
    }

    private data class ParsedDate(val year: Int, val month: Int, val day: Int)

    private fun parseDateString(dateStr: String): ParsedDate? {
        val clean = dateStr.trim().replace("-", "/")
        val parts = clean.split("/")
        if (parts.size >= 3) {
            val y = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val d = parts[2].toIntOrNull() ?: return null
            return ParsedDate(y, m, d)
        }
        return null
    }

    val currentMonthBalance: StateFlow<Double> = allTransactions.map { transactions ->
        val cal = java.util.Calendar.getInstance()
        val curYear = cal.get(java.util.Calendar.YEAR)
        val curMonth = cal.get(java.util.Calendar.MONTH) + 1
        var monthIncome = 0.0
        var monthExpense = 0.0
        transactions.forEach { tx ->
            val pd = parseDateString(tx.date)
            if (pd != null && pd.year == curYear && pd.month == curMonth) {
                monthIncome += (tx.income ?: 0.0)
                monthExpense += (tx.expense ?: 0.0)
            }
        }
        monthIncome - monthExpense
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    private val _selectedLanguage = MutableStateFlow(AppLanguage.TRADITIONAL_CHINESE)
    val selectedLanguage: StateFlow<AppLanguage> = _selectedLanguage

    private val _selectedCurrency = MutableStateFlow(AppCurrency.TWD)
    val selectedCurrency: StateFlow<AppCurrency> = _selectedCurrency

    private val _selectedStyleTheme = MutableStateFlow(AppStyleTheme.LIGHT)
    val selectedStyleTheme: StateFlow<AppStyleTheme> = _selectedStyleTheme

    private val _customCategories = MutableStateFlow<List<CustomCategory>>(CustomCategory.DEFAULT_CATEGORIES)
    val customCategories: StateFlow<List<CustomCategory>> = _customCategories

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    enum class SortField { DATE, TITLE, AMOUNT }
    enum class SortDirection { ASC, DESC }

    private val _sortField = MutableStateFlow(SortField.DATE)
    val sortField: StateFlow<SortField> = _sortField

    private val _sortDirection = MutableStateFlow(SortDirection.DESC)
    val sortDirection: StateFlow<SortDirection> = _sortDirection

    private val _selectedTimeRange = MutableStateFlow(ReportTimeRange.MONTH)
    val selectedTimeRange: StateFlow<ReportTimeRange> = _selectedTimeRange

    private val _selectedPeriodLabel = MutableStateFlow(
        run {
            val cal = java.util.Calendar.getInstance()
            val curYear = cal.get(java.util.Calendar.YEAR)
            val curMonth = cal.get(java.util.Calendar.MONTH) + 1
            String.format(java.util.Locale.TAIWAN, "%d/%02d", curYear, curMonth)
        }
    )
    val selectedPeriodLabel: StateFlow<String> = _selectedPeriodLabel

    val availablePeriodOptions: StateFlow<List<String>> = combine(
        allTransactions,
        selectedTimeRange
    ) { list, timeRange ->
        val cal = java.util.Calendar.getInstance()
        val curYear = cal.get(java.util.Calendar.YEAR)
        val curMonth = cal.get(java.util.Calendar.MONTH) + 1
        val curQuarter = (curMonth - 1) / 3 + 1

        when (timeRange) {
            ReportTimeRange.ALL -> listOf("全部歷史紀錄")
            ReportTimeRange.WEEK -> listOf("本週 (7 天內)", "近 14 天", "近 30 天")
            ReportTimeRange.MONTH -> {
                val monthsSet = mutableSetOf<String>()
                monthsSet.add(String.format(java.util.Locale.TAIWAN, "%d/%02d", curYear, curMonth))
                list.forEach { t ->
                    parseDateString(t.date)?.let { pd ->
                        monthsSet.add(String.format(java.util.Locale.TAIWAN, "%d/%02d", pd.year, pd.month))
                    }
                }
                monthsSet.toList().sortedDescending()
            }
            ReportTimeRange.QUARTER -> {
                val qSet = mutableSetOf<String>()
                qSet.add("$curYear Q$curQuarter")
                list.forEach { t ->
                    parseDateString(t.date)?.let { pd ->
                        val q = (pd.month - 1) / 3 + 1
                        qSet.add("${pd.year} Q$q")
                    }
                }
                qSet.toList().sortedDescending()
            }
            ReportTimeRange.YEAR -> {
                val ySet = mutableSetOf<String>()
                ySet.add("$curYear 年")
                list.forEach { t ->
                    parseDateString(t.date)?.let { pd ->
                        ySet.add("${pd.year} 年")
                    }
                }
                ySet.toList().sortedDescending()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("全部歷史紀錄"))

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _voiceTranscript = MutableStateFlow("")
    val voiceTranscript: StateFlow<String> = _voiceTranscript

    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput

    private val _isProcessingAi = MutableStateFlow(false)
    val isProcessingAi: StateFlow<Boolean> = _isProcessingAi

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "AI",
                text = "您好！我是 MyMoneyKeep 記帳助手。請長按或點擊大麥克風說話，或在此輸入例如：「2025/12/5 發薪日 收入47540」或「晚餐 95 元」，我會為您整理至 Google 試算表！"
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages

    private val _lastAddedTransaction = MutableStateFlow<TransactionEntity?>(null)
    val lastAddedTransaction: StateFlow<TransactionEntity?> = _lastAddedTransaction

    // Currency Exchange & Calculator State
    private val currencyRepository = CurrencyRepository(getApplication())

    private val _baseCurrency = MutableStateFlow(SupportedCurrencies.findByCode("USD"))
    val baseCurrency: StateFlow<CurrencyInfo> = _baseCurrency

    private val _targetCurrency = MutableStateFlow(SupportedCurrencies.findByCode("TWD"))
    val targetCurrency: StateFlow<CurrencyInfo> = _targetCurrency

    private val _calcExpression = MutableStateFlow("1")
    val calcExpression: StateFlow<String> = _calcExpression

    private val _exchangeRates = MutableStateFlow<Map<String, Double>>(emptyMap())
    val exchangeRates: StateFlow<Map<String, Double>> = _exchangeRates

    private val _currentRate = MutableStateFlow(31.90)
    val currentRate: StateFlow<Double> = _currentRate

    private val _historicalRates = MutableStateFlow<List<HistoricalRatePoint>>(emptyList())
    val historicalRates: StateFlow<List<HistoricalRatePoint>> = _historicalRates

    private val _exchangeTimeRange = MutableStateFlow(ExchangeTimeRange.ONE_MONTH)
    val exchangeTimeRange: StateFlow<ExchangeTimeRange> = _exchangeTimeRange

    private val _isExchangeLoading = MutableStateFlow(false)
    val isExchangeLoading: StateFlow<Boolean> = _isExchangeLoading

    private val _lastExchangeUpdate = MutableStateFlow("")
    val lastExchangeUpdate: StateFlow<String> = _lastExchangeUpdate

    private val _isExchangeFromCache = MutableStateFlow(false)
    val isExchangeFromCache: StateFlow<Boolean> = _isExchangeFromCache

    val calculatedBaseAmount: StateFlow<Double> = _calcExpression.map { expr ->
        CalculatorEngine.evaluate(expr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0)

    val convertedTargetAmount: StateFlow<Double> = combine(calculatedBaseAmount, _currentRate) { amount, rate ->
        amount * rate
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 31.90)

    // 預填記帳資料（用於從計算機一鍵帶入記帳）
    private val _prefilledTransaction = MutableStateFlow<TransactionEntity?>(null)
    val prefilledTransaction: StateFlow<TransactionEntity?> = _prefilledTransaction

    fun clearPrefilledTransaction() {
        _prefilledTransaction.value = null
    }

    init {
        val prefs = getApplication<Application>().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val savedLangCode = prefs.getString("selected_language_code", "zh-TW")
        _selectedLanguage.value = AppLanguage.fromCode(savedLangCode)

        val savedCurrencyCode = prefs.getString("selected_currency_code", "TWD") ?: "TWD"
        val matchedCurrency = AppCurrency.entries.find { it.code == savedCurrencyCode } ?: AppCurrency.TWD
        _selectedCurrency.value = matchedCurrency

        // 預設目標幣別依據使用者設定之記帳幣別
        _targetCurrency.value = SupportedCurrencies.findByCode(matchedCurrency.code)

        viewModelScope.launch {
            repository.checkAndSeedInitialData()
        }
        viewModelScope.launch {
            refreshExchangeRates(force = false)
        }
    }

    fun setLanguage(language: AppLanguage) {
        _selectedLanguage.value = language
        val prefs = getApplication<Application>().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_language_code", language.code).apply()
        MyMoneyKeepWidgetProvider.updateAllWidgets(getApplication())
    }

    fun setCurrency(currency: AppCurrency) {
        _selectedCurrency.value = currency
        val prefs = getApplication<Application>().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_currency_code", currency.code).apply()
        _targetCurrency.value = SupportedCurrencies.findByCode(currency.code)
        MyMoneyKeepWidgetProvider.updateAllWidgets(getApplication())
        viewModelScope.launch {
            updateCurrentRateAndHistory()
        }
    }

    fun setBaseCurrency(currencyInfo: CurrencyInfo) {
        if (_baseCurrency.value.code == currencyInfo.code) return
        _baseCurrency.value = currencyInfo
        viewModelScope.launch {
            refreshExchangeRates(force = false)
        }
    }

    fun setTargetCurrency(currencyInfo: CurrencyInfo) {
        if (_targetCurrency.value.code == currencyInfo.code) return
        _targetCurrency.value = currencyInfo
        viewModelScope.launch {
            updateCurrentRateAndHistory()
        }
    }

    fun swapCurrencies() {
        val oldBase = _baseCurrency.value
        val oldTarget = _targetCurrency.value
        _baseCurrency.value = oldTarget
        _targetCurrency.value = oldBase
        viewModelScope.launch {
            refreshExchangeRates(force = false)
        }
    }

    fun onCalcInput(key: String) {
        _calcExpression.value = CalculatorEngine.processInput(_calcExpression.value, key)
    }

    fun clearCalcInput() {
        _calcExpression.value = "0"
    }

    fun setExchangeTimeRange(range: ExchangeTimeRange) {
        _exchangeTimeRange.value = range
        viewModelScope.launch {
            updateHistoricalRatesOnly()
        }
    }

    fun refreshExchangeRates(force: Boolean = true) {
        viewModelScope.launch {
            _isExchangeLoading.value = true
            try {
                val baseCode = _baseCurrency.value.code
                val result = currencyRepository.getExchangeRates(baseCode, forceRefresh = force)
                _exchangeRates.value = result.rates
                _lastExchangeUpdate.value = result.lastUpdateFormatted
                _isExchangeFromCache.value = result.isFromCache

                val targetCode = _targetCurrency.value.code
                val rate = result.rates[targetCode] ?: 1.0
                _currentRate.value = rate

                // 抓取歷史走勢
                val history = currencyRepository.getHistoricalRates(
                    base = baseCode,
                    target = targetCode,
                    timeRange = _exchangeTimeRange.value,
                    currentLiveRate = rate
                )
                _historicalRates.value = history
            } catch (e: Exception) {
                android.util.Log.e("BookkeepingVM", "Error refreshing exchange rates: ${e.message}", e)
            } finally {
                _isExchangeLoading.value = false
            }
        }
    }

    private suspend fun updateCurrentRateAndHistory() {
        val targetCode = _targetCurrency.value.code
        val baseCode = _baseCurrency.value.code
        val rate = _exchangeRates.value[targetCode]
        if (rate != null) {
            _currentRate.value = rate
            val history = currencyRepository.getHistoricalRates(
                base = baseCode,
                target = targetCode,
                timeRange = _exchangeTimeRange.value,
                currentLiveRate = rate
            )
            _historicalRates.value = history
        } else {
            refreshExchangeRates(force = false)
        }
    }

    private suspend fun updateHistoricalRatesOnly() {
        try {
            val history = currencyRepository.getHistoricalRates(
                base = _baseCurrency.value.code,
                target = _targetCurrency.value.code,
                timeRange = _exchangeTimeRange.value,
                currentLiveRate = _currentRate.value
            )
            _historicalRates.value = history
        } catch (e: Exception) {
            android.util.Log.w("BookkeepingVM", "Error updating historical rates: ${e.message}")
        }
    }

    fun prefillTransactionFromExchange(targetAmount: Double, note: String) {
        val sdf = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
        val todayStr = sdf.format(java.util.Date())
        _prefilledTransaction.value = TransactionEntity(
            id = 0,
            date = todayStr,
            title = note,
            expense = targetAmount,
            income = null,
            category = "C"
        )
    }

    fun setStyleTheme(theme: AppStyleTheme) {
        _selectedStyleTheme.value = theme
    }

    fun getCategoryByCode(code: String?): CustomCategory {
        if (code.isNullOrBlank()) return CustomCategory.UNKNOWN
        return _customCategories.value.find { it.code.equals(code, ignoreCase = true) }
            ?: CustomCategory.UNKNOWN
    }

    fun addCategory(name: String, isIncome: Boolean, colorHex: String): Boolean {
        if (_customCategories.value.size >= 20) return false
        val newCode = "CAT_${System.currentTimeMillis().toString().takeLast(5)}"
        val newCat = CustomCategory(newCode, name, isIncome, colorHex)
        _customCategories.value = _customCategories.value + newCat
        return true
    }

    fun updateCategory(code: String, name: String, isIncome: Boolean, colorHex: String) {
        _customCategories.value = _customCategories.value.map { cat ->
            if (cat.code.equals(code, ignoreCase = true)) {
                cat.copy(name = name, isIncome = isIncome, colorHex = colorHex)
            } else cat
        }
    }

    fun deleteCategory(code: String) {
        _customCategories.value = _customCategories.value.filter { !it.code.equals(code, ignoreCase = true) }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setReportTimeRange(range: ReportTimeRange) {
        _selectedTimeRange.value = range
        val cal = java.util.Calendar.getInstance()
        val curYear = cal.get(java.util.Calendar.YEAR)
        val curMonth = cal.get(java.util.Calendar.MONTH) + 1
        val curQuarter = (curMonth - 1) / 3 + 1

        _selectedPeriodLabel.value = when (range) {
            ReportTimeRange.ALL -> "全部歷史紀錄"
            ReportTimeRange.WEEK -> "本週 (7 天內)"
            ReportTimeRange.MONTH -> String.format(java.util.Locale.TAIWAN, "%d/%02d", curYear, curMonth)
            ReportTimeRange.QUARTER -> "$curYear Q$curQuarter"
            ReportTimeRange.YEAR -> "$curYear 年"
        }
    }

    fun setReportPeriodLabel(period: String) {
        _selectedPeriodLabel.value = period
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
    }

    fun updateVoiceTranscript(text: String) {
        _voiceTranscript.value = text
    }

    fun updateChatInput(text: String) {
        _chatInput.value = text
    }

    private val _warningToastEvent = MutableStateFlow<String?>(null)
    val warningToastEvent: StateFlow<String?> = _warningToastEvent

    fun clearWarningToastEvent() {
        _warningToastEvent.value = null
    }

    fun processInput(text: String) {
        if (text.isBlank()) return
        _isProcessingAi.value = true

        // Append user chat message
        val userMsg = ChatMessage(sender = "USER", text = text)
        _chatMessages.value = _chatMessages.value + userMsg
        _chatInput.value = ""

        viewModelScope.launch {
            try {
                if (isFinancialQuestion(text)) {
                    // Route to AI Chat Agent
                    handleFinancialQuestion(text)
                } else {
                    // Route to Transaction Parser
                    val customKey = syncManager.accountState.value.geminiApiKey
                    val currentLang = _selectedLanguage.value
                    val result = parser.parseUserInput(text, customApiKey = customKey, language = currentLang)

                    if (result.isValid) {
                        // Add transaction to DB
                        repository.insertTransaction(
                            date = result.date,
                            title = result.title,
                            category = result.category,
                            income = result.income,
                            expense = result.expense
                        )
                        notifyWidgetUpdate()

                        val aiMsg = ChatMessage(
                            sender = "AI",
                            text = result.aiResponse,
                            parsedTransaction = result
                        )
                        _chatMessages.value = _chatMessages.value + aiMsg
                    } else {
                        // Invalid input: DO NOT add to DB! Pop up warning alert
                        val warningText = result.warningMessage ?: result.aiResponse
                        val aiMsg = ChatMessage(
                            sender = "AI",
                            text = warningText,
                            parsedTransaction = null
                        )
                        _chatMessages.value = _chatMessages.value + aiMsg
                        _warningToastEvent.value = warningText
                    }
                }
            } catch (e: Exception) {
                val warningText = com.example.data.network.ValidationStrings.getNoApiKeyWarning(_selectedLanguage.value)
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    sender = "AI",
                    text = warningText
                )
                _warningToastEvent.value = warningText
            } finally {
                _isProcessingAi.value = false
            }
        }
    }

    fun addManualTransaction(date: String, title: String, category: String, income: Double?, expense: Double?) {
        viewModelScope.launch {
            repository.insertTransaction(date, title, category, income, expense)
            notifyWidgetUpdate()
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
            notifyWidgetUpdate()
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            notifyWidgetUpdate()
        }
    }

    fun clearLastAdded() {
        _lastAddedTransaction.value = null
    }

    fun toggleGoogleLogin() {
        if (syncManager.accountState.value.isSignedIn) {
            _loginMode.value = LoginMode.GOOGLE_USER
        } else {
            _loginMode.value = LoginMode.GUEST
        }
    }

    fun selectGuestMode() {
        _loginMode.value = LoginMode.GUEST
    }

    fun loginWithGoogle(retainLocalData: Boolean) {
        viewModelScope.launch {
            if (!retainLocalData) {
                repository.clearAll()
            }
            notifyWidgetUpdate()
            _loginMode.value = LoginMode.GOOGLE_USER
        }
    }

    fun logoutGoogle() {
        syncManager.signOut()
        _loginMode.value = LoginMode.GUEST
    }

    fun autoCreateYearlySheetConfig() {
        val currentYear = java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        val autoFolder = "MyMoneyKeep_雲端記帳本"
        val autoTitle = "${currentYear}_MyMoneyKeep_記帳本"
        syncManager.updateSheetConfig(autoTitle, "", autoFolder)
    }

    suspend fun syncToGoogleDrive(): Boolean {
        return syncManager.syncToDrive(allTransactions.value, customCategories.value)
    }

    fun importCsv(csvContent: String) {
        viewModelScope.launch {
            val list = syncManager.parseCsvContent(csvContent)
            if (list.isNotEmpty()) {
                repository.replaceAll(list)
                notifyWidgetUpdate()
            }
        }
    }

    fun exportCsv(): String {
        return syncManager.generateCsvContent(allTransactions.value)
    }

    suspend fun restoreFromGoogleDrive(): Boolean {
        val list = syncManager.restoreFromDrive() ?: return false
        if (list.isNotEmpty()) {
            repository.replaceAll(list)
            notifyWidgetUpdate()
        }
        return true
    }

    // Filtered transaction list for query screen
    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        allTransactions, 
        searchQuery, 
        customCategories,
        sortField,
        sortDirection
    ) { list, query, cats, field, dir ->
        val filtered = if (query.isBlank()) {
            list
        } else {
            list.filter { item ->
                val catObj = cats.find { it.code.equals(item.category, ignoreCase = true) } ?: CustomCategory.UNKNOWN
                item.title.contains(query, ignoreCase = true) ||
                        item.date.contains(query) ||
                        item.category.contains(query, ignoreCase = true) ||
                        catObj.name.contains(query, ignoreCase = true)
            }
        }

        when (field) {
            SortField.DATE -> {
                if (dir == SortDirection.ASC) {
                    filtered.sortedWith(
                        compareBy(
                            { DateUtils.parseDateToComparable(it.date) },
                            { it.itemNo },
                            { it.id }
                        )
                    )
                } else {
                    filtered.sortedWith(
                        compareByDescending<TransactionEntity> { DateUtils.parseDateToComparable(it.date) }
                            .thenByDescending { it.itemNo }
                            .thenByDescending { it.id }
                    )
                }
            }
            SortField.TITLE -> {
                if (dir == SortDirection.ASC) filtered.sortedBy { it.title }
                else filtered.sortedByDescending { it.title }
            }
            SortField.AMOUNT -> {
                if (dir == SortDirection.ASC) filtered.sortedBy { (it.income ?: 0.0) + (it.expense ?: 0.0) }
                else filtered.sortedByDescending { (it.income ?: 0.0) + (it.expense ?: 0.0) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleSort(field: SortField) {
        if (_sortField.value == field) {
            _sortDirection.value = if (_sortDirection.value == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
        } else {
            _sortField.value = field
            _sortDirection.value = SortDirection.DESC // 預設降冪
        }
    }

    // Report Analysis Data Calculation
    val reportData: StateFlow<ReportAnalysisData> = combine(
        allTransactions,
        selectedTimeRange,
        selectedPeriodLabel,
        customCategories
    ) { list, timeRange, period, cats ->
        val filtered = list.filter { item ->
            val pd = parseDateString(item.date)
            when (timeRange) {
                ReportTimeRange.ALL -> true
                ReportTimeRange.WEEK -> {
                    if (pd == null) false
                    else {
                        val now = System.currentTimeMillis()
                        val cal = java.util.Calendar.getInstance()
                        cal.set(pd.year, pd.month - 1, pd.day)
                        val itemTime = cal.timeInMillis
                        val diffDays = (now - itemTime) / (1000 * 60 * 60 * 24)
                        when (period) {
                            "近 14 天" -> diffDays in 0..14
                            "近 30 天" -> diffDays in 0..30
                            else -> diffDays in 0..7
                        }
                    }
                }
                ReportTimeRange.MONTH -> {
                    if (pd == null) false
                    else {
                        val formatted1 = String.format(java.util.Locale.TAIWAN, "%d/%02d", pd.year, pd.month)
                        val formatted2 = "${pd.year}/${pd.month}"
                        period == formatted1 || period == formatted2
                    }
                }
                ReportTimeRange.QUARTER -> {
                    if (pd == null) false
                    else {
                        val q = (pd.month - 1) / 3 + 1
                        val expected = "${pd.year} Q$q"
                        period == expected
                    }
                }
                ReportTimeRange.YEAR -> {
                    if (pd == null) false
                    else {
                        val expected = "${pd.year} 年"
                        period == expected || period.contains(pd.year.toString())
                    }
                }
            }
        }

        var totalInc = 0.0
        var totalExp = 0.0

        val categoryMap = mutableMapOf<String, Double>()
        val categoryCountMap = mutableMapOf<String, Int>()

        filtered.forEach { item ->
            val inc = item.income ?: 0.0
            val exp = item.expense ?: 0.0
            totalInc += inc
            totalExp += exp

            val catObj = cats.find { it.code.equals(item.category, ignoreCase = true) } ?: CustomCategory.UNKNOWN
            val amt = if (catObj.isIncome || (item.income != null && item.income > 0)) inc else exp
            if (amt > 0) {
                val key = catObj.code
                categoryMap[key] = (categoryMap[key] ?: 0.0) + amt
                categoryCountMap[key] = (categoryCountMap[key] ?: 0) + 1
            }
        }

        val allCategoriesToReport = cats.toMutableList()
        if ((categoryMap[""] ?: 0.0) > 0) {
            allCategoriesToReport.add(CustomCategory.UNKNOWN)
        }

        val summaries = allCategoriesToReport.map { cat ->
            val code = cat.code
            val amt = categoryMap[code] ?: 0.0
            val count = categoryCountMap[code] ?: 0
            val denominator = if (cat.isIncome) totalInc.coerceAtLeast(1.0) else totalExp.coerceAtLeast(1.0)
            val percentage = if (amt > 0) ((amt / denominator) * 100).toFloat() else 0f

            CategorySummary(
                code = code,
                label = cat.name,
                totalAmount = amt,
                percentage = percentage,
                itemCount = count,
                isIncome = cat.isIncome,
                colorHex = cat.colorHex
            )
        }

        ReportAnalysisData(
            timeRange = timeRange,
            periodLabel = period,
            totalIncome = totalInc,
            totalExpense = totalExp,
            netBalance = totalInc - totalExp,
            categorySummaries = summaries
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ReportAnalysisData(ReportTimeRange.MONTH, "2025/12", 0.0, 0.0, 0.0, emptyList())
    )
}
