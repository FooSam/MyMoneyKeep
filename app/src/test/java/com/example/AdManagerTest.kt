package com.example

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.util.AdManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AdManagerTest {

    @Before
    fun setUp() {
        AdManager.resetForTesting()
    }

    @After
    fun tearDown() {
        AdManager.resetForTesting()
    }

    @Test
    fun testAdUnitIdConfiguration() {
        // 預設 (在測試環境/Debug 下應為測試 ID)
        val defaultId = AdManager.getInterstitialAdUnitId()
        assertEquals(AdManager.TEST_INTERSTITIAL_AD_UNIT_ID, defaultId)

        // 測試覆寫 ID
        AdManager.overrideAdUnitId = "custom_test_ad_unit_123"
        assertEquals("custom_test_ad_unit_123", AdManager.getInterstitialAdUnitId())
    }

    @Test
    fun testIsRunningInTestEnvironment() {
        assertTrue("Should detect Robolectric test environment", AdManager.isRunningInTestEnvironment())
    }

    @Test
    fun testShowInterstitialAdInvokesCallbackInTestEnvironment() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        var dismissedCalled = false

        AdManager.showInterstitialAd(activity) {
            dismissedCalled = true
        }

        assertTrue("onAdDismissed should be invoked immediately in test environment", dismissedCalled)
    }

    @Test
    fun testLoadInterstitialAdInvokesLoadedInTestEnvironment() {
        val app = ApplicationProvider.getApplicationContext<MyApplication>()
        var loadedCalled = false

        AdManager.loadInterstitialAd(
            context = app,
            onLoaded = { loadedCalled = true },
            onFailed = { }
        )

        assertTrue("onLoaded should be invoked in test environment", loadedCalled)
    }
}
