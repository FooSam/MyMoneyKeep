package com.example.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

/**
 * 應用程式簽署憑證指紋 (SHA-1 / SHA-256) 實時提取工具。
 * 用於在執行期動態獲取手機端 APK 的真實簽章，以供與 Google Cloud Console (GCP) OAuth 設定比對。
 */
object AppSignatureHelper {

    /**
     * 獲取當前執行中 APK 的 SHA-1 簽署憑證指紋（冒號分隔格式）
     */
    fun getAppSignatureSHA1(context: Context): String {
        return try {
            val signatures = getSignatures(context)
            if (signatures.isEmpty()) {
                return "未找到簽署憑證"
            }
            val certBytes = signatures[0]
            computeSha1(certBytes)
        } catch (e: Exception) {
            "獲取簽章失敗: ${e.message}"
        }
    }

    /**
     * 獲取當前執行中 APK 的 SHA-256 簽署憑證指紋（冒號分隔格式）
     */
    fun getAppSignatureSHA256(context: Context): String {
        return try {
            val signatures = getSignatures(context)
            if (signatures.isEmpty()) {
                return "未找到簽署憑證"
            }
            val certBytes = signatures[0]
            computeSha256(certBytes)
        } catch (e: Exception) {
            "獲取簽章失敗: ${e.message}"
        }
    }

    /**
     * 計算 ByteArray 之 SHA-1 雜湊並轉為標準大寫冒號格式
     */
    fun computeSha1(certBytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(certBytes)
        return formatFingerprint(digest)
    }

    /**
     * 計算 ByteArray 之 SHA-256 雜湊並轉為標準大寫冒號格式
     */
    fun computeSha256(certBytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(certBytes)
        return formatFingerprint(digest)
    }

    private fun formatFingerprint(digest: ByteArray): String {
        return digest.joinToString(":") { String.format(Locale.US, "%02X", it) }
    }

    @Suppress("DEPRECATION")
    private fun getSignatures(context: Context): List<ByteArray> {
        val packageManager = context.packageManager
        val packageName = context.packageName

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val packageInfo: PackageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signingInfo = packageInfo.signingInfo ?: return emptyList()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners.map { it.toByteArray() }
            } else {
                signingInfo.signingCertificateHistory.map { it.toByteArray() }
            }
        } else {
            val packageInfo: PackageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES
            )
            packageInfo.signatures?.map { it.toByteArray() } ?: emptyList()
        }
    }
}
