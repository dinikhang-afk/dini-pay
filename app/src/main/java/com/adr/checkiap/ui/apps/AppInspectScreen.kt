package com.adr.checkiap.ui.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.adr.checkiap.accessibility.IapAccessibilityService
import com.adr.checkiap.data.model.IapItemModel
import com.adr.checkiap.data.model.ProductModel
import com.adr.checkiap.data.repository.StoreAppRepository
import com.adr.checkiap.data.scanner.ApkAnalyzer
import com.adr.checkiap.data.scanner.ApkExtractor
import com.adr.checkiap.data.scanner.InstalledAppInfo
import com.adr.checkiap.data.scanner.PlayStoreScraper
import com.adr.checkiap.data.scanner.SplitApkInstaller
import com.adr.checkiap.ui.components.OfferBadge
import com.adr.checkiap.ui.components.StatusBadge
import com.adr.checkiap.ui.theme.AccentBlue
import com.adr.checkiap.ui.theme.DarkBackground
import com.adr.checkiap.ui.theme.DarkBorder
import com.adr.checkiap.ui.theme.DarkCard
import com.adr.checkiap.ui.theme.DarkSurface
import com.adr.checkiap.ui.theme.ErrorRed
import com.adr.checkiap.ui.theme.PurpleTrial
import com.adr.checkiap.ui.theme.SuccessGreen
import com.adr.checkiap.ui.theme.TextMuted
import com.adr.checkiap.ui.theme.TextPrimary
import com.adr.checkiap.ui.theme.TextSecondary
import com.adr.checkiap.ui.theme.WarningYellow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppInspectScreen(
    appInfo: InstalledAppInfo,
    summary: ApkAnalyzer.AppIapSummary?,
    storeInfo: PlayStoreScraper.StoreListingInfo?,
    isAnalyzing: Boolean,
    scannedProducts: List<ProductModel>,
    isScanning: Boolean,
    errorMessage: String?,
    onAnalyzeApk: () -> Unit,
    onScanProducts: (inAppIds: List<String>, subsIds: List<String>) -> Unit,
    onProductSelected: (ProductModel) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showManualInput by remember { mutableStateOf(false) }
    var inputIds by remember { mutableStateOf("") }
    var selectedIapItem by remember { mutableStateOf<IapItemModel?>(null) }
    var selectedDexIap by remember { mutableStateOf<ApkAnalyzer.ExtractedIap?>(null) }

    val knownIaps = remember(appInfo.packageName) {
        StoreAppRepository.getIapForApp(appInfo.packageName)
    }

    data class DiscoveredHookOffer(
        val productId: String,
        val offerToken: String,
        val basePlanId: String
    )
    var hookOffers by remember { mutableStateOf<List<DiscoveredHookOffer>>(emptyList()) }

    val triggerHookBilling: (productId: String, offerToken: String?) -> Unit = { productId, offerToken ->
        // Pass trigger via Intent extras so target app receives it on launch/resume
        val targetAppIntent = context.packageManager.getLaunchIntentForPackage(appInfo.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("com.adr.checkiap.PRODUCT_ID", productId)
            if (!offerToken.isNullOrBlank()) {
                putExtra("com.adr.checkiap.OFFER_TOKEN", offerToken)
            }
        }

        // Also prepare broadcast as backup
        val launchIntent = Intent("com.adr.checkiap.LAUNCH_BILLING").apply {
            setPackage(appInfo.packageName)
            putExtra("product_id", productId)
            if (!offerToken.isNullOrBlank()) {
                putExtra("offer_token", offerToken)
            }
        }

        if (targetAppIntent != null) {
            context.startActivity(targetAppIntent)
        }

        // Send broadcast slightly delayed so target app has time to enter foreground
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            context.sendBroadcast(launchIntent)
        }, 500)

        Toast.makeText(
            context,
            "Đang mở ${appInfo.appName} để kích hoạt Google Play Billing...",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Register receiver to listen for hook results and query discovered offers
    DisposableEffect(appInfo.packageName) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    "com.adr.checkiap.OFFERS_RESULT" -> {
                        val count = intent.getIntExtra("count", 0)
                        val list = mutableListOf<DiscoveredHookOffer>()
                        for (i in 0 until count) {
                            val p = intent.getStringExtra("product_$i") ?: continue
                            val t = intent.getStringExtra("token_$i") ?: ""
                            val b = intent.getStringExtra("baseplan_$i") ?: ""
                            list.add(DiscoveredHookOffer(p, t, b))
                        }
                        if (list.isNotEmpty()) {
                            hookOffers = list
                        }
                    }
                    "com.adr.checkiap.LAUNCH_RESULT" -> {
                        val success = intent.getBooleanExtra("success", false)
                        val err = intent.getStringExtra("error")
                        val pid = intent.getStringExtra("product_id") ?: ""
                        if (success) {
                            Toast.makeText(c, "✓ Đã mở thanh toán cho $pid!", Toast.LENGTH_SHORT).show()
                        } else if (!err.isNullOrBlank()) {
                            Toast.makeText(c, "Hook: $err", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("com.adr.checkiap.OFFERS_RESULT")
            addAction("com.adr.checkiap.LAUNCH_RESULT")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        // Query target app hook immediately
        context.sendBroadcast(Intent("com.adr.checkiap.QUERY_OFFERS").apply {
            setPackage(appInfo.packageName)
        })

        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    val extractedIaps = summary?.products ?: emptyList()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("INSPECT IAP", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Text("Phân tích APK • Trích xuất Product ID", fontSize = 11.sp, color = TextMuted)
                }
            }
        }

        // App info card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconLarge(drawable = appInfo.icon, size = 56)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(appInfo.appName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                appInfo.packageName, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                color = AccentBlue, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(appInfo.packageName)) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, "Copy", tint = TextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailChip("v${appInfo.versionName}")
                            if (appInfo.hasBillingPermission) DetailChip("BILLING", SuccessGreen)
                            DetailChip(
                                appInfo.installerPackage?.let {
                                    if (it == "com.android.vending") "Play Store" else it.substringAfterLast(".")
                                } ?: "Unknown"
                            )
                        }
                    }
                }
            }
        }

        // Google Play Billing mechanism notice
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ℹ️", fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Cơ chế Google Play: Hộp thoại thanh toán/dùng thử chỉ mở được từ bên trong chính app mục tiêu (${appInfo.appName}). Khi ấn Kích hoạt, hệ thống sẽ tự chuyển sang ${appInfo.appName} để Hook gọi Google Play hiển thị popup thanh toán 0đ.",
                        fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp
                    )
                }
            }
        }

        // Accessibility Service Automation Card
        item {
            val accessibilityEnabled = remember { IapAccessibilityService.isEnabled(context) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (accessibilityEnabled) SuccessGreen.copy(alpha = 0.5f) else AccentBlue.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🤖", fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "TRỢ NĂNG (TỰ ĐỘNG ĐIỀU HƯỚNG MUA)",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (accessibilityEnabled) SuccessGreen else AccentBlue
                            )
                        }
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                .background(if (accessibilityEnabled) SuccessGreen.copy(alpha = 0.15f) else WarningYellow.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (accessibilityEnabled) "ĐÃ BẬT" else "CHƯA BẬT",
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (accessibilityEnabled) SuccessGreen else WarningYellow
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Không cần Root hay LSPatch! Trợ năng sẽ tự mở ${appInfo.appName}, tìm nút Dùng thử / PRO / Nâng cấp và bấm tự động để Google Play hiện popup thanh toán.",
                        fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    if (!accessibilityEnabled) {
                        Button(
                            onClick = { IapAccessibilityService.openSettings(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WarningYellow)
                        ) {
                            Text(
                                "BẬT DỊCH VỤ TRỢ NĂNG (CÀI ĐẶT MÁY)",
                                color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 11.sp
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                val trialKeywords = knownIaps.mapNotNull { it.trialPeriod }
                                IapAccessibilityService.startAutomation(context, appInfo.packageName, trialKeywords)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                        ) {
                            Text(
                                "⚡ TỰ ĐỘNG MỞ & CLICK ĐẾN GOOGLE PLAY",
                                color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // LSPatch Workflow Section
        item {
            var extractResult by remember { mutableStateOf<ApkExtractor.ExtractionResult?>(null) }
            var isExtracting by remember { mutableStateOf(false) }
            val lspatchInstalled = remember { ApkExtractor.isLSPatchInstalled(context) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (extractResult?.success == true) SuccessGreen.copy(alpha = 0.5f)
                    else WarningYellow.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "LSPATCH — KÍCH HOẠT OFFER ẨN",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = WarningYellow
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Patch module vào APK để kích hoạt trial/offer ẩn. Không cần root.",
                        fontSize = 11.sp, color = TextSecondary
                    )

                    Spacer(Modifier.height(12.dp))

                    // Status row
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("LSPatch Manager:", fontSize = 11.sp, color = TextMuted)
                        Text(
                            if (lspatchInstalled) "ĐÃ CÀI" else "CHƯA CÀI",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (lspatchInstalled) SuccessGreen else ErrorRed
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Extract button
                    Button(
                        onClick = {
                            isExtracting = true
                            extractResult = ApkExtractor.extractApk(context, appInfo.packageName)
                            isExtracting = false
                            if (extractResult?.success == true) {
                                Toast.makeText(
                                    context,
                                    "APK đã lưu tại Downloads/IAPCheck/",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = !isExtracting,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WarningYellow)
                    ) {
                        if (isExtracting) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp), DarkBackground, strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            "EXTRACT APK — ${appInfo.appName}",
                            color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp
                        )
                    }

                    // Result
                    if (extractResult != null) {
                        Spacer(Modifier.height(8.dp))
                        if (extractResult!!.success) {
                            Text(
                                "✓ ${extractResult!!.outputPath}",
                                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = SuccessGreen
                            )
                            val sizeText = ApkExtractor.formatFileSize(extractResult!!.fileSize)
                            val splitText = if (extractResult!!.splitCount > 1)
                                " • ${extractResult!!.splitCount} split APKs (App Bundle)"
                            else ""
                            Text(
                                "Kích thước: $sizeText$splitText",
                                fontSize = 10.sp, color = TextMuted
                            )
                        } else {
                            Text(
                                "✗ ${extractResult!!.error}",
                                fontSize = 10.sp, color = ErrorRed
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Install patched split APKs section (after user patches via LSPatch)
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val targetDir = extractResult?.outputPath?.let { File(it) }
                        ?: File(downloadsDir, "IAPCheck/${appInfo.packageName}")
                    val hasExistingApks = targetDir.exists() && (targetDir.listFiles { f -> f.extension.equals("apk", ignoreCase = true) }?.size ?: 0) > 1

                    if ((extractResult?.success == true && extractResult!!.splitCount > 1) || hasExistingApks) {
                        var installStatus by remember { mutableStateOf("") }
                        var isInstalled by remember {
                            mutableStateOf(
                                try {
                                    context.packageManager.getPackageInfo(appInfo.packageName, 0)
                                    true
                                } catch (_: Exception) {
                                    false
                                }
                            )
                        }

                        Text(
                            "SAU KHI PATCH XONG",
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = WarningYellow
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Phải gỡ bản gốc trước (tránh lỗi xung đột chữ ký), rồi mới cài bản đã patch:",
                            fontSize = 11.sp, color = TextSecondary
                        )
                        Spacer(Modifier.height(6.dp))

                        // Button to uninstall original app first
                        if (isInstalled) {
                            OutlinedButton(
                                onClick = {
                                    val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                                        data = Uri.parse("package:${appInfo.packageName}")
                                    }
                                    context.startActivity(uninstallIntent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.7f))
                            ) {
                                Text(
                                    "BƯỚC 5: GỠ CÀI ĐẶT ${appInfo.appName.uppercase()} GỐC",
                                    color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 11.sp
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }

                        Button(
                            onClick = {
                                if (targetDir.isDirectory) {
                                    when (val result = SplitApkInstaller.installFromDirectory(context, targetDir)) {
                                        is SplitApkInstaller.InstallResult.Success -> {
                                            installStatus = "Đang mở hộp thoại xác nhận cài đặt cho ${result.apkCount} APKs..."
                                        }
                                        is SplitApkInstaller.InstallResult.Failure -> {
                                            installStatus = result.message
                                        }
                                    }
                                } else {
                                    installStatus = "Thư mục không tồn tại: ${targetDir.absolutePath}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                        ) {
                            Text(
                                "BƯỚC 6: CÀI ĐẶT SPLIT APKs (ĐÃ PATCH)",
                                color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp
                            )
                        }

                        if (installStatus.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                installStatus,
                                fontSize = 11.sp,
                                color = if (installStatus.startsWith("Đang mở")) SuccessGreen else WarningYellow
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                    }

                    // Step-by-step guide
                    Text(
                        "HƯỚNG DẪN",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, color = TextMuted
                    )
                    Spacer(Modifier.height(4.dp))

                    val isSplitApp = extractResult?.splitCount?.let { it > 1 } ?: false
                    val steps = if (isSplitApp) {
                        listOf(
                            "1. Ấn Extract APK ở trên",
                            "2. Mở LSPatch → chọn base.apk từ IAPCheck/${appInfo.packageName}/",
                            "3. Embed module: ADR Check IAP → Start Patch",
                            "4. Copy APK đã patch về thư mục IAPCheck/${appInfo.packageName}/",
                            "5. Gỡ ${appInfo.appName} gốc",
                            "6. Ấn \"CÀI ĐẶT SPLIT APKs\" ở trên để cài toàn bộ splits",
                            "7. Mở ${appInfo.appName} đã patch → quay lại ấn \"Dùng thử\""
                        )
                    } else {
                        listOf(
                            "1. Ấn Extract APK ở trên",
                            "2. Mở LSPatch → chọn file APK vừa extract",
                            "3. Embed module: ADR Check IAP → Start Patch",
                            "4. Gỡ ${appInfo.appName} gốc → Cài bản đã patch",
                            "5. Mở ${appInfo.appName} đã patch → quay lại ấn \"Dùng thử\""
                        )
                    }
                    steps.forEach { step ->
                        Text(step, fontSize = 11.sp, color = TextSecondary,
                            modifier = Modifier.padding(vertical = 1.dp))
                    }

                    // Download LSPatch button if not installed
                    if (!lspatchInstalled) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/LSPosed/LSPatch/releases")
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, WarningYellow.copy(alpha = 0.5f))
                        ) {
                            Text(
                                "TẢI LSPATCH MANAGER",
                                color = WarningYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Live offers discovered directly by Xposed hook inside target app
        if (hookOffers.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, SuccessGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡", fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "OFFER ẨN BẮT ĐƯỢC TỪ HOOK TRONG APP (${hookOffers.size})",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace, color = SuccessGreen
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Hook đã kết nối với ${appInfo.appName}! Ấn nút để mở cổng thanh toán:",
                            fontSize = 11.sp, color = TextSecondary
                        )
                        Spacer(Modifier.height(10.dp))
                        hookOffers.forEach { offer ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkCard),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(offer.productId, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                                        if (offer.basePlanId.isNotBlank()) {
                                            Text("BasePlan: ${offer.basePlanId}", fontSize = 10.sp, color = TextMuted)
                                        }
                                    }
                                    Button(
                                        onClick = { triggerHookBilling(offer.productId, offer.offerToken) },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("DÙNG THỬ 0Đ", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Play Store info card
        if (storeInfo != null && storeInfo.iapPriceRange.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("PLAY STORE", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace, color = AccentBlue)
                            Spacer(Modifier.width(8.dp))
                            if (storeInfo.containsAds) {
                                MiniTag("ADS", WarningYellow)
                                Spacer(Modifier.width(4.dp))
                            }
                            MiniTag("IAP", SuccessGreen)
                        }
                        Spacer(Modifier.height(8.dp))

                        // IAP price range
                        Text(storeInfo.iapPriceRange, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold, color = TextPrimary)

                        // Developer + category
                        if (storeInfo.developer.isNotBlank() || storeInfo.category.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            val meta = listOfNotNull(
                                storeInfo.developer.takeIf { it.isNotBlank() },
                                storeInfo.category.takeIf { it.isNotBlank() },
                                storeInfo.rating.takeIf { it.isNotBlank() }?.let { "★ $it" },
                                storeInfo.installs.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")
                            Text(meta, fontSize = 11.sp, color = TextSecondary)
                        }

                        // Subscription details from store page
                        if (storeInfo.subscriptionDetails.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Subscription Info", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace, color = TextMuted)
                            Spacer(Modifier.height(4.dp))
                            storeInfo.subscriptionDetails.take(5).forEach { detail ->
                                Text(detail, fontSize = 11.sp, color = TextSecondary,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
                            }
                        }
                    }
                }
            }
        }
        // Google Play Known IAP Packages (Pricing, Trial, SKU)
        if (knownIaps.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "GÓI IAP GOOGLE PLAY (${knownIaps.size})",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = SuccessGreen
                        )
                    }
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .background(SuccessGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("CÓ GIÁ & TRIAL", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = SuccessGreen)
                    }
                }
            }

            items(knownIaps) { iap ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedIapItem = iap },
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (iap.isTrial) SuccessGreen.copy(alpha = 0.5f) else DarkBorder
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(
                                iap.title,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (iap.isTrial) {
                                    MiniTag("TRIAL 0Đ", SuccessGreen)
                                }
                                if (iap.isHidden) {
                                    MiniTag("ẨN", ErrorRed)
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "SKU: ${iap.sku}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AccentBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                if (iap.priceText.isNotBlank()) {
                                    Text(
                                        iap.priceText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                }
                                if (iap.trialPeriod != null) {
                                    Text(
                                        "Dùng thử: ${iap.trialPeriod}",
                                        fontSize = 11.sp,
                                        color = PurpleTrial,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Button(
                                onClick = { selectedIapItem = iap },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (iap.isTrial) SuccessGreen else AccentBlue
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    if (iap.isTrial) "XEM GIÁ & DÙNG THỬ 0Đ" else "XEM CHI TIẾT & GIÁ",
                                    color = DarkBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (iap.renewalPriceText.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                iap.renewalPriceText,
                                fontSize = 10.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }        // DEX Analysis section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, if (extractedIaps.isNotEmpty()) SuccessGreen.copy(alpha = 0.3f) else DarkBorder
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DataObject, null, tint = PurpleTrial, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("DEX ANALYSIS", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace, color = PurpleTrial)
                        }
                        if (extractedIaps.isNotEmpty()) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                    .background(SuccessGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("${extractedIaps.size} IDs", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace, color = SuccessGreen)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Quét DEX files trong APK để trích xuất Product ID", fontSize = 11.sp,
                        color = TextMuted, lineHeight = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (isAnalyzing) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                            color = PurpleTrial, trackColor = DarkCard
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Đang phân tích DEX string table...", fontSize = 11.sp, color = PurpleTrial)
                    } else if (extractedIaps.isEmpty()) {
                        Button(
                            onClick = onAnalyzeApk, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurpleTrial)
                        ) {
                            Icon(Icons.Default.DataObject, null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PHÂN TÍCH APK", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Subscription groups
        if (summary != null && summary.subscriptionGroups.isNotEmpty()) {
            item {
                Text("SUBSCRIPTION GROUPS", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, color = PurpleTrial)
            }
            items(summary.subscriptionGroups) { group ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(8.dp).clip(CircleShape).background(PurpleTrial)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(group.name, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Product IDs - grouped by confidence
        if (extractedIaps.isNotEmpty()) {
            item {
                Text("PRODUCT IDS (${extractedIaps.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, color = TextPrimary)
            }

            val grouped = extractedIaps.groupBy { it.confidence }

            grouped[ApkAnalyzer.Confidence.HIGH]?.let { items ->
                item { ConfidenceGroup("HIGH CONFIDENCE", SuccessGreen, items, true, onSelectIap = { selectedDexIap = it }) }
            }
            grouped[ApkAnalyzer.Confidence.MEDIUM]?.let { items ->
                item { ConfidenceGroup("MEDIUM CONFIDENCE", WarningYellow, items, true, onSelectIap = { selectedDexIap = it }) }
            }
            grouped[ApkAnalyzer.Confidence.LOW]?.let { items ->
                item { ConfidenceGroup("LOW CONFIDENCE", TextMuted, items, false, onSelectIap = { selectedDexIap = it }) }
            }

            // Scan buttons
            item {
                val highMed = extractedIaps.filter { it.confidence != ApkAnalyzer.Confidence.LOW }
                    .map { it.productId }
                val allIds = extractedIaps.map { it.productId }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (highMed.isNotEmpty()) {
                        Button(
                            onClick = { onScanProducts(highMed, highMed) },
                            enabled = !isScanning,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(Modifier.size(16.dp), DarkBackground, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(Icons.Default.Search, null, tint = DarkBackground, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("QUERY BILLING (${highMed.size} IDs)", color = DarkBackground,
                                fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    if (allIds.size > highMed.size) {
                        OutlinedButton(
                            onClick = { onScanProducts(allIds, allIds) },
                            enabled = !isScanning, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TextMuted.copy(alpha = 0.4f))
                        ) {
                            Text("QUERY TẤT CẢ (${allIds.size})", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Trial info section
        if (summary != null && summary.rawTrialStrings.isNotEmpty()) {
            item {
                var expanded by remember { mutableStateOf(true) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TRIAL STRINGS (${summary.rawTrialStrings.size})", fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = SuccessGreen)
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null, tint = TextMuted, modifier = Modifier.size(18.dp)
                            )
                        }
                        AnimatedVisibility(expanded) {
                            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                summary.rawTrialStrings.forEach { s ->
                                    Text(s, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                        color = TextSecondary, modifier = Modifier
                                            .fillMaxWidth().clip(RoundedCornerShape(4.dp))
                                            .background(DarkCard).padding(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Manual input toggle
        item {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showManualInput = !showManualInput }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("NHẬP THỦ CÔNG", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, color = TextMuted)
                Spacer(Modifier.width(4.dp))
                Icon(if (showManualInput) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
            AnimatedVisibility(showManualInput, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Card(
                    Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder), shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = inputIds, onValueChange = { inputIds = it },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentBlue, unfocusedBorderColor = DarkBorder,
                                focusedContainerColor = DarkBackground, unfocusedContainerColor = DarkBackground,
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                            ),
                            placeholder = { Text("Nhập Product ID (mỗi dòng 1 ID)", color = TextMuted, fontSize = 11.sp) },
                            shape = RoundedCornerShape(6.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val ids = inputIds.lines().map { it.trim() }.filter { it.isNotBlank() }
                                if (ids.isNotEmpty()) onScanProducts(ids, ids)
                            },
                            enabled = !isScanning && inputIds.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Text("SCAN", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Billing scan results
        if (scannedProducts.isNotEmpty()) {
            item {
                val found = scannedProducts.count { it.status == com.adr.checkiap.data.model.ScanStatus.FOUND }
                Text("BILLING RESULTS — $found/${scannedProducts.size} tìm thấy",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    color = if (found > 0) SuccessGreen else WarningYellow)
            }
            items(scannedProducts.sortedBy { it.status != com.adr.checkiap.data.model.ScanStatus.FOUND }) { product ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onProductSelected(product) },
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(product.productId, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                                fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            StatusBadge(status = product.status)
                        }
                        if (product.title.isNotBlank() && product.title != product.productId) {
                            Text(product.title, fontSize = 11.sp, color = TextSecondary)
                        }
                        if (product.formattedBasePrice.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(product.formattedBasePrice, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                        }
                        if (product.offers.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                product.offers.take(4).forEach { OfferBadge(classification = it.classification) }
                            }
                        }

                        if (product.status == com.adr.checkiap.data.model.ScanStatus.FOUND) {
                            val trialOffer = product.offers.firstOrNull { it.classification == com.adr.checkiap.data.model.OfferClassification.FREE_TRIAL }
                            val bestOffer = trialOffer ?: product.offers.firstOrNull()
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    triggerHookBilling(product.productId, bestOffer?.offerToken)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (trialOffer != null) SuccessGreen else AccentBlue
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text(
                                    if (trialOffer != null) "KÍCH HOẠT DÙNG THỬ 0Đ (GOOGLE PLAY)" else "KÍCH HOẠT THANH TOÁN (GOOGLE PLAY)",
                                    color = DarkBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            item { Text(errorMessage, color = ErrorRed, fontSize = 11.sp) }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    // Dialog for Known IAP Packages
    if (selectedIapItem != null) {
        val iap = selectedIapItem!!
        AlertDialog(
            onDismissRequest = { selectedIapItem = null },
            title = {
                Text(iap.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Giá gói:", fontSize = 12.sp, color = TextMuted)
                        Text(iap.priceText.ifBlank { "N/A" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                    }
                    if (iap.trialPeriod != null) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Dùng thử:", fontSize = 12.sp, color = TextMuted)
                            Text(iap.trialPeriod, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PurpleTrial)
                        }
                    }
                    if (iap.renewalPriceText.isNotBlank()) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Gia hạn:", fontSize = 12.sp, color = TextMuted)
                            Text(iap.renewalPriceText, fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("SKU Android:", fontSize = 12.sp, color = TextMuted)
                        Text(iap.sku, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentBlue)
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Nhấn nút bên dưới để mở ${appInfo.appName} và kích hoạt hộp thoại thanh toán Google Play qua Hook:",
                        fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp
                    )

                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            triggerHookBilling(iap.sku, null)
                            selectedIapItem = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (iap.isTrial) SuccessGreen else AccentBlue)
                    ) {
                        Text(
                            if (iap.isTrial) "MỞ APP & DÙNG THỬ 0Đ (GOOGLE PLAY)" else "MỞ APP & KÍCH HOẠT (GOOGLE PLAY)",
                            color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 11.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(iap.sku))
                            Toast.makeText(context, "Đã sao chép SKU: ${iap.sku}", Toast.LENGTH_SHORT).show()
                            selectedIapItem = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("SAO CHÉP SKU ID", fontSize = 11.sp, color = AccentBlue)
                    }

                    OutlinedButton(
                        onClick = {
                            if (IapAccessibilityService.isEnabled(context)) {
                                IapAccessibilityService.startAutomation(
                                    context,
                                    appInfo.packageName,
                                    listOfNotNull(iap.title, iap.trialPeriod)
                                )
                            } else {
                                IapAccessibilityService.openSettings(context)
                            }
                            selectedIapItem = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            if (IapAccessibilityService.isEnabled(context)) "🤖 TỰ ĐỘNG ĐIỀU HƯỚNG BẰNG TRỢ NĂNG"
                            else "⚙️ BẬT TRỢ NĂNG ĐỂ TỰ ĐỘNG ĐIỀU HƯỚNG",
                            fontSize = 11.sp,
                            color = SuccessGreen
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedIapItem = null }) {
                    Text("ĐÓNG", color = TextMuted)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Dialog for DEX Extracted IAP
    if (selectedDexIap != null) {
        val iap = selectedDexIap!!
        AlertDialog(
            onDismissRequest = { selectedDexIap = null },
            title = {
                Text(iap.productId, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Phân loại:", fontSize = 12.sp, color = TextMuted)
                        Text(iap.category.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PurpleTrial)
                    }
                    if (iap.trialInfo != null) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Dùng thử:", fontSize = 12.sp, color = TextMuted)
                            Text(iap.trialInfo, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }
                    }
                    if (iap.periodHint != null) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Chu kỳ:", fontSize = 12.sp, color = TextMuted)
                            Text(iap.periodHint, fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    if (iap.pricingHint != null) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Gợi ý giá:", fontSize = 12.sp, color = TextMuted)
                            Text(iap.pricingHint, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }
                    }
                    if (iap.groupName != null) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Nhóm:", fontSize = 12.sp, color = TextMuted)
                            Text(iap.groupName, fontSize = 11.sp, color = TextSecondary)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Nhấn nút bên dưới để mở ${appInfo.appName} và kích hoạt gọi Google Play Billing qua Hook:",
                        fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp
                    )

                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            triggerHookBilling(iap.productId, null)
                            selectedDexIap = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (iap.trialInfo != null) SuccessGreen else AccentBlue
                        )
                    ) {
                        Text(
                            if (iap.trialInfo != null) "MỞ APP & DÙNG THỬ 0Đ" else "MỞ APP & KÍCH HOẠT THANH TOÁN",
                            color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 11.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(iap.productId))
                            Toast.makeText(context, "Đã sao chép Product ID: ${iap.productId}", Toast.LENGTH_SHORT).show()
                            selectedDexIap = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("SAO CHÉP PRODUCT ID", fontSize = 11.sp, color = AccentBlue)
                    }

                    OutlinedButton(
                        onClick = {
                            if (IapAccessibilityService.isEnabled(context)) {
                                IapAccessibilityService.startAutomation(
                                    context,
                                    appInfo.packageName,
                                    listOfNotNull(iap.productId, iap.trialInfo)
                                )
                            } else {
                                IapAccessibilityService.openSettings(context)
                            }
                            selectedDexIap = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            if (IapAccessibilityService.isEnabled(context)) "🤖 TỰ ĐỘNG ĐIỀU HƯỚNG BẰNG TRỢ NĂNG"
                            else "⚙️ BẬT TRỢ NĂNG ĐỂ TỰ ĐỘNG ĐIỀU HƯỚNG",
                            fontSize = 11.sp,
                            color = SuccessGreen
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDexIap = null }) {
                    Text("ĐÓNG", color = TextMuted)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

// ── Reusable composables ──────────────────────────────────────────

@Composable
private fun ConfidenceGroup(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    items: List<ApkAnalyzer.ExtractedIap>,
    defaultExpanded: Boolean,
    onSelectIap: (ApkAnalyzer.ExtractedIap) -> Unit
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text("$label (${items.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, color = color)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = TextMuted, modifier = Modifier.size(18.dp))
            }

            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { iap -> IapItemCard(iap, onSelectIap) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IapItemCard(iap: ApkAnalyzer.ExtractedIap, onSelectIap: (ApkAnalyzer.ExtractedIap) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DarkCard)
            .clickable { onSelectIap(iap) }
            .padding(10.dp)
    ) {
        // Product ID + category badge
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(
                iap.productId, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                color = TextPrimary, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (iap.isHidden) {
                    MiniTag("ẨN", ErrorRed)
                }
                CategoryBadge(iap.category)
            }
        }

        // Trial info
        if (iap.trialInfo != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(3.dp))
                        .background(SuccessGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text("TRIAL", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, color = SuccessGreen)
                }
                Spacer(Modifier.width(6.dp))
                Text(iap.trialInfo, fontSize = 11.sp, color = SuccessGreen)
            }
        }

        // Period + pricing
        val details = buildList {
            iap.periodHint?.let { add(it) }
            iap.pricingHint?.let { add(it) }
        }
        if (details.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                details.joinToString(" • "),
                fontSize = 11.sp, color = TextSecondary
            )
        }

        // Group name
        if (iap.groupName != null) {
            Spacer(Modifier.height(4.dp))
            Text(iap.groupName, fontSize = 10.sp, color = PurpleTrial, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSelectIap(iap) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (iap.trialInfo != null) SuccessGreen else AccentBlue
            ),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().height(34.dp)
        ) {
            Text(
                if (iap.trialInfo != null) "XEM CHI TIẾT & KÍCH HOẠT DÙNG THỬ 0Đ" else "XEM CHI TIẾT & KÍCH HOẠT",
                color = DarkBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CategoryBadge(category: ApkAnalyzer.IapCategory) {
    val (text, color) = when (category) {
        ApkAnalyzer.IapCategory.SUBSCRIPTION -> "SUB" to PurpleTrial
        ApkAnalyzer.IapCategory.ONE_TIME -> "BUY" to AccentBlue
        ApkAnalyzer.IapCategory.CONSUMABLE -> "COIN" to WarningYellow
        ApkAnalyzer.IapCategory.UNKNOWN -> "?" to TextMuted
    }
    MiniTag(text, color)
}

@Composable
private fun MiniTag(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, color = color)
    }
}

@Composable
private fun AppIconLarge(drawable: Drawable?, size: Int) {
    if (drawable != null) {
        val bitmap = drawable.toBitmap(size * 2, size * 2).asImageBitmap()
        Image(bitmap, null, Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)))
    } else {
        Box(Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(DarkCard), Alignment.Center) {
            Icon(Icons.Default.Android, null, tint = TextMuted, modifier = Modifier.size((size / 2).dp))
        }
    }
}

@Composable
private fun DetailChip(text: String, color: androidx.compose.ui.graphics.Color = TextSecondary) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp)).background(DarkCard)
            .border(0.5.dp, DarkBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = color)
    }
}
