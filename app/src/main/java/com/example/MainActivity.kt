package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext

import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SyncScreen
import com.example.ui.screens.WelcomeLoginScreen
import com.example.ui.theme.MyMoneyKeepTheme
import com.example.ui.viewmodel.BookkeepingViewModel
import com.example.ui.viewmodel.LoginMode
import com.example.widget.MyMoneyKeepWidgetProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

enum class NavigationTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("語音記帳", Icons.Filled.Mic, Icons.Outlined.Mic),
    LEDGER("記帳明細", Icons.AutoMirrored.Filled.ListAlt, Icons.AutoMirrored.Outlined.ListAlt),
    REPORTS("消費報表", Icons.Filled.Analytics, Icons.Outlined.Analytics),
    SYNC("帳號設定", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: BookkeepingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val incomingAction = intent?.action

        setContent {
            val context = LocalContext.current
            val styleTheme by viewModel.selectedStyleTheme.collectAsState()
            val loginMode by viewModel.loginMode.collectAsState()

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
                    Toast.makeText(context, "登入失敗: ${e.statusCode}", Toast.LENGTH_SHORT).show()
                }
            }

            MyMoneyKeepTheme(styleTheme = styleTheme) {
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
                                else -> NavigationTab.HOME
                            }
                        )
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            NavigationBar {
                                NavigationTab.entries.forEach { tab ->
                                    val isSelected = selectedTab == tab
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { selectedTab = tab },
                                        label = { Text(tab.title) },
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.title
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
                                NavigationTab.REPORTS -> ReportsScreen(viewModel = viewModel)

                                NavigationTab.SYNC -> SyncScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
