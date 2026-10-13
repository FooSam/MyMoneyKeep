package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.util.AdManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * AdMob 橫幅廣告 (Banner Ad) Composable
 *
 * 1. 採用標準 AdSize.BANNER (320x50dp)。
 * 2. 於測試環境 (Robolectric) 自動隔離，防止測試崩潰。
 * 3. 垂直留白保持最小化 (50dp 緊湊高度)，減少對主畫面的空間壓縮。
 */
@Composable
fun BannerAdView(
    modifier: Modifier = Modifier,
    adUnitId: String = AdManager.getBannerAdUnitId()
) {
    if (AdManager.isRunningInTestEnvironment()) {
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        this.adUnitId = adUnitId
                        loadAd(AdRequest.Builder().build())
                    }
                },
                onRelease = { view ->
                    view.destroy()
                }
            )
        }
    }
}
