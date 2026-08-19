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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CustomCategory
import com.example.ui.viewmodel.AppCurrency
import com.example.ui.viewmodel.AppLanguage
import com.example.ui.viewmodel.AppStyleTheme
import com.example.ui.viewmodel.BookkeepingViewModel
import com.example.ui.theme.ColorExpense
import com.example.ui.theme.ColorIncome
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
    val showHomeBalance by viewModel.showHomeBalance.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var versionClickCount by remember { mutableIntStateOf(0) }
    var isDiagVisible by remember { mutableStateOf(false) }

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
    var showAboutDialog by remember { mutableStateOf(false) }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CustomCategory?>(null) }
    var categoryToDelete by remember { mutableStateOf<CustomCategory?>(null) }

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
                if (allTransactions.isNotEmpty()) {
                    showRetainDataDialog = true
                } else {
                    viewModel.loginWithGoogle(retainLocalData = true)
                }
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

            com.example.util.CrashReporter.recordException(
                throwable = e,
                tag = "Settings_GoogleSignIn",
                customKeys = mapOf(
                    "runtime_sha1" to runtimeSha1,
                    "status_code" to statusCode.toString(),
                    "status_message" to statusMsg,
                    "package_name" to context.packageName
                )
            )

            try {
                val diagFile = java.io.File(context.filesDir, "sign_in_error.txt")
                val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                diagFile.writeText("時間: $ts\n$detailMsg\n\n完整 Exception 堆疊:\n${e.stackTraceToString()}")
            } catch (_: Exception) {}

            diagLogContent = detailMsg
            showDiagDialog = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.sync_title),
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
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(stringResource(R.string.sync_general_settings), fontWeight = FontWeight.Bold) },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "General Settings") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text(stringResource(R.string.nav_sync), fontWeight = FontWeight.Bold) },
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
                                    text = stringResource(R.string.sync_preferences_title),
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
                                        label = { Text(stringResource(R.string.sync_pref_theme)) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleDropdownExpanded) },
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                                        label = { Text(stringResource(R.string.sync_pref_language)) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langDropdownExpanded) },
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                                        label = { Text(stringResource(R.string.sync_pref_currency)) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currDropdownExpanded) },
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                            .fillMaxWidth()
                                    )

                                    ExposedDropdownMenu(
                                        expanded = currDropdownExpanded,
                                        onDismissRequest = { currDropdownExpanded = false }
                                    ) {
                                        AppCurrency.entries.forEach { curr ->
                                            DropdownMenuItem(
                                                text = { Text("${curr.displayName} (${curr.symbol})") },
                                                onClick = {
                                                    viewModel.setCurrency(curr)
                                                    currDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // Home Balance Switch
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.sync_pref_show_home_balance),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = stringResource(R.string.sync_pref_show_home_balance_desc),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = showHomeBalance,
                                        onCheckedChange = { viewModel.setShowHomeBalance(it) }
                                    )
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
                                            text = stringResource(R.string.sync_categories_title),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = stringResource(R.string.sync_categories_count, customCategories.size, 20),
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
                                        Text(stringResource(R.string.sync_btn_add_category), fontSize = 12.sp)
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

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
                                                text = "${cat.code} ${cat.name}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (cat.isIncome) stringResource(R.string.dialog_type_income) else stringResource(R.string.dialog_type_expense),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (cat.isIncome) ColorIncome else ColorExpense
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
                                                    contentDescription = "Edit",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = { categoryToDelete = cat },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Version Info Card (連點 6 次解鎖診斷按鈕)
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    versionClickCount++
                                    if (versionClickCount >= 6) {
                                        isDiagVisible = true
                                        Toast.makeText(context, context.getString(R.string.sync_easter_unlocked), Toast.LENGTH_SHORT).show()
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${stringResource(R.string.welcome_title)} v${com.example.BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.sync_about_copyright),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (isDiagVisible) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            val diagFile = java.io.File(context.filesDir, "sign_in_error.txt")
                                            diagLogContent = if (diagFile.exists()) diagFile.readText() else "目前無診斷記錄。\n請嘗試登入失敗後再點此查看。"
                                            showDiagDialog = true
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.BugReport, contentDescription = "Diag", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.sync_diag_title))
                                    }
                                }
                            }
                        }
                    }

                } else {
                    // ==========================================
                    // 頁籤 2: 【帳號設定】
                    // ==========================================

                    // Google Account & Cloud Backup Card
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
                                        text = stringResource(R.string.sync_google_account_title),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )

                                    TextButton(onClick = { showArchitectureSolutionDialog = true }) {
                                        Icon(Icons.Default.Info, contentDescription = "Guide", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.sync_btn_architecture_guide), fontSize = 12.sp)
                                    }
                                }

                                if (accountState.isSignedIn) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = "User",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = accountState.email,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = stringResource(R.string.sync_account_logged_in),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.logoutGoogle() },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(stringResource(R.string.sync_btn_logout), fontSize = 12.sp)
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                                    // Cloud Info
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = stringResource(R.string.sync_drive_folder_label),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = accountState.driveFolder,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.sync_google_sheet_label),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = accountState.sheetTitle,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val success = viewModel.syncToGoogleDrive()
                                                    val finalState = viewModel.googleAccountState.value
                                                    if (success) {
                                                        Toast.makeText(context, context.getString(R.string.sync_toast_synced, finalState.sheetTitle), Toast.LENGTH_LONG).show()
                                                    } else {
                                                        syncErrorDetail = finalState.lastSyncError.ifBlank { "Sync failed. Please check network connection." }
                                                        showSyncErrorDialog = true
                                                    }
                                                }
                                            },
                                            enabled = !accountState.isSyncing,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            if (accountState.isSyncing) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("...")
                                            } else {
                                                Icon(Icons.Default.CloudUpload, contentDescription = "Sync", modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.sync_btn_sync_to_cloud))
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { showRestoreConfirmDialog = true },
                                            enabled = !accountState.isSyncing,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.CloudDownload, contentDescription = "Restore", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.sync_btn_restore_from_cloud))
                                        }
                                    }
                                } else {
                                    Text(
                                        text = stringResource(R.string.sync_account_not_logged_in),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = {
                                            signInLauncher.launch(viewModel.syncManager.getSignInIntent())
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.AccountCircle, contentDescription = "Login", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.sync_btn_login))
                                    }
                                }
                            }
                        }
                    }

                    // CSV Backup Card
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
                                Text(
                                    text = stringResource(R.string.sync_csv_backup_title),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = stringResource(R.string.sync_csv_backup_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val csvData = viewModel.exportCsv()
                                            clipboardManager.setText(AnnotatedString(csvData))
                                            Toast.makeText(context, context.getString(R.string.sync_toast_csv_copied), Toast.LENGTH_LONG).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy CSV", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.sync_btn_copy_csv))
                                    }

                                    OutlinedButton(
                                        onClick = { showImportDialog = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = "Paste CSV", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.sync_btn_paste_csv))
                                    }
                                }
                            }
                        }
                    }

                    // Gemini AI Configuration Card (帳號設定頁面)
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
                                Text(
                                    text = stringResource(R.string.sync_gemini_api_title),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = stringResource(R.string.sync_gemini_api_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedTextField(
                                    value = customApiKeyInput,
                                    onValueChange = { customApiKeyInput = it },
                                    label = { Text(stringResource(R.string.sync_gemini_api_placeholder)) },
                                    singleLine = true,
                                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                            Icon(
                                                imageVector = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle Key Visibility"
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = {
                                        viewModel.syncManager.updateGeminiApiKey(customApiKeyInput)
                                        Toast.makeText(context, context.getString(R.string.sync_toast_key_saved), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Key, contentDescription = "Save Key", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.sync_btn_save_key))
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
            title = { Text(stringResource(R.string.sync_paste_csv_title)) },
            text = {
                OutlinedTextField(
                    value = csvImportText,
                    onValueChange = { csvImportText = it },
                    placeholder = { Text(stringResource(R.string.sync_paste_csv_placeholder)) },
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
                        Toast.makeText(context, context.getString(R.string.sync_toast_csv_imported), Toast.LENGTH_LONG).show()
                    },
                    enabled = csvImportText.isNotBlank()
                ) {
                    Text(stringResource(R.string.sync_paste_csv_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.ledger_btn_cancel))
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.sync_version_info_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.welcome_title), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.sync_about_version, com.example.BuildConfig.VERSION_NAME))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.sync_about_copyright))
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.diag_btn_close))
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
                    Text(stringResource(R.string.sync_diag_report_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
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
                        Toast.makeText(context, context.getString(R.string.sync_toast_copied), Toast.LENGTH_SHORT).show()
                        showSyncErrorDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.diag_btn_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncErrorDialog = false }) {
                    Text(stringResource(R.string.diag_btn_close))
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
                    Text(stringResource(R.string.sync_diag_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
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
                        Toast.makeText(context, context.getString(R.string.sync_toast_copied), Toast.LENGTH_SHORT).show()
                        showDiagDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.diag_btn_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiagDialog = false }) {
                    Text(stringResource(R.string.diag_btn_close))
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
                } else {
                    viewModel.addCategory(name, isIncome, colorHex)
                }
                showCategoryDialog = false
            }
        )
    }

    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text(stringResource(R.string.ledger_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.ledger_delete_confirm_msg))
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(cat.code)
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.ledger_btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text(stringResource(R.string.ledger_btn_cancel))
                }
            }
        )
    }

    if (showRetainDataDialog) {
        AlertDialog(
            onDismissRequest = { showRetainDataDialog = false },
            title = { Text(stringResource(R.string.sync_retain_data_title)) },
            text = {
                Text(stringResource(R.string.sync_retain_data_msg, allTransactions.size))
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.loginWithGoogle(retainLocalData = true)
                        showRetainDataDialog = false
                    }
                ) {
                    Text(stringResource(R.string.sync_btn_retain_data))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.loginWithGoogle(retainLocalData = false)
                        showRetainDataDialog = false
                    }
                ) {
                    Text(stringResource(R.string.sync_btn_clear_local_data))
                }
            }
        )
    }

    if (showArchitectureSolutionDialog) {
        AlertDialog(
            onDismissRequest = { showArchitectureSolutionDialog = false },
            title = { Text(stringResource(R.string.sync_architecture_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.sync_architecture_folder_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.sync_architecture_folder_desc),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = stringResource(R.string.sync_architecture_yearly_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.sync_architecture_yearly_desc),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = stringResource(R.string.sync_architecture_style_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.sync_architecture_style_desc),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = stringResource(R.string.sync_architecture_restore_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.sync_architecture_restore_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showArchitectureSolutionDialog = false }) {
                    Text(stringResource(R.string.sync_architecture_btn_close))
                }
            }
        )
    }

    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            icon = { Icon(Icons.Default.CloudDownload, contentDescription = "Restore", tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.sync_confirm_restore_title)) },
            text = {
                Text(stringResource(R.string.sync_confirm_restore_msg))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        coroutineScope.launch {
                            val success = viewModel.restoreFromGoogleDrive()
                            if (success) {
                                Toast.makeText(context, context.getString(R.string.sync_toast_restore_success), Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.sync_toast_restore_failed), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.sync_btn_restore_from_cloud))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text(stringResource(R.string.ledger_btn_cancel))
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
        title = { Text(text = stringResource(if (category == null) R.string.sync_dialog_add_cat_title else R.string.home_edit_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.sync_dialog_cat_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.sync_dialog_cat_type),
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
                        label = { Text(stringResource(R.string.dialog_type_expense)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorExpense.copy(alpha = 0.2f),
                            selectedLabelColor = ColorExpense
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isIncome,
                        onClick = { isIncome = true },
                        label = { Text(stringResource(R.string.dialog_type_income)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorIncome.copy(alpha = 0.2f),
                            selectedLabelColor = ColorIncome
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = stringResource(R.string.sync_dialog_cat_color),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CustomCategory.PRESET_COLORS.chunked(8).forEach { colorRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            colorRow.forEach { hex ->
                                val color = try {
                                    Color(android.graphics.Color.parseColor(hex))
                                } catch (_: Exception) {
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
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), isIncome, selectedColorHex)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.dialog_btn_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_btn_cancel))
            }
        }
    )
}
