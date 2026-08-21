package com.example.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Google AdMob 插頁式廣告 (Interstitial Ad) 統一管理類別
 *
 * 具備以下核心能力：
 * 1. 自動區分 Debug (測試廣告單元 ID) 與 Release (正式廣告單元 ID)。
 * 2. 自動預載機制 (Preload Strategy)，確保使用者觸發時廣告秒開。
 * 3. 完善的 Fallback 容錯機制：若廣告尚未載入或展示失敗，立即觸發回呼，不阻擋使用者操作流程。
 * 4. 支援 Robolectric 單元測試環境安全隔離，防止測試崩潰。
 */
object AdManager {

    private const val TAG = "AdManager"

    // 正式插頁式廣告單元 ID
    const val PROD_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5014630903713895/3584134709"

    // Google 官方測試插頁式廣告單元 ID
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    // 支援測試或特定情境覆寫
    var overrideAdUnitId: String? = null

    @Volatile
    private var interstitialAd: InterstitialAd? = null

    @Volatile
    private var isLoadingAd: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 取得當前適用的插頁式廣告單元 ID
     */
    fun getInterstitialAdUnitId(): String {
        overrideAdUnitId?.let { return it }
        return if (BuildConfig.DEBUG) {
            TEST_INTERSTITIAL_AD_UNIT_ID
        } else {
            PROD_INTERSTITIAL_AD_UNIT_ID
        }
    }

    /**
     * 判斷是否處於 Robolectric 或單元測試環境
     */
    fun isRunningInTestEnvironment(): Boolean {
        return Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
    }

    /**
     * 初始化 AdMob SDK 並預先載入第一檔插頁廣告
     */
    fun init(context: Context) {
        if (isRunningInTestEnvironment()) {
            Log.d(TAG, "Running in test environment, skipping AdMob initialization.")
            return
        }

        try {
            MobileAds.initialize(context) { initializationStatus ->
                Log.d(TAG, "AdMob MobileAds initialized successfully: $initializationStatus")
                // 初始化完成後，立即背景預載插頁式廣告
                loadInterstitialAd(context.applicationContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MobileAds", e)
        }
    }

    /**
     * 預先載入插頁式廣告
     */
    fun loadInterstitialAd(
        context: Context,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null
    ) {
        if (isRunningInTestEnvironment()) {
            onLoaded?.invoke()
            return
        }

        if (interstitialAd != null) {
            Log.d(TAG, "Interstitial ad is already loaded and ready.")
            onLoaded?.invoke()
            return
        }

        if (isLoadingAd) {
            Log.d(TAG, "Interstitial ad is currently loading, skipping duplicate request.")
            return
        }

        isLoadingAd = true
        val adUnitId = getInterstitialAdUnitId()
        val adRequest = AdRequest.Builder().build()

        mainHandler.post {
            try {
                InterstitialAd.load(
                    context,
                    adUnitId,
                    adRequest,
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            Log.d(TAG, "Interstitial ad loaded successfully (AdUnit: $adUnitId)")
                            interstitialAd = ad
                            isLoadingAd = false
                            onLoaded?.invoke()
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            val errorMsg = "Interstitial ad failed to load: ${loadAdError.message} (code: ${loadAdError.code})"
                            Log.w(TAG, errorMsg)
                            interstitialAd = null
                            isLoadingAd = false
                            onFailed?.invoke(errorMsg)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during InterstitialAd.load", e)
                isLoadingAd = false
                onFailed?.invoke(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * 檢查廣告是否已準備就緒
     */
    fun isAdReady(): Boolean {
        return interstitialAd != null
    }

    /**
     * 展示插頁式廣告
     *
     * @param activity 當前前景 Activity
     * @param onAdDismissed 當廣告播放完畢關閉、或廣告未準備好/展示失敗時觸發的回呼
     */
    fun showInterstitialAd(activity: Activity, onAdDismissed: () -> Unit) {
        // 安全包裝回呼，確保在主線程執行且只觸發一次
        var hasInvokedDismiss = false
        val safeDismiss = {
            if (!hasInvokedDismiss) {
                hasInvokedDismiss = true
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    onAdDismissed()
                } else {
                    mainHandler.post { onAdDismissed() }
                }
            }
        }

        if (isRunningInTestEnvironment()) {
            Log.d(TAG, "Test environment: directly invoking onAdDismissed.")
            safeDismiss()
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            Log.d(TAG, "No interstitial ad ready to show. Invoking fallback callback.")
            safeDismiss()
            // 嘗試預載供下次使用
            loadInterstitialAd(activity.applicationContext)
            return
        }

        // 取出廣告後立即將暫存設為 null，因為一檔廣告物件只能 show 一次
        interstitialAd = null

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial ad showed full screen content.")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial ad dismissed by user.")
                safeDismiss()
                // 廣告關閉後，立即背景預載下一檔廣告
                loadInterstitialAd(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Interstitial ad failed to show: ${adError.message} (code: ${adError.code})")
                safeDismiss()
                // 展示失敗亦在背景重新預載
                loadInterstitialAd(activity.applicationContext)
            }
        }

        mainHandler.post {
            try {
                ad.show(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Exception while showing interstitial ad", e)
                safeDismiss()
                loadInterstitialAd(activity.applicationContext)
            }
        }
    }

    /**
     * 重設廣告狀態（主要供單元測試使用）
     */
    fun resetForTesting() {
        interstitialAd = null
        isLoadingAd = false
        overrideAdUnitId = null
    }
}
