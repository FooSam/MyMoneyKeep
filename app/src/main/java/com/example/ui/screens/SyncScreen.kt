package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
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
            Toast.makeText(context, "登入失敗: ${e.statusCode}", Toast.LENGTH_SHORT).show()
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
                                                val success = viewModel.syncManager.syncToDrive(allTransactions)
                                                if (success) {
                                                    Toast.makeText(context, "已成功同步至 Google 試算表『${accountState.driveFolder} / ${accountState.sheetTitle}』！", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "同步失敗，請確認網路連線或重新登入授權", Toast.LENGTH_LONG).show()
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
            title = { Text("多年存檔與體驗優化 3 大可選方案") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "針對『試算表多年累積資料過大』與『手動貼上 ID 體驗繁瑣』問題，提供以下 3 種不同架構備案供您評估：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "方案 A：【按年獨立分檔 + 按月工作表】（推薦）",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• 作法：每年自動建立『YYYY_MyMoneyKeep_記帳本』，內含 12 個月份分頁與年度總覽，並維護『00_歷史目錄總頁』。\n• 特點：完全符合您習慣的結構，單檔資料量適中，自動連結免填 ID。",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "方案 B：【單一主表 + 年終自動封存至歷史庫】",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• 作法：平時只維護『MyMoneyKeep_主記帳本』。跨年或滿 5,000 筆時，系統自動將舊資料搬移封存至『歷史資料庫』試算表。\n• 特點：主試算表永遠極致輕量，日常檢視極速無卡頓。",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "方案 C：【雲端 AppData 隱形資料庫 + 依需求一鍵匯出】",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• 作法：平時備份至 Google Drive 隱藏 AppData 專屬區（純 JSON / CSV）。需要電腦檢視時，按『一鍵匯出 Google 試算表』。\n• 特點：Google Drive 目錄最乾淨、傳輸極速且隱私度最高。",
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
