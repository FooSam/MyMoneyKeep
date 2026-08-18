package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.ExchangeTimeRange
import com.example.data.model.HistoricalRatePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ExchangeRateResult(
    val baseCode: String,
    val rates: Map<String, Double>,
    val lastUpdateTimestamp: Long,
    val lastUpdateFormatted: String,
    val isFromCache: Boolean = false
)

class CurrencyRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("mymoneykeep_currency_cache", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun getExchangeRates(baseCode: String, forceRefresh: Boolean = false): ExchangeRateResult = withContext(Dispatchers.IO) {
        val uppercaseBase = baseCode.uppercase()

        // 1. 如果不是強制更新，且本地有 2 小時內的快取，可優先使用
        if (!forceRefresh) {
            val cachedResult = loadFromCache(uppercaseBase)
            if (cachedResult != null && (System.currentTimeMillis() - cachedResult.lastUpdateTimestamp < 2 * 3600 * 1000L)) {
                return@withContext cachedResult
            }
        }

        // 2. 聯網請求最新匯率 (open.er-api.com)
        try {
            val url = "https://open.er-api.com/v6/latest/$uppercaseBase"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MyMoneyKeep-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    if (json.optString("result") == "success") {
                        val ratesJson = json.optJSONObject("rates") ?: JSONObject()
                        val rateMap = mutableMapOf<String, Double>()
                        val keys = ratesJson.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            rateMap[k] = ratesJson.optDouble(k, 1.0)
                        }

                        val updateUnix = json.optLong("time_last_update_unix", System.currentTimeMillis() / 1000)
                        val timestampMs = updateUnix * 1000
                        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                        val formattedTime = sdf.format(Date(timestampMs))

                        // 寫入本機快取
                        saveToCache(uppercaseBase, rateMap, timestampMs, formattedTime)

                        return@withContext ExchangeRateResult(
                            baseCode = uppercaseBase,
                            rates = rateMap,
                            lastUpdateTimestamp = timestampMs,
                            lastUpdateFormatted = formattedTime,
                            isFromCache = false
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("CurrencyRepo", "Failed to fetch online exchange rates for $uppercaseBase: ${e.message}")
        }

        // 3. 若網路失敗，退回本機快取
        val fallback = loadFromCache(uppercaseBase)
        if (fallback != null) {
            return@withContext fallback
        }

        // 4. 若從未快取過，提供常用幣別基準值作為兜底保護
        val defaultRates = getDefaultFallbackRates(uppercaseBase)
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        ExchangeRateResult(
            baseCode = uppercaseBase,
            rates = defaultRates,
            lastUpdateTimestamp = now,
            lastUpdateFormatted = sdf.format(Date(now)),
            isFromCache = true
        )
    }

    suspend fun getHistoricalRates(
        base: String,
        target: String,
        timeRange: ExchangeTimeRange,
        currentLiveRate: Double
    ): List<HistoricalRatePoint> = withContext(Dispatchers.IO) {
        val uppercaseBase = base.uppercase()
        val uppercaseTarget = target.uppercase()

        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val endDateStr = sdf.format(cal.time)

        cal.add(Calendar.DAY_OF_YEAR, -timeRange.days)
        val startDateStr = sdf.format(cal.time)

        // 嘗試從 Frankfurter API 獲取真實歷史匯率
        try {
            val url = "https://api.frankfurter.dev/v1/$startDateStr..$endDateStr?base=$uppercaseBase&symbols=$uppercaseTarget"
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    val ratesObj = json.optJSONObject("rates")
                    if (ratesObj != null) {
                        val points = mutableListOf<HistoricalRatePoint>()
                        val sortedKeys = ratesObj.keys().asSequence().sorted().toList()
                        for (dateKey in sortedKeys) {
                            val innerObj = ratesObj.optJSONObject(dateKey)
                            if (innerObj != null && innerObj.has(uppercaseTarget)) {
                                val r = innerObj.optDouble(uppercaseTarget)
                                val displayDate = if (dateKey.length >= 10) dateKey.substring(5).replace("-", "/") else dateKey
                                points.add(HistoricalRatePoint(date = displayDate, rate = r))
                            }
                        }
                        if (points.isNotEmpty()) {
                            return@withContext points
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("CurrencyRepo", "Failed to fetch historical rates from Frankfurter: ${e.message}")
        }

        // 若 API 無法支援該貨幣對（例如非 ECB 列管貨幣）或離線，使用真實基準模擬趨勢曲線（避免畫面空白）
        return@withContext generateTrendPoints(timeRange, currentLiveRate)
    }

    private fun generateTrendPoints(timeRange: ExchangeTimeRange, baseRate: Double): List<HistoricalRatePoint> {
        val points = mutableListOf<HistoricalRatePoint>()
        val cal = Calendar.getInstance()
        val numPoints = when (timeRange) {
            ExchangeTimeRange.ONE_WEEK -> 7
            ExchangeTimeRange.ONE_MONTH -> 15
            ExchangeTimeRange.THREE_MONTHS -> 20
            ExchangeTimeRange.ONE_YEAR -> 24
        }
        val stepDays = timeRange.days / numPoints.coerceAtLeast(1)

        val sdf = SimpleDateFormat("MM/dd", Locale.US)
        val rand = java.util.Random(baseRate.toBits())

        for (i in (numPoints - 1) downTo 0) {
            val pointCal = Calendar.getInstance()
            pointCal.add(Calendar.DAY_OF_YEAR, -i * stepDays)
            val dateLabel = sdf.format(pointCal.time)
            val fluctuation = 1.0 + (rand.nextDouble() - 0.5) * 0.03 * (i.toDouble() / numPoints)
            val rate = if (i == 0) baseRate else baseRate * fluctuation
            points.add(HistoricalRatePoint(date = dateLabel, rate = rate))
        }
        return points
    }

    private fun saveToCache(baseCode: String, rates: Map<String, Double>, timestamp: Long, formattedTime: String) {
        try {
            val json = JSONObject()
            json.put("base", baseCode)
            json.put("timestamp", timestamp)
            json.put("formattedTime", formattedTime)
            val ratesObj = JSONObject()
            for ((k, v) in rates) {
                ratesObj.put(k, v)
            }
            json.put("rates", ratesObj)

            prefs.edit()
                .putString("rates_$baseCode", json.toString())
                .apply()
        } catch (e: Exception) {
            Log.w("CurrencyRepo", "Failed to save currency cache: ${e.message}")
        }
    }

    private fun loadFromCache(baseCode: String): ExchangeRateResult? {
        val str = prefs.getString("rates_$baseCode", null) ?: return null
        return try {
            val json = JSONObject(str)
            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
            val formatted = json.optString("formattedTime", "")
            val ratesObj = json.optJSONObject("rates") ?: JSONObject()
            val map = mutableMapOf<String, Double>()
            val keys = ratesObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = ratesObj.optDouble(k, 1.0)
            }
            ExchangeRateResult(
                baseCode = baseCode,
                rates = map,
                lastUpdateTimestamp = timestamp,
                lastUpdateFormatted = formatted,
                isFromCache = true
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun getDefaultFallbackRates(baseCode: String): Map<String, Double> {
        // 提供 USD 與 TWD 為基準的基本對照
        return when (baseCode) {
            "USD" -> mapOf(
                "USD" to 1.0, "TWD" to 31.90, "JPY" to 159.20, "EUR" to 0.86,
                "CNY" to 6.75, "KRW" to 1414.0, "HKD" to 7.84, "GBP" to 0.74,
                "SGD" to 1.28, "AUD" to 1.41, "CAD" to 1.38, "THB" to 33.0, "VND" to 26100.0
            )
            "TWD" -> mapOf(
                "TWD" to 1.0, "USD" to 0.0313, "JPY" to 4.99, "EUR" to 0.027,
                "CNY" to 0.212, "KRW" to 44.3, "HKD" to 0.246, "GBP" to 0.023,
                "SGD" to 0.040, "AUD" to 0.044, "CAD" to 0.043, "THB" to 1.03, "VND" to 820.0
            )
            else -> mapOf(baseCode to 1.0, "TWD" to 31.90, "USD" to 1.0)
        }
    }
}
