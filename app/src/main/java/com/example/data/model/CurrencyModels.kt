package com.example.data.model

import com.example.ui.viewmodel.AppLanguage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ExchangeRateResponse(
    @field:Json(name = "result") val result: String? = null,
    @field:Json(name = "base_code") val baseCode: String? = null,
    @field:Json(name = "time_last_update_unix") val timeLastUpdateUnix: Long? = null,
    @field:Json(name = "time_last_update_utc") val timeLastUpdateUtc: String? = null,
    @field:Json(name = "rates") val rates: Map<String, Double>? = null
)

@JsonClass(generateAdapter = true)
data class HistoricalRatesResponse(
    @field:Json(name = "amount") val amount: Double? = null,
    @field:Json(name = "base") val base: String? = null,
    @field:Json(name = "start_date") val startDate: String? = null,
    @field:Json(name = "end_date") val endDate: String? = null,
    @field:Json(name = "rates") val rates: Map<String, Map<String, Double>>? = null
)

data class HistoricalRatePoint(
    val date: String,
    val rate: Double
)

enum class ExchangeTimeRange(val days: Int, val labelResKey: String) {
    ONE_WEEK(7, "exchange_time_range_1w"),
    ONE_MONTH(30, "exchange_time_range_1m"),
    THREE_MONTHS(90, "exchange_time_range_3m"),
    ONE_YEAR(365, "exchange_time_range_1y")
}

data class CurrencyInfo(
    val code: String,
    val flagEmoji: String,
    val symbol: String,
    val nameZhTW: String,
    val nameZhCN: String,
    val nameEn: String,
    val nameJa: String,
    val nameKo: String,
    val isPopular: Boolean = false,
    val decimalPlaces: Int = 2
) {
    fun getDisplayName(language: AppLanguage): String {
        return when (language) {
            AppLanguage.TRADITIONAL_CHINESE -> nameZhTW
            AppLanguage.SIMPLIFIED_CHINESE -> nameZhCN
            AppLanguage.ENGLISH -> nameEn
            AppLanguage.JAPANESE -> nameJa
            AppLanguage.KOREAN -> nameKo
        }
    }
}

