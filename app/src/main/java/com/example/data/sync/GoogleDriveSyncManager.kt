package com.example.data.sync

import android.content.Context
import android.content.Intent
import com.example.BuildConfig
import com.example.data.model.TransactionEntity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GoogleAccountState(
    val isSignedIn: Boolean = false,
    val email: String = "",
    val displayName: String = "",
    val driveFolder: String = "MyMoneyKeep_雲端記帳本",
    val sheetTitle: String = "MyMoneyKeep_記帳本",
    val sheetId: String = "",
    val lastSyncTime: String = "尚未同步",
    val isSyncing: Boolean = false,
    val geminiApiKey: String = ""
)

class GoogleDriveSyncManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("mymoneykeep_sync_prefs", Context.MODE_PRIVATE)

    private val _accountState = MutableStateFlow(
        GoogleAccountState(
            driveFolder = prefs.getString("drive_folder", "MyMoneyKeep_雲端記帳本") ?: "MyMoneyKeep_雲端記帳本",
            sheetTitle = (prefs.getString("sheet_title", "MyMoneyKeep_記帳本") ?: "MyMoneyKeep_記帳本").removeSuffix(".csv"),
            sheetId = prefs.getString("sheet_id", "") ?: "",
            lastSyncTime = prefs.getString("last_sync_time", "尚未同步") ?: "尚未同步",
            geminiApiKey = prefs.getString("gemini_api_key", "") ?: ""
        )
    )
    val accountState: StateFlow<GoogleAccountState> = _accountState

    // 建立 Google Sign-In 用戶端，並要求 Drive File 的寫入權限 (純 Android 原生 OAuth 流程)
    val signInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        GoogleSignIn.getClient(context, gso)
    }

    init {
        // 啟動時檢查是否已經登入過
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            handleSignInResult(account)
        }
    }

    fun getSignInIntent(): Intent {
        return signInClient.signInIntent
    }

    fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            _accountState.value = _accountState.value.copy(
                isSignedIn = true,
                email = account.email ?: "",
                displayName = account.displayName ?: "Google 用戶"
            )
        } else {
            signOut()
        }
    }

    fun signOut() {
        signInClient.signOut().addOnCompleteListener {
            _accountState.value = _accountState.value.copy(
                isSignedIn = false,
                email = "",
                displayName = ""
            )
        }
    }

    fun updateSheetConfig(title: String, sheetId: String, folder: String = "MyMoneyKeep_雲端記帳本") {
        val cleanTitle = title.removeSuffix(".csv").ifBlank { "MyMoneyKeep_記帳本" }
        prefs.edit()
            .putString("drive_folder", folder)
            .putString("sheet_title", cleanTitle)
            .putString("sheet_id", sheetId)
            .apply()

        _accountState.value = _accountState.value.copy(
            driveFolder = folder,
            sheetTitle = cleanTitle,
            sheetId = sheetId
        )
    }

    fun updateGeminiApiKey(apiKey: String) {
        prefs.edit()
            .putString("gemini_api_key", apiKey.trim())
            .apply()

        _accountState.value = _accountState.value.copy(
            geminiApiKey = apiKey.trim()
        )
    }

    // ---------------------------------------------------------
    // 真實 Google Drive API 原生 Google 試算表同步邏輯
    // ---------------------------------------------------------

    suspend fun syncToDrive(transactions: List<TransactionEntity>): Boolean = withContext(Dispatchers.IO) {
        try {
            _accountState.value = _accountState.value.copy(isSyncing = true)
            
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext false
            
            // 建立憑證
            val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
            credential.selectedAccount = account.account
            
            // 建立 Drive 服務
            val driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("MyMoneyKeep").build()

            // 1. 尋找或建立專屬雲端資料夾 (預設: MyMoneyKeep_雲端記帳本)
            val folderName = _accountState.value.driveFolder.ifBlank { "MyMoneyKeep_雲端記帳本" }
            val folderQuery = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val folderList = driveService.files().list()
                .setQ(folderQuery)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val folderId = folderList.files?.firstOrNull()?.id ?: run {
                val folderMetadata = File().apply {
                    name = folderName
                    mimeType = "application/vnd.google-apps.folder"
                }
                driveService.files().create(folderMetadata)
                    .setFields("id")
                    .execute()
                    .id
            }
            
            val csvContent = generateCsvContent(transactions)
            val fileContent = ByteArrayContent.fromString("text/csv; charset=UTF-8", csvContent)
            
            // 2. 檢查專屬資料夾內是否已有相同名稱的 Google 試算表檔案
            val fileName = _accountState.value.sheetTitle.removeSuffix(".csv").ifBlank { "MyMoneyKeep_記帳本" }
            val fileQuery = "name = '$fileName' and '$folderId' in parents and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
            val fileList = driveService.files().list()
                .setQ(fileQuery)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
                
            val existingFileId = fileList.files?.firstOrNull()?.id
            
            val fileMetadata = File().apply {
                name = fileName
                mimeType = "application/vnd.google-apps.spreadsheet"
                if (existingFileId == null) {
                    parents = listOf(folderId)
                }
            }
            
            val result = if (existingFileId != null) {
                // 更新現有 Google 試算表內容
                driveService.files().update(existingFileId, null, fileContent)
                    .setFields("id")
                    .execute()
            } else {
                // 建立新 Google 試算表於專屬資料夾中 (Drive API 自動將 CSV 內容轉換為 Google 試算表)
                driveService.files().create(fileMetadata, fileContent)
                    .setFields("id")
                    .execute()
            }
                
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
            val syncTime = sdf.format(Date())
            prefs.edit()
                .putString("last_sync_time", syncTime)
                .putString("sheet_id", result.id)
                .apply()

            _accountState.value = _accountState.value.copy(
                lastSyncTime = syncTime,
                sheetId = result.id
            )
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        } finally {
            _accountState.value = _accountState.value.copy(isSyncing = false)
        }
    }

    // ---------------------------------------------------------
    // 從 Google Drive 原生試算表匯出下載並還原至本機
    // ---------------------------------------------------------

    suspend fun restoreFromDrive(): List<TransactionEntity>? = withContext(Dispatchers.IO) {
        try {
            _accountState.value = _accountState.value.copy(isSyncing = true)
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
            val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
            credential.selectedAccount = account.account

            val driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("MyMoneyKeep").build()

            val folderName = _accountState.value.driveFolder.ifBlank { "MyMoneyKeep_雲端記帳本" }
            val folderQuery = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val folderList = driveService.files().list()
                .setQ(folderQuery)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val folderId = folderList.files?.firstOrNull()?.id ?: return@withContext null

            val fileName = _accountState.value.sheetTitle.removeSuffix(".csv").ifBlank { "MyMoneyKeep_記帳本" }
            val fileQuery = "name = '$fileName' and '$folderId' in parents and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
            val fileList = driveService.files().list()
                .setQ(fileQuery)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val existingFileId = fileList.files?.firstOrNull()?.id ?: return@withContext null

            // 從 Google 試算表以 text/csv 格式匯出下載
            val outputStream = ByteArrayOutputStream()
            driveService.files().export(existingFileId, "text/csv")
                .executeMediaAndDownloadTo(outputStream)

            val csvData = outputStream.toString("UTF-8")
            return@withContext parseCsvContent(csvData)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            _accountState.value = _accountState.value.copy(isSyncing = false)
        }
    }

    // ---------------------------------------------------------
    // CSV 生成與解析邏輯
    // ---------------------------------------------------------

    fun generateCsvContent(transactions: List<TransactionEntity>): String {
        val sb = java.lang.StringBuilder()
        sb.append("# MyMoneyKeep 雲端記帳本 CSV 匯出 (專屬目錄: MyMoneyKeep_雲端記帳本)\n")
        sb.append("項目,日期,標題,類別,收入,支出,小計\n")
        transactions.sortedBy { it.date }.forEachIndexed { index, t ->
            val itemNum = index + 1
            val incomeStr = t.income?.takeIf { it > 0 }?.run { if (this % 1.0 == 0.0) this.toLong().toString() else this.toString() } ?: ""
            val expenseStr = t.expense?.takeIf { it > 0 }?.run { if (this % 1.0 == 0.0) this.toLong().toString() else this.toString() } ?: ""
            val subtotalStr = if (t.subtotal % 1.0 == 0.0) t.subtotal.toLong().toString() else t.subtotal.toString()
            sb.append("$itemNum,${t.date},${t.title},${t.category},$incomeStr,$expenseStr,$subtotalStr\n")
        }
        return sb.toString()
    }

    fun parseCsvContent(csvContent: String): List<TransactionEntity> {
        val rawList = mutableListOf<TransactionEntity>()
        val lines = csvContent.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("項目") || trimmed.startsWith("Item")) {
                continue
            }
            val parts = trimmed.split(",").map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            if (parts.size >= 4) {
                val date = parts.getOrNull(1)?.trim() ?: ""
                val title = parts.getOrNull(2)?.trim() ?: ""
                val category = parts.getOrNull(3)?.trim() ?: "C"
                val incomeClean = parts.getOrNull(4)?.replace("$", "")?.replace("NT", "")?.replace(",", "")?.toDoubleOrNull()
                val expenseClean = parts.getOrNull(5)?.replace("$", "")?.replace("NT", "")?.replace(",", "")?.toDoubleOrNull()

                if (date.isNotBlank() && title.isNotBlank()) {
                    rawList.add(
                        TransactionEntity(
                            itemNo = 0,
                            date = date,
                            title = title,
                            category = category,
                            income = incomeClean,
                            expense = expenseClean,
                            subtotal = 0.0,
                            isSynced = true
                        )
                    )
                }
            }
        }

        val sortedList = rawList.sortedBy { it.date }
        var currentSubtotal = 0.0
        return sortedList.mapIndexed { index, t ->
            if (t.income != null) currentSubtotal += t.income
            if (t.expense != null) currentSubtotal -= t.expense
            t.copy(
                itemNo = index + 1,
                subtotal = currentSubtotal
            )
        }
    }
}
