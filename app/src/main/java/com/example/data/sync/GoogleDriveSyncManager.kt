package com.example.data.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.model.CustomCategory
import com.example.data.model.TransactionEntity
import com.example.util.DateUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.*
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
    val sheetTitle: String = "",
    val sheetId: String = "",
    val lastSyncTime: String = "尚未同步",
    val isSyncing: Boolean = false,
    val geminiApiKey: String = "",
    val lastSyncError: String = ""  // 最近一次同步的詳細錯誤訊息，空字串代表無錯誤
)

class GoogleDriveSyncManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("mymoneykeep_sync_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "MyMoneyKeepSync"

        fun getDefaultSheetTitle(): String {
            val currentYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
            return "${currentYear}_MyMoneyKeep_記帳本"
        }

        fun hexToSheetsColor(hex: String, defaultColor: Color = Color().setRed(0.15f).setGreen(0.15f).setBlue(0.15f)): Color {
            return try {
                val cleanHex = hex.removePrefix("#")
                val colorInt = cleanHex.toLong(16)
                val r: Float
                val g: Float
                val b: Float
                if (cleanHex.length == 8) {
                    r = ((colorInt shr 16) and 0xFF) / 255.0f
                    g = ((colorInt shr 8) and 0xFF) / 255.0f
                    b = (colorInt and 0xFF) / 255.0f
                } else if (cleanHex.length == 6) {
                    r = ((colorInt shr 16) and 0xFF) / 255.0f
                    g = ((colorInt shr 8) and 0xFF) / 255.0f
                    b = (colorInt and 0xFF) / 255.0f
                } else {
                    return defaultColor
                }
                Color().setRed(r).setGreen(g).setBlue(b)
            } catch (e: Exception) {
                defaultColor
            }
        }

        fun formatApiException(e: Throwable, stepDescription: String): String {
            val sb = StringBuilder()
            sb.append("【發生步驟】：$stepDescription\n")
            if (e is GoogleJsonResponseException) {
                val code = e.statusCode
                val details = e.details
                val mainMsg = details?.message ?: e.message ?: "未知 Google API 伺服器錯誤"
                val errorItem = details?.errors?.firstOrNull()
                val reason = errorItem?.reason ?: ""
                val domain = errorItem?.domain ?: ""

                sb.append("【HTTP 狀態碼】：$code\n")
                if (reason.isNotBlank()) sb.append("【錯誤原因代碼】：$reason (領域: $domain)\n")
                sb.append("【Google 伺服器訊息】：\n$mainMsg\n")

                if (code == 403) {
                    if (mainMsg.contains("Sheets API", ignoreCase = true) || reason.contains("accessNotConfigured", ignoreCase = true)) {
                        sb.append("\n💡【解決指引】：\n您的 Google Cloud 專案尚未啟用『Google Sheets API』。\n請登入 Google Cloud Console，進入【API 和服務】>【已啟用的 API 和服務】，點擊【+ 啟用 API 和服務】，搜尋『Google Sheets API』並點擊『啟用 (Enable)』。")
                    } else if (mainMsg.contains("Drive API", ignoreCase = true)) {
                        sb.append("\n💡【解決指引】：\n您的 Google Cloud 專案尚未啟用『Google Drive API』。\n請至 Google Cloud Console 搜尋『Google Drive API』並點擊『啟用 (Enable)』。")
                    } else {
                        sb.append("\n💡【解決指引】：\nGoogle 存取權限不足 (403)。請確認您的 Google 帳號在登入授權時已勾選所有雲端硬碟與試算表存取權限。")
                    }
                }
            } else if (e is UserRecoverableAuthIOException) {
                sb.append("【授權失效】：Google OAuth 授權已過期或被撤銷，請點擊『登出帳號』後重新登入授權。")
            } else {
                sb.append("【例外類型】：${e.javaClass.simpleName}\n")
                sb.append("【錯誤訊息】：${e.message ?: "無詳細訊息"}")
            }
            return sb.toString()
        }
    }

    private val _accountState = MutableStateFlow(
        GoogleAccountState(
            driveFolder = prefs.getString("drive_folder", "MyMoneyKeep_雲端記帳本") ?: "MyMoneyKeep_雲端記帳本",
            sheetTitle = (prefs.getString("sheet_title", getDefaultSheetTitle()) ?: getDefaultSheetTitle()).removeSuffix(".csv").removeSuffix(".xlsx"),
            sheetId = prefs.getString("sheet_id", "") ?: "",
            lastSyncTime = prefs.getString("last_sync_time", "尚未同步") ?: "尚未同步",
            geminiApiKey = prefs.getString("gemini_api_key", "") ?: ""
        )
    )
    val accountState: StateFlow<GoogleAccountState> = _accountState

    // 建立 Google Sign-In 用戶端，要求完整 Drive 與 Sheets 權限
    val signInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(DriveScopes.DRIVE),            // 完整 Drive 存取（搜尋、建立、移動、刪除）
                Scope(SheetsScopes.SPREADSHEETS)     // 完整 Sheets 存取（讀寫格式化）
            )
            .build()

        GoogleSignIn.getClient(context, gso)
    }

    init {
        // 啟動時檢查是否已取得完整 Drive scope
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE))) {
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
        val cleanTitle = title.removeSuffix(".csv").removeSuffix(".xlsx").ifBlank { getDefaultSheetTitle() }
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
    // 真實 Google Sheets API v4 兩階段標準排版美化套版同步邏輯
    // ---------------------------------------------------------

    suspend fun syncToDrive(
        transactions: List<TransactionEntity>,
        customCategories: List<CustomCategory> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        var currentStep = "步驟 0：Google 憑證與權限驗證"
        try {
            // 每次同步開始前先清除上一次的錯誤訊息
            _accountState.value = _accountState.value.copy(isSyncing = true, lastSyncError = "")

            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account == null || account.account == null) {
                val errMsg = "【發生步驟】：步驟 0：Google 憑證驗證\n【原因】：本機 Google 帳號憑證不存在或已失效。\n💡【建議解決方式】：請點擊『登入帳號』重新取得授權。"
                Log.e(TAG, "syncToDrive failed: $errMsg")
                _accountState.value = _accountState.value.copy(lastSyncError = errMsg)
                return@withContext false
            }

            // 驗證是否已取得完整 Drive scope（避免舊 token 只有 DRIVE_FILE）
            if (!GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE))) {
                val errMsg = "【發生步驟】：步驟 0：Google 權限範圍檢查\n【原因】：尚未取得完整 Drive 存取權限 (DriveScopes.DRIVE)。\n💡【建議解決方式】：請點擊『登出帳號』後重新登入，並務必在 Google 授權畫面中勾選所有權限項目。"
                Log.e(TAG, "syncToDrive failed: $errMsg")
                _accountState.value = _accountState.value.copy(lastSyncError = errMsg)
                return@withContext false
            }

            // 建立憑證
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(DriveScopes.DRIVE, SheetsScopes.SPREADSHEETS)
            )
            credential.selectedAccount = account.account!!

            val transport = NetHttpTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()

            val driveService = Drive.Builder(transport, jsonFactory, credential)
                .setApplicationName("MyMoneyKeep")
                .build()

            val sheetsService = Sheets.Builder(transport, jsonFactory, credential)
                .setApplicationName("MyMoneyKeep")
                .build()

            // 1. 尋找或建立專屬雲端資料夾 (預設: MyMoneyKeep_雲端記帳本)
            currentStep = "步驟 1：雲端專屬資料夾檢查與建立"
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

            // 2. 檢查專屬資料夾內是否已有相同名稱的 Google 試算表檔案
            currentStep = "步驟 2：試算表檔案定位與初始化"
            val fileName = _accountState.value.sheetTitle.removeSuffix(".csv").removeSuffix(".xlsx").ifBlank { getDefaultSheetTitle() }
            val fileQuery = "name = '$fileName' and '$folderId' in parents and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
            val fileList = driveService.files().list()
                .setQ(fileQuery)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            var targetFileId = fileList.files?.firstOrNull()?.id

            if (targetFileId == null) {
                // 建立全新試算表：優先使用 Sheets API，若遇權限問題則降級使用 Drive API 建立
                try {
                    val newSpreadsheet = Spreadsheet().apply {
                        properties = SpreadsheetProperties().apply {
                            title = fileName
                        }
                    }
                    val created = sheetsService.spreadsheets().create(newSpreadsheet)
                        .setFields("spreadsheetId")
                        .execute()
                    targetFileId = created.spreadsheetId

                    // 將新建的試算表移動至專屬資料夾中
                    try {
                        driveService.files().update(targetFileId, null)
                            .setAddParents(folderId)
                            .setFields("id, parents")
                            .execute()
                    } catch (moveEx: Exception) {
                        Log.w(TAG, "Failed to move spreadsheet to folder: ${moveEx.message}")
                    }
                } catch (sheetsCreateEx: Exception) {
                    Log.w(TAG, "Sheets API create failed, fallback to Drive API create", sheetsCreateEx)
                    val spreadsheetMetadata = File().apply {
                        name = fileName
                        mimeType = "application/vnd.google-apps.spreadsheet"
                        parents = listOf(folderId)
                    }
                    val createdFile = driveService.files().create(spreadsheetMetadata)
                        .setFields("id")
                        .execute()
                    targetFileId = createdFile.id
                }
            }

            val sortedTransactions = transactions.sortedWith(
                compareBy(
                    { DateUtils.parseDateToComparable(it.date) },
                    { it.itemNo },
                    { it.id }
                )
            )

            // 雙層同步機制：優先使用 Google Sheets API 原生兩階段處理
            currentStep = "步驟 3：試算表數據寫入與原生排版套版"
            var sheetsUpdateSuccess = false
            var sheetsErrorMsg = ""
            try {
                val spreadsheet = sheetsService.spreadsheets().get(targetFileId).execute()
                val sheetObj = spreadsheet.sheets?.firstOrNull()
                val targetSheetId = sheetObj?.properties?.sheetId ?: 0
                val targetSheetTitle = sheetObj?.properties?.title ?: "Sheet1"

                // ==========================================
                // 第 1 階段：寫入所有儲存格數據 (values.update)
                // ==========================================
                val valuesList = mutableListOf<List<Any>>()

                // 第 1 列：主標題橫幅
                valuesList.add(listOf("# MyMoneyKeep 雲端記帳本", "", "", "", "", "", ""))

                // 第 2 列：欄位標頭
                valuesList.add(listOf("項目", "日期", "標題", "類別", "收入", "支出", "小計"))

                // 第 3 列起：資料列 (依日期順序累加計算小計)
                var runningSubtotal = 0.0
                sortedTransactions.forEachIndexed { index, t ->
                    val inc = t.income ?: 0.0
                    val exp = t.expense ?: 0.0
                    runningSubtotal += (inc - exp)
                    valuesList.add(
                        listOf(
                            index + 1,
                            t.date,
                            t.title,
                            t.category,
                            t.income?.takeIf { it > 0 } ?: "",
                            t.expense?.takeIf { it > 0 } ?: "",
                            runningSubtotal
                        )
                    )
                }

                // 先清理舊資料範圍
                try {
                    sheetsService.spreadsheets().values().clear(
                        targetFileId,
                        "'$targetSheetTitle'!A1:Z5000",
                        ClearValuesRequest()
                    ).execute()
                } catch (ignored: Exception) {}

                // 寫入儲存格數據
                val valueBody = ValueRange().setValues(valuesList)
                sheetsService.spreadsheets().values().update(targetFileId, "'$targetSheetTitle'!A1", valueBody)
                    .setValueInputOption("USER_ENTERED")
                    .execute()

                // ==========================================
                // 第 2 階段：套用精確排版格式 (batchUpdate)
                // ==========================================
                val requests = mutableListOf<Request>()

                // 建立類別顏色對照表
                val effectiveCategories = customCategories.ifEmpty { CustomCategory.DEFAULT_CATEGORIES }
                val categoryColorMap = mutableMapOf<String, Color>()
                effectiveCategories.forEach { cat ->
                    val sheetsColor = hexToSheetsColor(cat.colorHex)
                    categoryColorMap[cat.code.uppercase()] = sheetsColor
                    categoryColorMap[cat.name.uppercase()] = sheetsColor
                }
                val defaultTitleColor = Color().setRed(0.15f).setGreen(0.15f).setBlue(0.15f)

                // (1) 合併第 1 列 A1:G1
                requests.add(
                    Request().setMergeCells(
                        MergeCellsRequest().apply {
                            range = GridRange().apply {
                                sheetId = targetSheetId
                                startRowIndex = 0
                                endRowIndex = 1
                                startColumnIndex = 0
                                endColumnIndex = 7
                            }
                            mergeType = "MERGE_ALL"
                        }
                    )
                )

                // (2) 第 1 列橫幅格式 (置中、粗體 12pt)
                requests.add(
                    Request().setRepeatCell(
                        RepeatCellRequest().apply {
                            range = GridRange().apply {
                                sheetId = targetSheetId
                                startRowIndex = 0
                                endRowIndex = 1
                                startColumnIndex = 0
                                endColumnIndex = 7
                            }
                            cell = CellData().apply {
                                userEnteredFormat = CellFormat().apply {
                                    horizontalAlignment = "CENTER"
                                    verticalAlignment = "MIDDLE"
                                    textFormat = TextFormat().apply {
                                        bold = true
                                        fontSize = 12
                                    }
                                }
                            }
                            fields = "userEnteredFormat(horizontalAlignment,verticalAlignment,textFormat)"
                        }
                    )
                )

                // (3) 第 2 列表頭格式 (置中、粗體 11pt)
                requests.add(
                    Request().setRepeatCell(
                        RepeatCellRequest().apply {
                            range = GridRange().apply {
                                sheetId = targetSheetId
                                startRowIndex = 1
                                endRowIndex = 2
                                startColumnIndex = 0
                                endColumnIndex = 7
                            }
                            cell = CellData().apply {
                                userEnteredFormat = CellFormat().apply {
                                    horizontalAlignment = "CENTER"
                                    verticalAlignment = "MIDDLE"
                                    textFormat = TextFormat().apply {
                                        bold = true
                                        fontSize = 11
                                    }
                                }
                            }
                            fields = "userEnteredFormat(horizontalAlignment,verticalAlignment,textFormat)"
                        }
                    )
                )

                if (sortedTransactions.isNotEmpty()) {
                    val totalDataRows = sortedTransactions.size

                    // (4) A, B 欄 (項目、日期) 水平垂直置中
                    requests.add(
                        Request().setRepeatCell(
                            RepeatCellRequest().apply {
                                range = GridRange().apply {
                                    sheetId = targetSheetId
                                    startRowIndex = 2
                                    endRowIndex = 2 + totalDataRows
                                    startColumnIndex = 0
                                    endColumnIndex = 2
                                }
                                cell = CellData().apply {
                                    userEnteredFormat = CellFormat().apply {
                                        horizontalAlignment = "CENTER"
                                        verticalAlignment = "MIDDLE"
                                    }
                                }
                                fields = "userEnteredFormat(horizontalAlignment,verticalAlignment)"
                            }
                        )
                    )

                    // (5) D 欄 (類別) 水平垂直置中
                    requests.add(
                        Request().setRepeatCell(
                            RepeatCellRequest().apply {
                                range = GridRange().apply {
                                    sheetId = targetSheetId
                                    startRowIndex = 2
                                    endRowIndex = 2 + totalDataRows
                                    startColumnIndex = 3
                                    endColumnIndex = 4
                                }
                                cell = CellData().apply {
                                    userEnteredFormat = CellFormat().apply {
                                        horizontalAlignment = "CENTER"
                                        verticalAlignment = "MIDDLE"
                                    }
                                }
                                fields = "userEnteredFormat(horizontalAlignment,verticalAlignment)"
                            }
                        )
                    )

                    // (6) C 欄 (標題) 水平垂直置中，並依自訂類別顏色動態著色
                    sortedTransactions.forEachIndexed { idx, t ->
                        val rowIndex = 2 + idx
                        val catKey = t.category.trim().uppercase()
                        val titleColor = categoryColorMap[catKey] ?: defaultTitleColor

                        requests.add(
                            Request().setRepeatCell(
                                RepeatCellRequest().apply {
                                    range = GridRange().apply {
                                        sheetId = targetSheetId
                                        startRowIndex = rowIndex
                                        endRowIndex = rowIndex + 1
                                        startColumnIndex = 2
                                        endColumnIndex = 3
                                    }
                                    cell = CellData().apply {
                                        userEnteredFormat = CellFormat().apply {
                                            horizontalAlignment = "CENTER"
                                            verticalAlignment = "MIDDLE"
                                            textFormat = TextFormat().apply {
                                                bold = true
                                                foregroundColor = titleColor
                                            }
                                        }
                                    }
                                    fields = "userEnteredFormat(horizontalAlignment,verticalAlignment,textFormat)"
                                }
                            )
                        )
                    }

                    // (7) E, F, G 欄 (收入、支出、小計) 靠右對齊，並套用千分位數字格式
                    requests.add(
                        Request().setRepeatCell(
                            RepeatCellRequest().apply {
                                range = GridRange().apply {
                                    sheetId = targetSheetId
                                    startRowIndex = 2
                                    endRowIndex = 2 + totalDataRows
                                    startColumnIndex = 4
                                    endColumnIndex = 7
                                }
                                cell = CellData().apply {
                                    userEnteredFormat = CellFormat().apply {
                                        horizontalAlignment = "RIGHT"
                                        verticalAlignment = "MIDDLE"
                                        numberFormat = NumberFormat().setType("NUMBER").setPattern("#,##0")
                                    }
                                }
                                fields = "userEnteredFormat(horizontalAlignment,verticalAlignment,numberFormat)"
                            }
                        )
                    )
                }

                // (8) 設定欄寬 (完全對齊附圖 2 寬度比例)
                val columnWidths = listOf(60, 110, 180, 80, 100, 100, 110)
                columnWidths.forEachIndexed { colIdx, width ->
                    requests.add(
                        Request().setUpdateDimensionProperties(
                            UpdateDimensionPropertiesRequest().apply {
                                range = DimensionRange().apply {
                                    sheetId = targetSheetId
                                    dimension = "COLUMNS"
                                    startIndex = colIdx
                                    endIndex = colIdx + 1
                                }
                                properties = DimensionProperties().apply {
                                    pixelSize = width
                                }
                                fields = "pixelSize"
                            }
                        )
                    )
                }

                // (9) 表格全區間淡灰邊框
                val borderStyle = Border().apply {
                    style = "SOLID"
                    color = Color().setRed(0.85f).setGreen(0.87f).setBlue(0.90f)
                }
                val totalRowCount = maxOf(2, 2 + sortedTransactions.size)
                requests.add(
                    Request().setUpdateBorders(
                        UpdateBordersRequest().apply {
                            range = GridRange().apply {
                                sheetId = targetSheetId
                                startRowIndex = 0
                                endRowIndex = totalRowCount
                                startColumnIndex = 0
                                endColumnIndex = 7
                            }
                            top = borderStyle
                            bottom = borderStyle
                            left = borderStyle
                            right = borderStyle
                            innerHorizontal = borderStyle
                            innerVertical = borderStyle
                        }
                    )
                )

                // 執行格式化批次處理
                sheetsService.spreadsheets().batchUpdate(
                    targetFileId,
                    BatchUpdateSpreadsheetRequest().setRequests(requests)
                ).execute()

                sheetsUpdateSuccess = true
            } catch (sheetsEx: Exception) {
                sheetsErrorMsg = formatApiException(sheetsEx, currentStep)
                Log.e(TAG, "Sheets API execution failed: $sheetsErrorMsg, activating Level-2 fallback to Drive CSV streaming", sheetsEx)
            }

            // 第二層備援：若 Sheets API 異常，自動透過 Drive API 上傳 CSV 數據串流覆寫，保證資料永不為空
            if (!sheetsUpdateSuccess) {
                val csvContent = generateCsvContent(transactions)
                val fileContent = ByteArrayContent.fromString("text/csv; charset=UTF-8", csvContent)
                driveService.files().update(targetFileId, null, fileContent)
                    .setFields("id")
                    .execute()
            }

            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
            val baseTime = sdf.format(Date())
            val syncTime = if (sheetsUpdateSuccess) {
                "$baseTime (已套用美化排版)"
            } else {
                "$baseTime (純文字備援)"
            }
            prefs.edit()
                .putString("last_sync_time", syncTime)
                .putString("sheet_id", targetFileId)
                .apply()

            _accountState.value = _accountState.value.copy(
                lastSyncTime = syncTime,
                sheetId = targetFileId,
                // 若退回備援，將詳細 sheetsErrorMsg 記錄至 lastSyncError
                lastSyncError = if (sheetsUpdateSuccess) "" else sheetsErrorMsg
            )
            return@withContext true
        } catch (e: Exception) {
            val errMsg = formatApiException(e, currentStep)
            Log.e(TAG, "syncToDrive critical error: $errMsg", e)
            _accountState.value = _accountState.value.copy(lastSyncError = errMsg)
            return@withContext false
        } finally {
            _accountState.value = _accountState.value.copy(isSyncing = false)
        }
    }

    // ---------------------------------------------------------
    // 從 Google Drive 原生試算表下載資料並還原至本機 (無損雲端格式)
    // ---------------------------------------------------------

    suspend fun restoreFromDrive(): List<TransactionEntity>? = withContext(Dispatchers.IO) {
        try {
            _accountState.value = _accountState.value.copy(isSyncing = true)
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(DriveScopes.DRIVE, SheetsScopes.SPREADSHEETS)
            )
            credential.selectedAccount = account.account

            val transport = NetHttpTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()

            val driveService = Drive.Builder(transport, jsonFactory, credential)
                .setApplicationName("MyMoneyKeep")
                .build()

            val sheetsService = Sheets.Builder(transport, jsonFactory, credential)
                .setApplicationName("MyMoneyKeep")
                .build()

            val folderName = _accountState.value.driveFolder.ifBlank { "MyMoneyKeep_雲端記帳本" }
            val folderQuery = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val folderList = driveService.files().list()
                .setQ(folderQuery)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val folderId = folderList.files?.firstOrNull()?.id ?: return@withContext null

            val fileName = _accountState.value.sheetTitle.removeSuffix(".csv").removeSuffix(".xlsx").ifBlank { getDefaultSheetTitle() }
            val fileQuery = "name = '$fileName' and '$folderId' in parents and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
            val fileList = driveService.files().list()
                .setQ(fileQuery)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val existingFileId = fileList.files?.firstOrNull()?.id ?: return@withContext null

            // 優先透過 Sheets API 讀取儲存格 Values (A3:G)
            return@withContext try {
                val spreadsheet = sheetsService.spreadsheets().get(existingFileId).execute()
                val targetSheetTitle = spreadsheet.sheets?.firstOrNull()?.properties?.title ?: "Sheet1"
                val response = sheetsService.spreadsheets().values().get(existingFileId, "'$targetSheetTitle'!A3:G").execute()
                val rows = response.getValues()
                if (rows != null && rows.isNotEmpty()) {
                    val rawList = mutableListOf<TransactionEntity>()
                    for (row in rows) {
                        if (row.size >= 3) {
                            val date = row.getOrNull(1)?.toString()?.trim() ?: ""
                            val title = row.getOrNull(2)?.toString()?.trim() ?: ""
                            val category = row.getOrNull(3)?.toString()?.trim() ?: "C"
                            val incomeClean = row.getOrNull(4)?.toString()?.replace("$", "")?.replace("NT", "")?.replace(",", "")?.toDoubleOrNull()
                            val expenseClean = row.getOrNull(5)?.toString()?.replace("$", "")?.replace("NT", "")?.replace(",", "")?.toDoubleOrNull()

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
                    val sortedList = rawList.sortedWith(
                        compareBy(
                            { DateUtils.parseDateToComparable(it.date) },
                            { it.itemNo },
                            { it.id }
                        )
                    )
                    var currentSubtotal = 0.0
                    sortedList.mapIndexed { index, t ->
                        if (t.income != null) currentSubtotal += t.income
                        if (t.expense != null) currentSubtotal -= t.expense
                        t.copy(itemNo = index + 1, subtotal = currentSubtotal)
                    }
                } else {
                    // 若 Sheets 讀取為空，備退使用 Drive export CSV
                    val outputStream = ByteArrayOutputStream()
                    driveService.files().export(existingFileId, "text/csv")
                        .executeMediaAndDownloadTo(outputStream)
                    val csvData = outputStream.toString("UTF-8")
                    parseCsvContent(csvData)
                }
            } catch (sheetsEx: Exception) {
                // 備退方案：若 Sheets API 異常，使用 Drive text/csv 導出下載 (純讀取，不破壞格式)
                val outputStream = ByteArrayOutputStream()
                driveService.files().export(existingFileId, "text/csv")
                    .executeMediaAndDownloadTo(outputStream)
                val csvData = outputStream.toString("UTF-8")
                parseCsvContent(csvData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "restoreFromDrive error", e)
            return@withContext null
        } finally {
            _accountState.value = _accountState.value.copy(isSyncing = false)
        }
    }

    // ---------------------------------------------------------
    // CSV 本機備份生成與解析邏輯 (相容 Google 試算表格式)
    // ---------------------------------------------------------

    fun generateCsvContent(transactions: List<TransactionEntity>): String {
        val currentYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
        val sb = java.lang.StringBuilder()
        sb.append("# MyMoneyKeep 雲端記帳本 ($currentYear 年度)\n")
        sb.append("項目,日期,標題,類別,收入,支出,小計\n")
        val sortedTransactions = transactions.sortedWith(
            compareBy(
                { DateUtils.parseDateToComparable(it.date) },
                { it.itemNo },
                { it.id }
            )
        )
        var runningSubtotal = 0.0
        sortedTransactions.forEachIndexed { index, t ->
            val inc = t.income ?: 0.0
            val exp = t.expense ?: 0.0
            runningSubtotal += (inc - exp)
            val itemNum = index + 1
            val incomeStr = t.income?.takeIf { it > 0 }?.run { if (this % 1.0 == 0.0) this.toLong().toString() else this.toString() } ?: ""
            val expenseStr = t.expense?.takeIf { it > 0 }?.run { if (this % 1.0 == 0.0) this.toLong().toString() else this.toString() } ?: ""
            val subtotalStr = if (runningSubtotal % 1.0 == 0.0) runningSubtotal.toLong().toString() else runningSubtotal.toString()
            sb.append("$itemNum,${t.date},${t.title},${t.category},$incomeStr,$expenseStr,$subtotalStr\n")
        }
        return sb.toString()
    }

    fun parseCsvContent(csvContent: String): List<TransactionEntity> {
        val rawList = mutableListOf<TransactionEntity>()
        val lines = csvContent.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("項目") || trimmed.startsWith("Item") || trimmed.contains("MyMoneyKeep")) {
                continue
            }
            val parts = trimmed.split(",").map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            if (parts.size >= 3) {
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

        val sortedList = rawList.sortedWith(
            compareBy(
                { DateUtils.parseDateToComparable(it.date) },
                { it.itemNo },
                { it.id }
            )
        )
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
