package com.adr.checkiap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.adr.checkiap.data.model.ProductModel
import com.adr.checkiap.data.model.ScanStatus
import com.adr.checkiap.data.scanner.ApkAnalyzer
import com.adr.checkiap.data.scanner.InstalledAppInfo
import com.adr.checkiap.data.scanner.PlayStoreScraper
import com.adr.checkiap.ui.apps.AppInspectScreen
import com.adr.checkiap.ui.apps.InstalledAppsScreen
import com.adr.checkiap.ui.apps.InstalledAppsViewModel
import com.adr.checkiap.ui.explore.ExploreScreen
import com.adr.checkiap.ui.explore.ExploreViewModel
import com.adr.checkiap.ui.explore.ProductDetailScreen
import com.adr.checkiap.ui.history.HistoryScreen
import com.adr.checkiap.ui.qa.TestCenterScreen
import com.adr.checkiap.ui.theme.AccentBlue
import com.adr.checkiap.ui.theme.AdrCheckIapTheme
import com.adr.checkiap.ui.theme.DarkBackground
import com.adr.checkiap.ui.theme.DarkSurface
import com.adr.checkiap.ui.theme.TextMuted
import com.adr.checkiap.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab(val title: String, val icon: ImageVector) {
    APPS("Apps", Icons.Default.Apps),
    SCAN("Quét IAP", Icons.Default.Explore),
    HISTORY("Lịch sử", Icons.Default.Folder),
    SETTINGS("Cài đặt", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as AdrCheckIapApp

        val exploreViewModel: ExploreViewModel by viewModels {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ExploreViewModel(
                        app.billingManager,
                        app.database.scanDao(),
                        app.scanProductsUseCase
                    ) as T
                }
            }
        }

        val installedAppsViewModel: InstalledAppsViewModel by viewModels()

        setContent {
            AdrCheckIapTheme {
                MainContent(
                    app = app,
                    exploreViewModel = exploreViewModel,
                    installedAppsViewModel = installedAppsViewModel
                )
            }
        }
    }
}

sealed interface InspectNav {
    data object None : InspectNav
    data class AppInspect(val appInfo: InstalledAppInfo) : InspectNav
}

