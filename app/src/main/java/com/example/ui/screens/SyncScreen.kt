package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CustomCategory
import com.example.ui.viewmodel.AppCurrency
import com.example.ui.viewmodel.AppLanguage
import com.example.ui.viewmodel.AppStyleTheme
import com.example.ui.viewmodel.BookkeepingViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(viewModel: BookkeepingViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val accountState by viewModel.googleAccountState.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    val selectedStyleTheme by viewModel.selectedStyleTheme.collectAsState()
    val customCategories by viewModel.customCategories.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    var driveFolder by remember { mutableStateOf(accountState.driveFolder) }
    var sheetTitle by remember { mutableStateOf(accountState.sheetTitle) }
    var sheetId by remember { mutableStateOf(accountState.sheetId) }
    var customApiKeyInput by remember { mutableStateOf(accountState.geminiApiKey) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var csvImportText by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var showRetainDataDialog by remember { mutableStateOf(false) }
    var showArchitectureSolutionDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showSyncErrorDialog by remember { mutableStateOf(false) }
    var syncErrorDetail by remember { mutableStateOf("") }
    var showDiagDialog by remember { mutableStateOf(false) }
    var diagLogContent by remember { mutableStateOf("") }

    var langDropdownExpanded by remember { mutableStateOf(false) }
    var currDropdownExpanded by remember { mutableStateOf(false) }
    var styleDropdownExpanded by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val signInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            viewModel.syncManager.handleSignInResult(account)
            if (account != null) {
                viewModel.loginWithGoogle(retainLocalData = true)
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            viewModel.syncManager.handleSignInResult(null)
            val runtimeSha1 = com.example.util.AppSignatureHelper.getAppSignatureSHA1(context)
            val statusCode = e.statusCode
            val statusMsg = e.status.statusMessage ?: "(無訊息)"
            val causeMsg = e.cause?.message ?: e.cause?.javaClass?.simpleName ?: "(無 cause)"
            val detailMsg = "【Google 登入診斷報告】\n" +
                "● 當前運行 APK 簽章 SHA-1:\n$runtimeSha1\n\n" +
                "● 錯誤代碼: $statusCode (${if (statusCode == 10) "DEVELOPER_ERROR" else "Error"})\n" +
                "● 錯誤訊息: $statusMsg\n" +
                "● Cause: $causeMsg\n" +
                "● 應用程式套件名: ${context.packageName}\n\n" +
                "【排查三大要點】：\n" +
                "1. GCP 憑證之 Android Client SHA-1 是否為上述字串？\n" +
                "2. GCP OAuth 同意畫面「範圍」是否已包含 Drive 與 Sheets？\n" +
                "3. 登入 Google 帳號是否已加入 GCP「測試使用者」清單？"

            android.util.Log.e("MMK_SignIn", detailMsg, e)

            // 記錄至 Firebase Crashlytics
            com.example.util.CrashReporter.recordException(
                throwable = e,
                tag = "SyncScreen_GoogleSignIn",
                customKeys = mapOf(
                    "runtime_sha1" to runtimeSha1,
                    "status_code" to statusCode.toString(),
                    "status_message" to statusMsg,
                    "package_name" to context.packageName
                )
            )

            // 寫入本機診斷檔案供查看
            try {
                val diagFile = java.io.File(context.filesDir, "sign_in_error.txt")
                val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                diagFile.writeText("時間: $ts\n$detailMsg\n\n完整 Exception 堆疊:\n${e.stackTraceToString()}")
            } catch (_: Exception) {}

            diagLogContent = detailMsg
            showDiagDialog = true
        }
    }

    // Category Management Dialog States
    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CustomCategory?>(null) }
    var categoryToDelete by remember { mutableStateOf<CustomCategory?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "帳號與系統設定",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Two Main Tabs: [一般設定], [帳號設定]
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("一般設定", fontWeight = FontWeight.Bold) },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "General Settings") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("帳號設定", fontWeight = FontWeight.Bold) },
                    icon = { Icon(imageVector = Icons.Default.ManageAccounts, contentDescription = "Account Settings") }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
            ) {
                if (selectedTabIndex == 0) {
                    // ==========================================
                    // 頁籤 1: 【一般設定】
                    // ==========================================

                    // Preferences Card (Theme, Language, Currency)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "偏好設定 (語系、幣別與顯示風格)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )

                                // Theme Style Selection
                                ExposedDropdownMenuBox(
                                    expanded = styleDropdownExpanded,
                                    onExpandedChange = { styleDropdownExpanded = !styleDropdownExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = selectedStyleTheme.displayName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("顯示模版樣式 (Theme Style)") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleDropdownExpanded) },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth()
                                    )

                                    ExposedDropdownMenu(
                                        expanded = styleDropdownExpanded,
                                        onDismissRequest = { styleDropdownExpanded = false }
                                    ) {
                                        AppStyleTheme.entries.forEach { style ->
                                            DropdownMenuItem(
                                                text = { Text(style.displayName) },
                                                onClick = {
                                                    viewModel.setStyleTheme(style)
                                                    styleDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Language Selection
                                ExposedDropdownMenuBox(
                                    expanded = langDropdownExpanded,
                                    onExpandedChange = { langDropdownExpanded = !langDropdownExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = selectedLanguage.displayName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("應用程式語系 (Language)") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langDropdownExpanded) },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth()
                                    )

                                    ExposedDropdownMenu(
                                        expanded = langDropdownExpanded,
                                        onDismissRequest = { langDropdownExpanded = false }
                                    ) {
                                        AppLanguage.entries.forEach { lang ->
                                            DropdownMenuItem(
                                                text = { Text(lang.displayName) },
                                                onClick = {
                                                    viewModel.setLanguage(lang)
                                                    langDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Currency Selection
                                ExposedDropdownMenuBox(
                                    expanded = currDropdownExpanded,
                                    onExpandedChange = { currDropdownExpanded = !currDropdownExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = selectedCurrency.displayName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("記帳預設幣別 (Currency)") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currDropdownExpanded) },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth()
                                    )

                                    ExposedDropdownMenu(
                                        expanded = currDropdownExpanded,
                                        onDismissRequest = { currDropdownExpanded = false }
                                    ) {
                                        AppCurrency.entries.forEach { curr ->
                                            DropdownMenuItem(
                                                text = { Text("${curr.displayName} - 符號 ${curr.symbol} (小數位數: ${curr.decimalPlaces})") },
                                                onClick = {
                                                    viewModel.setCurrency(curr)
                                                    currDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Dynamic Custom Category Management Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "自訂記帳類別設定",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "已建立 ${customCategories.size} / 20 種類別 (防呆機制已啟用)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            editingCategory = null
                                            showCategoryDialog = true
                                        },
                                        enabled = customCategories.size < 20,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add Category",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("新增類別", fontSize = 12.sp)
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                                customCategories.forEach { cat ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(cat.parseColor())
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "類別 ${cat.code}：${cat.name}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (cat.isIncome) "[收入]" else "[支出]",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (cat.isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                        }

                                        Row {
                                            IconButton(
                                                onClick = {
                                                    editingCategory = cat
                                                    showCategoryDialog = true
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Category",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            IconButton(
                                                onClick = { categoryToDelete = cat },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Category",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // About App Card
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAboutDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "關於 MyMoneyKeep",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "版本資訊",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // 登入診斷按鈕 (開發診斷用)
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val diagFile = java.io.File(context.filesDir, "sign_in_error.txt")
                                    diagLogContent = if (diagFile.exists()) diagFile.readText() else "目前無診斷記錄。\n請嘗試登入失敗後再點此查看。"
                                    showDiagDialog = true
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔍 登入診斷記錄",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "點此查看",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                } else {
                    // ==========================================
                    // 頁籤 2: 【帳號設定】
                    // ==========================================

                    // Google Account Status Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Google Account",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (accountState.isSignedIn) accountState.displayName else "未登入 Google 帳號",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = if (accountState.isSignedIn) accountState.email else "請點擊登入以同步雲端試算表",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (accountState.isSignedIn) {
                                            viewModel.syncManager.signOut()
                                        } else {
                                            signInLauncher.launch(viewModel.syncManager.getSignInIntent())
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (accountState.isSignedIn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                                        contentColor = if (accountState.isSignedIn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text(if (accountState.isSignedIn) "登出帳號" else "登入帳號")
                                }
                            }
                        }
                    }

                    // Google Sheets Configuration Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Google Drive 雲端同步設定",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )

                                    TextButton(onClick = { showArchitectureSolutionDialog = true }) {
                                        Text("架構說明", fontSize = 12.sp)
                                    }
                                }

                                // Status Box showing Folder & Yearly Sheet structure
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = "Folder",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("專屬雲端資料夾", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(accountState.driveFolder, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            }
                                        }

                                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Description,
                                                contentDescription = "Sheet",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("雲端 Google 試算表檔案", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(accountState.sheetTitle, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Sync,
                                                contentDescription = "Status",
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "雲端格式：原生 Google 試算表 (Google Sheets)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                val success = viewModel.syncToGoogleDrive()
                                                // 同步完成後讀取最新 accountState（已被 syncToDrive 更新）
                                                val finalState = viewModel.googleAccountState.value
                                                if (success) {
                                                    val syncTime = finalState.lastSyncTime
                                                    if (syncTime.contains("已套用美化排版")) {
                                                        Toast.makeText(context, "✅ 已成功同步並套用美化排版至『${finalState.driveFolder} / ${finalState.sheetTitle}』", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        // 成功備份但排版降級為純文字，彈出診斷對話框提示
                                                        syncErrorDetail = finalState.lastSyncError.ifBlank { "Google Sheets API 未能成功套用排版，已降級為純文字備援格式儲存至雲端。" }
                                                        showSyncErrorDialog = true
                                                    }
                                                } else {
                                                    // 同步失敗，彈出完整診斷對話框
                                                    syncErrorDetail = finalState.lastSyncError.ifBlank { "同步失敗，請確認網路連線或重新登入授權。" }
                                                    showSyncErrorDialog = true
                                                }
                                            }
                                        },
                                        enabled = accountState.isSignedIn && !accountState.isSyncing,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        if (accountState.isSyncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("處理中...")
                                        } else {
                                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = "Sync")
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("同步至試算表")
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            showRestoreConfirmDialog = true
                                        },
                                        enabled = accountState.isSignedIn && !accountState.isSyncing,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = "Restore")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("從試算表還原")
                                    }
                                }
                            }
                        }
                    }

                    // Backup & CSV Import/Export
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "試算表全表 CSV 備份 (複製/匯入)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )

                                Text(
                                    text = "完全相容『${accountState.driveFolder} / ${accountState.sheetTitle}』資料格式。複製後可直接貼入 Google 試算表或 Excel；貼上匯入時自動忽略標頭，並按日期智慧排序與計算結餘。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val csvData = viewModel.exportCsv()
                                            clipboardManager.setText(AnnotatedString(csvData))
                                            Toast.makeText(context, "已複製全表 CSV！可直接貼上至 Google 試算表或 Excel", Toast.LENGTH_LONG).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = "Export")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("複製 CSV 試算表", fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = { showImportDialog = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = "Import")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("貼上匯入 CSV", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Gemini AI API Key Card (BYOK)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Gemini AI 辨識密鑰 (API Key) 設定",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )

                                Text(
                                    text = "如需使用 AI 自然語言解析與智慧分類功能，請在此填入您的專屬 Gemini API Key。未填寫時系統將停用 AI 自動辨識，改用手動規則檢查與防呆驗證。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedTextField(
                                    value = customApiKeyInput,
                                    onValueChange = {
                                        customApiKeyInput = it
                                        viewModel.syncManager.updateGeminiApiKey(it)
                                    },
                                    label = { Text("Gemini API Key (需填寫以啟用 AI 解析)") },
                                    leadingIcon = { Icon(imageVector = Icons.Default.Key, contentDescription = "API Key") },
                                    trailingIcon = {
                                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                            Icon(
                                                imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle Visibility"
                                            )
                                        }
                                    },
                                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedButton(
                                    onClick = {
                                        viewModel.syncManager.updateGeminiApiKey(customApiKeyInput)
                                        Toast.makeText(
                                            context,
                                            if (customApiKeyInput.isNotBlank()) "已儲存 Gemini API Key！已啟用 AI 智慧解析功能。" else "已清除 API Key（AI 智慧解析功能已關閉，改用標準規則驗證）",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Save Key")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("儲存 API Key 設定")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("貼上匯入 Google 試算表 CSV 內容") },
            text = {
                OutlinedTextField(
                    value = csvImportText,
                    onValueChange = { csvImportText = it },
                    placeholder = { Text("貼上記錄 CSV，格式：\n項目,日期,標題,類別,收入,支出,小計\n1,2025/12/5,發薪日,A,47540,,47540...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importCsv(csvImportText)
                        showImportDialog = false
                        Toast.makeText(context, "試算表 CSV 資料已成功匯入！已按日期重新排序並計算結餘。", Toast.LENGTH_LONG).show()
                    },
                    enabled = csvImportText.isNotBlank()
                ) {
                    Text("確認匯入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("關於 MyMoneyKeep") },
            text = {
                Column {
                    Text("MyMoneyKeep 雲端記帳本", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("版本：${com.example.BuildConfig.VERSION_NAME}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("版權所有 © 2026 Ordinary People Studio")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("確定")
                }
            }
        )
    }

    // Detailed Sync Error / Diagnostic Report Dialog
    if (showSyncErrorDialog) {
        AlertDialog(
            onDismissRequest = { showSyncErrorDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Sync Report",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("雲端同步診斷報告", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = syncErrorDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(syncErrorDetail))
                        Toast.makeText(context, "已將完整診斷報告複製至剪貼簿！", Toast.LENGTH_SHORT).show()
                        showSyncErrorDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("複製報告並關閉")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncErrorDialog = false }) {
                    Text("關閉")
                }
            }
        )
    }

    // === 登入診斷 Dialog ===
    if (showDiagDialog) {
        AlertDialog(
            onDismissRequest = { showDiagDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Diag",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🔍 登入診斷記錄", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = diagLogContent,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(diagLogContent))
                        Toast.makeText(context, "已複製診斷記錄至剪貼簿！", Toast.LENGTH_SHORT).show()
                        showDiagDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("複製並關閉")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiagDialog = false }) {
                    Text("關閉")
                }
            }
        )
    }


    if (showCategoryDialog) {
        EditCategoryDialog(
            category = editingCategory,
            onDismiss = { showCategoryDialog = false },
            onSave = { name, isIncome, colorHex ->
                if (editingCategory != null) {
                    viewModel.updateCategory(editingCategory!!.code, name, isIncome, colorHex)
                    Toast.makeText(context, "已更新類別 『$name』", Toast.LENGTH_SHORT).show()
                } else {
                    val success = viewModel.addCategory(name, isIncome, colorHex)
                    if (success) {
                        Toast.makeText(context, "已新增類別 『$name』", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "新增失敗：類別數量已達上限 (20種)", Toast.LENGTH_SHORT).show()
                    }
                }
                showCategoryDialog = false
            }
        )
    }

    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("確認刪除類別 『${cat.name}』？") },
            text = {
                Text("刪除類別後，未來或過去使用此類別的記帳記錄將會自動觸發防呆機制，標示為『未知類別』(值為空值)。確定刪除嗎？")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(cat.code)
                        Toast.makeText(context, "已刪除類別 『${cat.name}』", Toast.LENGTH_SHORT).show()
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showRetainDataDialog) {
        AlertDialog(
            onDismissRequest = { showRetainDataDialog = false },
            title = { Text("保留手機端現有記帳紀錄？") },
            text = {
                Text("檢測到手機本機目前有 ${allTransactions.size} 筆歷史記帳資料。\n\n登入 Google 帳號時，您希望如何處理這些本機資料？\n\n• 【保留】: 延用手機現有資料，並可將紀錄同步至雲端試算表。\n• 【不保留】: 清空本機歷史資料，以全新帳號開始。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.loginWithGoogle(retainLocalData = true)
                        Toast.makeText(context, "已成功連結 Google 帳號並保留本機資料！", Toast.LENGTH_SHORT).show()
                        showRetainDataDialog = false
                    }
                ) {
                    Text("保留手機資料")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.loginWithGoogle(retainLocalData = false)
                        Toast.makeText(context, "已清空本機資料並連結 Google 帳號！", Toast.LENGTH_SHORT).show()
                        showRetainDataDialog = false
                    }
                ) {
                    Text("不保留 (清空)")
                }
            }
        )
    }

    if (showArchitectureSolutionDialog) {
        AlertDialog(
            onDismissRequest = { showArchitectureSolutionDialog = false },
            title = { Text("雲端記帳本架構與同步機制說明") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "MyMoneyKeep 採用安全、極速且直覺的雲端試算表架構，讓您在手機與電腦端都能輕鬆掌握財務狀況：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "1. 專屬雲端空間集中管理",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• 系統會在您的 Google 雲端硬碟中自動建立專屬『MyMoneyKeep_雲端記帳本』資料夾，集中存放歷年記帳試算表，乾淨不干擾個人其他檔案。",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "2. 按年度自動分檔，長年使用依然極速",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• 系統自動依年度建立獨立的 Google 試算表（例如：『2026_MyMoneyKeep_記帳本』）。即使記帳多年，單一檔案依然輕巧流暢，檢視與搜尋零卡頓。",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "3. 原生排版美化套版與自訂類別色彩",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• 同步時自動套用整齊排版（第一列標題橫幅、欄位置中、收入/支出/小計靠右對齊）。\n• 試算表中的『標題』文字會即時依據您在 APP 內為各類別設定的專屬色彩顯示，一目了然。",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "4. 無損還原與全自動連線",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• 換機或重新安裝時，按『從試算表還原』即可將雲端資料完整下載回手機，且完全不會破壞雲端既有的精美排版與樣式。\n• 免手動填寫複雜 ID，只要登入 Google 帳號即可全自動完成所有串接。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showArchitectureSolutionDialog = false }) {
                    Text("了解並關閉")
                }
            }
        )
    }

    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            icon = { Icon(Icons.Default.CloudDownload, contentDescription = "Restore", tint = MaterialTheme.colorScheme.primary) },
            title = { Text("從 Google 試算表下載還原？") },
            text = {
                Text("即將從 Google Drive 專屬目錄『${accountState.driveFolder} / ${accountState.sheetTitle}』下載 Google 試算表資料。\n\n⚠️ 注意：此操作將會以雲端試算表的紀錄覆蓋手機本機的記帳明細，確定要繼續嗎？")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        coroutineScope.launch {
                            val success = viewModel.restoreFromGoogleDrive()
                            if (success) {
                                Toast.makeText(context, "已成功從 Google 試算表還原記帳紀錄！", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "還原失敗，找不到雲端試算表或網路發生錯誤", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("確認還原")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDiagDialog) {
        AlertDialog(
            onDismissRequest = { showDiagDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = "Diag", tint = MaterialTheme.colorScheme.error) },
            title = { Text("🔍 Google 登入與憑證診斷") },
            text = {
                val currentRuntimeSha1 = com.example.util.AppSignatureHelper.getAppSignatureSHA1(context)
                val fullDisplayContent = if (diagLogContent.isNotBlank()) {
                    diagLogContent
                } else {
                    "【當前環境憑證資訊】\n" +
                    "● 當前運行 APK 簽章 SHA-1:\n$currentRuntimeSha1\n\n" +
                    "● 應用程式套件名: ${context.packageName}\n\n" +
                    "目前尚無最近的登入失敗紀錄。"
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = fullDisplayContent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentRuntimeSha1 = com.example.util.AppSignatureHelper.getAppSignatureSHA1(context)
                        val textToCopy = if (diagLogContent.isNotBlank()) {
                            diagLogContent
                        } else {
                            "【當前環境憑證資訊】\n" +
                            "● 當前運行 APK 簽章 SHA-1:\n$currentRuntimeSha1\n\n" +
                            "● 應用程式套件名: ${context.packageName}"
                        }
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("MMK_SignIn_Diag", textToCopy)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "已複製診斷資訊至剪貼簿！", Toast.LENGTH_SHORT).show()
                        showDiagDialog = false
                    }
                ) {
                    Text("複製診斷報告")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiagDialog = false }) {
                    Text("關閉")
                }
            }
        )
    }
}

@Composable
fun EditCategoryDialog(
    category: CustomCategory? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, isIncome: Boolean, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var isIncome by remember { mutableStateOf(category?.isIncome ?: false) }
    var selectedColorHex by remember { mutableStateOf(category?.colorHex ?: CustomCategory.PRESET_COLORS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (category == null) "新增自訂記帳類別" else "編輯記帳類別 ${category.code}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("類別名稱 (例：寵物支出、娛樂)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "類別屬性",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isIncome,
                        onClick = { isIncome = false },
                        label = { Text("支出類別") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isIncome,
                        onClick = { isIncome = true },
                        label = { Text("收入類別") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "代表顏色",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Grid of Preset Colors
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CustomCategory.PRESET_COLORS.chunked(8).forEach { colorRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            colorRow.forEach { hex ->
                                val color = try {
                                    Color(android.graphics.Color.parseColor(hex))
                                } catch (e: Exception) {
                                    Color.Gray
                                }
                                val isSelected = hex.equals(selectedColorHex, ignoreCase = true)

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { selectedColorHex = hex }
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, isIncome, selectedColorHex) },
                enabled = name.isNotBlank()
            ) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