object SupportedCurrencies {
    val list: List<CurrencyInfo> = listOf(
        // Popular / Major
        CurrencyInfo("TWD", "🇹🇼", "NT$", "新台幣 (TWD)", "新台币 (TWD)", "New Taiwan Dollar (TWD)", "新台湾ドル (TWD)", "대만 달러 (TWD)", isPopular = true, decimalPlaces = 0),
        CurrencyInfo("USD", "🇺🇸", "$", "美元 (USD)", "美元 (USD)", "US Dollar (USD)", "米ドル (USD)", "미국 달러 (USD)", isPopular = true, decimalPlaces = 2),
        CurrencyInfo("JPY", "🇯🇵", "¥", "日圓 (JPY)", "日元 (JPY)", "Japanese Yen (JPY)", "日本円 (JPY)", "일본 엔 (JPY)", isPopular = true, decimalPlaces = 0),
        CurrencyInfo("EUR", "🇪🇺", "€", "歐元 (EUR)", "欧元 (EUR)", "Euro (EUR)", "ユーロ (EUR)", "유로 (EUR)", isPopular = true, decimalPlaces = 2),
        CurrencyInfo("CNY", "🇨🇳", "¥", "人民幣 (CNY)", "人民币 (CNY)", "Chinese Yuan (CNY)", "中国人民元 (CNY)", "중국 위안 (CNY)", isPopular = true, decimalPlaces = 2),
        CurrencyInfo("KRW", "🇰🇷", "₩", "韓元 (KRW)", "韩元 (KRW)", "South Korean Won (KRW)", "韓国ウォン (KRW)", "대한민국 원 (KRW)", isPopular = true, decimalPlaces = 0),
        CurrencyInfo("HKD", "🇭🇰", "HK$", "港幣 (HKD)", "港币 (HKD)", "Hong Kong Dollar (HKD)", "香港ドル (HKD)", "홍콩 달러 (HKD)", isPopular = true, decimalPlaces = 2),
        CurrencyInfo("GBP", "🇬🇧", "£", "英鎊 (GBP)", "英镑 (GBP)", "British Pound (GBP)", "英ポンド (GBP)", "영국 파운드 (GBP)", isPopular = true, decimalPlaces = 2),
        CurrencyInfo("SGD", "🇸🇬", "S$", "新加坡幣 (SGD)", "新加坡元 (SGD)", "Singapore Dollar (SGD)", "シンガポールドル (SGD)", "싱가포르 달러 (SGD)", isPopular = true, decimalPlaces = 2),
        CurrencyInfo("AUD", "🇦🇺", "A$", "澳幣 (AUD)", "澳元 (AUD)", "Australian Dollar (AUD)", "豪ドル (AUD)", "호주 달러 (AUD)", isPopular = true, decimalPlaces = 2),
        CurrencyInfo("CAD", "🇨🇦", "C$", "加拿大幣 (CAD)", "加拿大元 (CAD)", "Canadian Dollar (CAD)", "カナダドル (CAD)", "캐나다 달러 (CAD)", isPopular = true, decimalPlaces = 2),
        CurrencyInfo("THB", "🇹🇭", "฿", "泰銖 (THB)", "泰铢 (THB)", "Thai Baht (THB)", "タイバーツ (THB)", "태국 바트 (THB)", isPopular = true, decimalPlaces = 2),
        CurrencyInfo("VND", "🇻🇳", "₫", "越南盾 (VND)", "越南盾 (VND)", "Vietnamese Dong (VND)", "ベトナムドン (VND)", "베트남 동 (VND)", isPopular = true, decimalPlaces = 0),
        CurrencyInfo("MYR", "🇲🇾", "RM", "馬來西亞令吉 (MYR)", "马来西亚令吉 (MYR)", "Malaysian Ringgit (MYR)", "マレーシアリンギット (MYR)", "말레이시아 링깃 (MYR)", isPopular = true, decimalPlaces = 2),
        CurrencyInfo("PHP", "🇵🇭", "₱", "菲律賓披索 (PHP)", "菲律宾比索 (PHP)", "Philippine Peso (PHP)", "フィリピンペソ (PHP)", "필리핀 페소 (PHP)", isPopular = true, decimalPlaces = 2),
        CurrencyInfo("NZD", "🇳🇿", "NZ$", "紐西蘭幣 (NZD)", "新西兰元 (NZD)", "New Zealand Dollar (NZD)", "NZドル (NZD)", "뉴질랜드 달러 (NZD)", isPopular = true, decimalPlaces = 2),
        CurrencyInfo("CHF", "🇨🇭", "CHF", "瑞士法郎 (CHF)", "瑞士法郎 (CHF)", "Swiss Franc (CHF)", "スイスフラン (CHF)", "스위스 프랑 (CHF)", isPopular = true, decimalPlaces = 2),

        // Others
        CurrencyInfo("AED", "🇦🇪", "AED", "阿聯酋迪拉姆 (AED)", "阿联酋迪拉姆 (AED)", "UAE Dirham (AED)", "UAEディルハム (AED)", "아랍에미리트 디르함 (AED)"),
        CurrencyInfo("BRL", "🇧🇷", "R$", "巴西雷亞爾 (BRL)", "巴西雷亚尔 (BRL)", "Brazilian Real (BRL)", "ブラジルレアル (BRL)", "브라질 헤알 (BRL)"),
        CurrencyInfo("CZK", "🇨🇿", "Kč", "捷克克朗 (CZK)", "捷克克朗 (CZK)", "Czech Koruna (CZK)", "チェココルナ (CZK)", "체코 코루나 (CZK)"),
        CurrencyInfo("DKK", "🇩🇰", "kr", "丹麥克朗 (DKK)", "丹麦克朗 (DKK)", "Danish Krone (DKK)", "デンマーククローネ (DKK)", "덴마크 크로네 (DKK)"),
        CurrencyInfo("IDR", "🇮🇩", "Rp", "印尼盾 (IDR)", "印尼盾 (IDR)", "Indonesian Rupiah (IDR)", "インドネシアルピア (IDR)", "인도네시아 루피아 (IDR)", decimalPlaces = 0),
        CurrencyInfo("ILS", "🇮🇱", "₪", "以色列新謝克爾 (ILS)", "以色列新谢克尔 (ILS)", "Israeli New Shekel (ILS)", "イスラエルシェケル (ILS)", "이스라엘 셰켈 (ILS)"),
        CurrencyInfo("INR", "🇮🇳", "₹", "印度盧比 (INR)", "印度卢比 (INR)", "Indian Rupee (INR)", "インドルピー (INR)", "인도 루피 (INR)"),
        CurrencyInfo("MXN", "🇲🇽", "Mex$", "墨西哥披索 (MXN)", "墨西哥比索 (MXN)", "Mexican Peso (MXN)", "メキシコペソ (MXN)", "멕시코 페소 (MXN)"),
        CurrencyInfo("NOK", "🇳🇴", "kr", "挪威克朗 (NOK)", "挪威克朗 (NOK)", "Norwegian Krone (NOK)", "ノルウェークローネ (NOK)", "노르웨이 크로네 (NOK)"),
        CurrencyInfo("PLN", "🇵🇱", "zł", "波蘭茲羅提 (PLN)", "波兰兹罗提 (PLN)", "Polish Zloty (PLN)", "ポーランドズロチ (PLN)", "폴란드 즈워티 (PLN)"),
        CurrencyInfo("SAR", "🇸🇦", "SAR", "沙烏地里亞爾 (SAR)", "沙特里亚尔 (SAR)", "Saudi Riyal (SAR)", "サウジリヤル (SAR)", "사우디 리얄 (SAR)"),
        CurrencyInfo("SEK", "🇸🇪", "kr", "瑞典克朗 (SEK)", "瑞典克朗 (SEK)", "Swedish Krona (SEK)", "スウェーデンクローナ (SEK)", "스웨덴 크로나 (SEK)"),
        CurrencyInfo("TRY", "🇹🇷", "₺", "土耳其里拉 (TRY)", "土耳其里拉 (TRY)", "Turkish Lira (TRY)", "トルコリラ (TRY)", "튀르키예 리라 (TRY)"),
        CurrencyInfo("ZAR", "🇿🇦", "R", "南非蘭特 (ZAR)", "南非兰特 (ZAR)", "South African Rand (ZAR)", "南アフリカランド (ZAR)", "남아프리카 랜드 (ZAR)")
    )

    fun findByCode(code: String): CurrencyInfo {
        return list.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: CurrencyInfo(
                code = code.uppercase(),
                flagEmoji = "🌐",
                symbol = code.uppercase(),
                nameZhTW = "$code 貨幣",
                nameZhCN = "$code 货币",
                nameEn = "$code Currency",
                nameJa = "$code 通貨",
                nameKo = "$code 통화"
            )
    }
}
