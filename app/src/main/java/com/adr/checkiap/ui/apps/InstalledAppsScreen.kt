package com.adr.checkiap.ui.apps

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.adr.checkiap.data.scanner.InstalledAppInfo
import com.adr.checkiap.ui.theme.AccentBlue
import com.adr.checkiap.ui.theme.DarkBackground
import com.adr.checkiap.ui.theme.DarkBorder
import com.adr.checkiap.ui.theme.DarkCard
import com.adr.checkiap.ui.theme.DarkSurface
import com.adr.checkiap.ui.theme.PurpleTrial
import com.adr.checkiap.ui.theme.SuccessGreen
import com.adr.checkiap.ui.theme.TextMuted
import com.adr.checkiap.ui.theme.TextPrimary
import com.adr.checkiap.ui.theme.TextSecondary
import com.adr.checkiap.ui.theme.WarningYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppFilter {
    ALL,
    BILLING_ONLY,
    PLAY_STORE
}

@Composable
fun InstalledAppsScreen(
    viewModel: InstalledAppsViewModel,
    onAppSelected: (InstalledAppInfo) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "INSTALLED APPS",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Quét app đã cài • Detect IAP/Billing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = AccentBlue
                    )
                }
            }
        }

        // Stats cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatMiniCard(
                    label = "Tổng app",
                    value = "${uiState.totalApps}",
                    color = AccentBlue,
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    label = "Có Billing",
                    value = "${uiState.billingApps}",
                    color = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    label = "Play Store",
                    value = "${uiState.playStoreApps}",
                    color = PurpleTrial,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Search bar
        item {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearch(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Tìm app theo tên hoặc package...", color = TextMuted, fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = DarkBorder,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }

        // Filter chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.filter == AppFilter.ALL,
                    onClick = { viewModel.setFilter(AppFilter.ALL) },
                    label = { Text("Tất cả") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                        selectedLabelColor = AccentBlue
                    )
                )
                FilterChip(
                    selected = uiState.filter == AppFilter.BILLING_ONLY,
                    onClick = { viewModel.setFilter(AppFilter.BILLING_ONLY) },
                    label = { Text("Có Billing") },
                    leadingIcon = {
                        if (uiState.filter == AppFilter.BILLING_ONLY) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SuccessGreen.copy(alpha = 0.2f),
                        selectedLabelColor = SuccessGreen
                    )
                )
                FilterChip(
                    selected = uiState.filter == AppFilter.PLAY_STORE,
                    onClick = { viewModel.setFilter(AppFilter.PLAY_STORE) },
                    label = { Text("Play Store") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PurpleTrial.copy(alpha = 0.2f),
                        selectedLabelColor = PurpleTrial
                    )
                )
            }
        }

        // Loading
        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = AccentBlue,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Đang quét ứng dụng...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Results count
        if (!uiState.isLoading && uiState.filteredApps.isNotEmpty()) {
            item {
                Text(
                    text = "KẾT QUẢ (${uiState.filteredApps.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
            }
        }

        // App list
        items(uiState.filteredApps, key = { it.packageName }) { app ->
            AppCard(app = app, onClick = { onAppSelected(app) })
        }

        // Empty state
        if (!uiState.isLoading && uiState.filteredApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Không tìm thấy app nào",
                            color = TextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Thử thay đổi bộ lọc hoặc từ khóa",
                            color = TextMuted.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCard(
    app: InstalledAppInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (app.hasBillingPermission) SuccessGreen.copy(alpha = 0.3f) else DarkBorder
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon
            AppIcon(drawable = app.icon, size = 44)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.appName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    if (app.hasBillingPermission) {
                        BillingBadge()
                    }
                }

                Text(
                    text = app.packageName,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "v${app.versionName}",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    val installer = app.installerPackage?.let { formatInstaller(it) } ?: "Unknown"
                    Text(
                        text = installer,
                        fontSize = 10.sp,
                        color = when {
                            installer == "Play Store" -> PurpleTrial
                            else -> TextSecondary
                        }
                    )
                    Text(
                        text = formatDate(app.lastUpdateTime),
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon(drawable: Drawable?, size: Int) {
    if (drawable != null) {
        val bitmap = drawable.toBitmap(size * 2, size * 2).asImageBitmap()
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Android,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size((size / 2).dp)
            )
        }
    }
}

@Composable
private fun BillingBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(SuccessGreen.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = "IAP",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = SuccessGreen
        )
    }
}

@Composable
private fun StatMiniCard(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = TextMuted
            )
        }
    }
}

private fun formatInstaller(packageName: String): String = when (packageName) {
    "com.android.vending" -> "Play Store"
    "com.google.android.packageinstaller" -> "APK"
    "com.samsung.android.packageinstaller" -> "Samsung"
    "com.huawei.appmarket" -> "Huawei"
    "com.xiaomi.market" -> "Xiaomi"
    else -> packageName.substringAfterLast(".")
}

private val dateFormatter = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

private fun formatDate(timestamp: Long): String {
    return dateFormatter.format(Date(timestamp))
}
