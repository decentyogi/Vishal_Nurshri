package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.CashMemoViewModel
import com.example.ui.screens.BusinessProfileScreen
import com.example.ui.screens.CashMemoPreviewScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.RateScreen
import com.example.ui.screens.ScannerAndEditorScreen
import com.example.ui.theme.CashMemoTheme

enum class AppTab(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    CREATE("Create", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    RATE("Rate", Icons.Default.Sell, Icons.Outlined.Sell),
    HISTORY("Saved", Icons.Default.History, Icons.Outlined.History),
    PROFILE("Profile", Icons.Default.Storefront, Icons.Outlined.Storefront)
}

enum class ScreenState {
    MAIN_TABS,
    MEMO_PREVIEW
}

class MainActivity : ComponentActivity() {
    private val viewModel: CashMemoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CashMemoTheme {
                MainAppContainer(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContainer(viewModel: CashMemoViewModel) {
    var currentScreen by remember { mutableStateOf(ScreenState.MAIN_TABS) }
    var selectedTab by remember { mutableStateOf(AppTab.CREATE) }

    // Intercept back button when on sub-tabs
    androidx.activity.compose.BackHandler(enabled = currentScreen == ScreenState.MAIN_TABS && selectedTab != AppTab.CREATE) {
        selectedTab = AppTab.CREATE
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (currentScreen) {
            ScreenState.MEMO_PREVIEW -> {
                CashMemoPreviewScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = ScreenState.MAIN_TABS }
                )
            }

            ScreenState.MAIN_TABS -> {
                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp,
                            modifier = Modifier.testTag("bottom_nav_bar")
                        ) {
                            AppTab.values().forEach { tab ->
                                val selected = selectedTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectedTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title
                                        )
                                    },
                                    label = { Text(tab.title) },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            AppTab.CREATE -> {
                                ScannerAndEditorScreen(
                                    viewModel = viewModel,
                                    onNavigateToPreview = { currentScreen = ScreenState.MEMO_PREVIEW }
                                )
                            }

                            AppTab.RATE -> {
                                RateScreen(
                                    viewModel = viewModel,
                                    onNavigateToCreate = { selectedTab = AppTab.CREATE }
                                )
                            }

                            AppTab.HISTORY -> {
                                HistoryScreen(
                                    viewModel = viewModel,
                                    onOpenMemo = { memo ->
                                        viewModel.setPreviewMemo(memo)
                                        currentScreen = ScreenState.MEMO_PREVIEW
                                    },
                                    onNewMemo = {
                                        viewModel.resetEditor()
                                        selectedTab = AppTab.CREATE
                                    }
                                )
                            }

                            AppTab.PROFILE -> {
                                BusinessProfileScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