@Composable
fun MainContent(
    app: AdrCheckIapApp,
    exploreViewModel: ExploreViewModel,
    installedAppsViewModel: InstalledAppsViewModel
) {
    var currentTab by remember { mutableStateOf(MainTab.APPS) }
    var selectedProduct by remember { mutableStateOf<ProductModel?>(null) }
    var inspectNav by remember { mutableStateOf<InspectNav>(InspectNav.None) }

    // Inspect state
    var inspectProducts by remember { mutableStateOf<List<ProductModel>>(emptyList()) }
    var inspectScanning by remember { mutableStateOf(false) }
    var inspectError by remember { mutableStateOf<String?>(null) }

    // APK analysis state
    var iapSummary by remember { mutableStateOf<ApkAnalyzer.AppIapSummary?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }

    // Play Store info
    var storeInfo by remember { mutableStateOf<PlayStoreScraper.StoreListingInfo?>(null) }

    val scope = rememberCoroutineScope()
    val apkAnalyzer = remember { ApkAnalyzer(app) }
    val showBottomBar = selectedProduct == null && inspectNav is InspectNav.None

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = TextPrimary
                ) {
                    MainTab.values().forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentBlue,
                                selectedTextColor = AccentBlue,
                                indicatorColor = DarkBackground,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                selectedProduct != null -> {
                    ProductDetailScreen(
                        product = selectedProduct!!,
                        billingManager = app.billingManager,
                        onBack = { selectedProduct = null }
                    )
                }
                inspectNav is InspectNav.AppInspect -> {
                    val nav = inspectNav as InspectNav.AppInspect
                    // Auto-fetch store info on first open
                    if (storeInfo == null) {
                        scope.launch {
                            storeInfo = PlayStoreScraper.fetchStoreInfo(nav.appInfo.packageName)
                        }
                    }

                    // Auto-analyze APK DEX strings and auto-query billing
                    if (iapSummary == null && !isAnalyzing) {
                        scope.launch {
                            isAnalyzing = true
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    apkAnalyzer.analyzeApp(nav.appInfo.packageName)
                                }
                                iapSummary = result

                                val candidateIds = result.products
                                    .filter { it.confidence != ApkAnalyzer.Confidence.LOW }
                                    .map { it.productId }
                                if (candidateIds.isNotEmpty()) {
                                    inspectScanning = true
                                    try {
                                        val results = withContext(Dispatchers.IO) {
                                            val list = mutableListOf<ProductModel>()
                                            list.addAll(app.billingManager.queryProducts(candidateIds, com.adr.checkiap.data.model.ProductType.SUBS))
                                            list.addAll(app.billingManager.queryProducts(candidateIds, com.adr.checkiap.data.model.ProductType.INAPP))
                                            list
                                        }
                                        inspectProducts = results
                                            .groupBy { it.productId }
                                            .map { (_, models) ->
                                                models.firstOrNull { it.status == ScanStatus.FOUND }
                                                    ?: models.first()
                                            }
                                    } catch (e: Exception) {
                                        inspectError = e.localizedMessage
                                    } finally {
                                        inspectScanning = false
                                    }
                                }
                            } catch (e: Exception) {
                                inspectError = e.localizedMessage
                            } finally {
                                isAnalyzing = false
                            }
                        }
                    }

                    AppInspectScreen(
                        appInfo = nav.appInfo,
                        summary = iapSummary,
                        storeInfo = storeInfo,
                        isAnalyzing = isAnalyzing,
                        scannedProducts = inspectProducts,
                        isScanning = inspectScanning,
                        errorMessage = inspectError,
                        onAnalyzeApk = {
                            scope.launch {
                                isAnalyzing = true
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        apkAnalyzer.analyzeApp(nav.appInfo.packageName)
                                    }
                                    iapSummary = result
                                } catch (e: Exception) {
                                    inspectError = e.localizedMessage
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        },
                        onScanProducts = { inAppIds, subsIds ->
                            scope.launch {
                                inspectScanning = true
                                inspectError = null
                                try {
                                    val results = mutableListOf<ProductModel>()
                                    if (inAppIds.isNotEmpty()) {
                                        results.addAll(
                                            app.billingManager.queryProducts(
                                                inAppIds,
                                                com.adr.checkiap.data.model.ProductType.INAPP
                                            )
                                        )
                                    }
                                    if (subsIds.isNotEmpty()) {
                                        results.addAll(
                                            app.billingManager.queryProducts(
                                                subsIds,
                                                com.adr.checkiap.data.model.ProductType.SUBS
                                            )
                                        )
                                    }
                                    inspectProducts = results
                                        .groupBy { it.productId }
                                        .map { (_, models) ->
                                            models.firstOrNull { it.status == ScanStatus.FOUND }
                                                ?: models.first()
                                        }
                                } catch (e: Exception) {
                                    inspectError = e.localizedMessage ?: "Scan failed"
                                } finally {
                                    inspectScanning = false
                                }
                            }
                        },
                        onProductSelected = { selectedProduct = it },
                        onBack = {
                            inspectNav = InspectNav.None
                            inspectProducts = emptyList()
                            iapSummary = null
                            storeInfo = null
                            inspectError = null
                        }
                    )
                }
                else -> {
                    when (currentTab) {
                        MainTab.APPS -> InstalledAppsScreen(
                            viewModel = installedAppsViewModel,
                            onAppSelected = { appInfo ->
                                inspectNav = InspectNav.AppInspect(appInfo)
                            }
                        )
                        MainTab.SCAN -> ExploreScreen(
                            viewModel = exploreViewModel,
                            onProductSelected = { selectedProduct = it }
                        )
                        MainTab.HISTORY -> HistoryScreen(
                            scanDao = app.database.scanDao(),
                            onCompareSelected = { _, _ -> }
                        )
                        MainTab.SETTINGS -> TestCenterScreen(
                            billingManager = app.billingManager
                        )
                    }
                }
            }
        }
    }
}
