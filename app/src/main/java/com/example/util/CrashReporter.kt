package com.example.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Firebase Crashlytics 遙測與錯誤回報封裝工具。
 * 統一處理崩潰日誌、非致命例外 (Non-Fatal Exception) 及自訂除錯鍵值。
 */
object CrashReporter {

    private const val TAG = "CrashReporter"

    private val crashlytics: FirebaseCrashlytics?
        get() = try {
            FirebaseCrashlytics.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseCrashlytics is not initialized: ${e.message}")
            null
        }

    /**
     * 記錄操作軌跡或麵包屑日誌 (Breadcrumb)
     */
    fun log(message: String) {
        Log.d(TAG, message)
        try {
            crashlytics?.log(message)
        } catch (_: Exception) {}
    }

    /**
     * 設定使用者識別碼
     */
    fun setUserId(userId: String) {
        try {
            crashlytics?.setUserId(userId)
        } catch (_: Exception) {}
    }

    /**
     * 設定自訂字串鍵值
     */
    fun setCustomKey(key: String, value: String) {
        try {
            crashlytics?.setCustomKey(key, value)
        } catch (_: Exception) {}
    }

    /**
     * 設定自訂整數鍵值
     */
    fun setCustomKey(key: String, value: Int) {
        try {
            crashlytics?.setCustomKey(key, value)
        } catch (_: Exception) {}
    }

    /**
     * 設定自訂布林鍵值
     */
    fun setCustomKey(key: String, value: Boolean) {
        try {
            crashlytics?.setCustomKey(key, value)
        } catch (_: Exception) {}
    }

    /**
     * 記錄非致命例外 (Non-Fatal Exception)
     * 可附帶額外自訂鍵值與日誌
     */
    fun recordException(
        throwable: Throwable,
        tag: String? = null,
        customKeys: Map<String, String>? = null
    ) {
        val tagPrefix = if (tag != null) "[$tag] " else ""
        Log.e(TAG, "$tagPrefix${throwable.message}", throwable)

        try {
            crashlytics?.let { fc ->
                tag?.let { fc.setCustomKey("error_tag", it) }
                customKeys?.forEach { (k, v) ->
                    fc.setCustomKey(k, v)
                }
                fc.recordException(throwable)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record exception to Crashlytics: ${e.message}")
        }
    }
}
