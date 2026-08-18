package com.example.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

    fun getLocale(languageCode: String): Locale {
        return when (languageCode) {
            "zh-TW", "zh-HK", "zh-MO" -> Locale.TRADITIONAL_CHINESE
            "zh-CN", "zh-SG" -> Locale.SIMPLIFIED_CHINESE
            "ja" -> Locale.JAPANESE
            "ko" -> Locale.KOREAN
            "en" -> Locale.ENGLISH
            else -> Locale.forLanguageTag(languageCode)
        }
    }

    fun applyLocale(context: Context, languageCode: String): Context {
        val locale = getLocale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
                localeManager?.applicationLocales = LocaleList(locale)
            } catch (_: Exception) {}
        }

        return context.createConfigurationContext(config)
    }
}
