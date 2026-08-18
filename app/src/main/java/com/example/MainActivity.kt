package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import com.example.ui.screens.CurrencyCalculatorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SyncScreen
import com.example.ui.screens.WelcomeLoginScreen
import com.example.ui.theme.MyMoneyKeepTheme
import com.example.ui.viewmodel.BookkeepingViewModel
import com.example.ui.viewmodel.LoginMode
import com.example.util.LocaleHelper
import com.example.widget.MyMoneyKeepWidgetProvider
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

enum class NavigationTab(
    val titleResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(R.string.nav_home, Icons.Filled.Mic, Icons.Outlined.Mic),
    LEDGER(R.string.nav_ledger, Icons.AutoMirrored.Filled.ListAlt, Icons.AutoMirrored.Outlined.ListAlt),
    EXCHANGE(R.string.nav_exchange, Icons.Filled.Calculate, Icons.Outlined.Calculate),
    REPORTS(R.string.nav_reports, Icons.Filled.Analytics, Icons.Outlined.Analytics),
    SYNC(R.string.nav_sync, Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: BookkeepingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val incomingAction = intent?.action

        setContent {
            val baseContext = LocalContext.current
            val currentLanguage by viewModel.selectedLanguage.collectAsState()
            val styleTheme by viewModel.selectedStyleTheme.collectAsState()
            val loginMode by viewModel.loginMode.collectAsState()

            val localizedContext = remember(currentLanguage) {
                LocaleHelper.applyLocale(baseContext, currentLanguage.code)
            }
            val localizedConfiguration = remember(currentLanguage, localizedContext) {
                localizedContext.resources.configuration
            }

            var showSignInDiagDialog by remember { mutableStateOf(false) }
            var signInDiagContent by remember { mutableStateOf("") }

            val welcomeSignInLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    viewModel.syncManager.handleSignInResult(account)
                    if (account != null) {
                        viewModel.loginWithGoogle(retainLocalData = true)
                    }
                } catch (e: ApiException) {
                    viewModel.syncManager.handleSignInResult(null)
                    val runtimeSha1 = com.example.util.AppSignatureHelper.getAppSignatureSHA1(baseContext)
                    val statusCode = e.statusCode
                    val statusMsg = e.status.statusMessage ?: "(無訊息)"
                    val causeMsg = e.cause?.message ?: e.cause?.javaClass?.simpleName ?: "(無 cause)"
                    val detailMsg = "【Google 登入診斷報告】\n" +
                        "● 當前運行 APK 簽章 SHA-1:\n$runtimeSha1\n\n" +
                        "● 錯誤代碼: $statusCode (${if (statusCode == 10) "DEVELOPER_ERROR" else "Error"})\n" +
                        "● 錯誤訊息: $statusMsg\n" +
                        "● Cause: $causeMsg\n" +
                        "● 應用程式套件名: ${baseContext.packageName}\n\n" +
                        "【排查三大要點】：\n" +
                        "1. GCP 憑證之 Android Client SHA-1 是否為上述字串？\n" +
                        "2. GCP OAuth 同意畫面「範圍」是否已包含 Drive 與 Sheets？\n" +
                        "3. 登入 Google 帳號是否已加入 GCP「測試使用者」清單？"

                    Log.e("MMK_SignIn", detailMsg, e)

                    // 記錄至 Firebase Crashlytics
                    com.example.util.CrashReporter.recordException(
                        throwable = e,
                        tag = "Welcome_GoogleSignIn",
                        customKeys = mapOf(
                            "runtime_sha1" to runtimeSha1,
                            "status_code" to statusCode.toString(),
                            "status_message" to statusMsg,
                            "package_name" to baseContext.packageName
                        )
                    )

                    // 寫入本機診斷檔案供查看
                    try {
                        val diagFile = java.io.File(filesDir, "sign_in_error.txt")
                        val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        diagFile.writeText("時間: $ts\n$detailMsg\n\n完整 Exception 堆疊:\n${e.stackTraceToString()}")
                    } catch (_: Exception) {}

                    signInDiagContent = detailMsg
                    showSignInDiagDialog = true
                }
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration,
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalOnBackPressedDispatcherOwner provides this@MainActivity
            ) {
                MyMoneyKeepTheme(styleTheme = styleTheme) {
                    if (showSignInDiagDialog) {
                        AlertDialog(
                            onDismissRequest = { showSignInDiagDialog = false },
                            title = { Text(stringResource(R.string.diag_dialog_title)) },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = signInDiagContent,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val clipboard = localizedContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("MMK_SignIn_Diag", signInDiagContent)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(localizedContext, localizedContext.getString(R.string.diag_copy_success), Toast.LENGTH_SHORT).show()
                                        showSignInDiagDialog = false
                                    }
                                ) {
                                    Text(stringResource(R.string.diag_btn_copy))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSignInDiagDialog = false }) {
                                    Text(stringResource(R.string.diag_btn_close))
                                }
                            }
                        )
                    }
                    if (loginMode == LoginMode.UNSET) {
                        WelcomeLoginScreen(
                            onGoogleLogin = {
                                welcomeSignInLauncher.launch(viewModel.syncManager.getSignInIntent())
                            },
                            onGuestMode = {
                                viewModel.selectGuestMode()
                            }
                        )
                    } else {
                        var selectedTab by remember {
                            mutableStateOf(
                                when (incomingAction) {
                                    ACTION_OPEN_CALCULATOR -> NavigationTab.EXCHANGE
                                    else -> NavigationTab.HOME
                                }
                            )
                        }

                        val intentAction by currentIntentAction.collectAsState()
                        LaunchedEffect(intentAction) {
                            if (intentAction == ACTION_OPEN_CALCULATOR) {
                                selectedTab = NavigationTab.EXCHANGE
                                currentIntentAction.value = null
                            }
                        }

                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                NavigationBar {
                                    NavigationTab.entries.forEach { tab ->
                                        val isSelected = selectedTab == tab
                                        val tabTitle = stringResource(tab.titleResId)
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { selectedTab = tab },
                                            label = { Text(tabTitle) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tabTitle
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                when (selectedTab) {
                                    NavigationTab.HOME -> HomeScreen(viewModel = viewModel)
                                    NavigationTab.LEDGER -> LedgerScreen(viewModel = viewModel)
                                    NavigationTab.EXCHANGE -> CurrencyCalculatorScreen(
                                        viewModel = viewModel,
                                        onNavigateToLedgerWithPrefill = {
                                            selectedTab = NavigationTab.LEDGER
                                        }
                                    )
                                    NavigationTab.REPORTS -> ReportsScreen(viewModel = viewModel)
                                    NavigationTab.SYNC -> SyncScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private val currentIntentAction = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentAction.value = intent.action
    }

    companion object {
        const val ACTION_OPEN_CALCULATOR = "com.example.mymoneykeep.ACTION_OPEN_CALCULATOR"
    }
}
