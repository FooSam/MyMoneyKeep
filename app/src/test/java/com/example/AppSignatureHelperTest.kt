package com.example

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.util.AppSignatureHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppSignatureHelperTest {

    @Test
    fun testComputeSha1Formatting() {
        // 測試純字串 "test" 的 SHA-1
        // SHA-1("test") = a94a8fe5ccb19ba61c4c0873d391e987982fbbd3
        val input = "test".toByteArray(Charsets.UTF_8)
        val sha1Result = AppSignatureHelper.computeSha1(input)
        assertEquals("A9:4A:8F:E5:CC:B1:9B:A6:1C:4C:08:73:D3:91:E9:87:98:2F:BB:D3", sha1Result)
    }

    @Test
    fun testComputeSha256Formatting() {
        // 測試純字串 "test" 的 SHA-256
        // SHA-256("test") = 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
        val input = "test".toByteArray(Charsets.UTF_8)
        val sha256Result = AppSignatureHelper.computeSha256(input)
        assertEquals("9F:86:D0:81:88:4C:7D:65:9A:2F:EA:A0:C5:5A:D0:15:A3:BF:4F:1B:2B:0B:82:2C:D1:5D:6C:15:B0:F0:0A:08", sha256Result)
    }

    @Test
    fun testGetAppSignatureSHA1RunsWithoutCrash() {
        val context = ApplicationProvider.getApplicationContext<MyApplication>()
        val result = AppSignatureHelper.getAppSignatureSHA1(context)
        assertNotNull(result)
        assertTrue(result.isNotBlank())
    }
}
