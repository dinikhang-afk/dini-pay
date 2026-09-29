package com.adr.checkiap.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adr.checkiap.data.billing.BillingConnectionState
import com.adr.checkiap.ui.components.MetricCard
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToScanner: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onSelectScan: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val recentScans by viewModel.recentScans.collectAsState()

    val snapshot = uiState.latestSnapshot
    val totalProducts = snapshot?.totalProducts ?: 0
    val totalSubs = snapshot?.totalSubs ?: 0
    val totalTrials = snapshot?.totalTrials ?: 0
    val totalDiscounts = snapshot?.totalDiscounts ?: 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ADR CHECK IAP",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Google Play Billing Official Inspector",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }

                // Connection badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    val (dotColor, label) = when (connectionState) {
                        is BillingConnectionState.Connected -> Pair(SuccessGreen, "ONLINE")
                        is BillingConnectionState.Connecting -> Pair(WarningYellow, "CONNECTING")
                        is BillingConnectionState.Disconnected -> Pair(TextMuted, "DISCONNECTED")
                        is BillingConnectionState.Error -> Pair(ErrorRed, "ERROR")
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = dotColor
                    )
                }
            }
        }

        // Metrics Grid (2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "PRODUCTS",
                        value = totalProducts.toString(),
                        accentColor = AccentBlue,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "SUBSCRIPTIONS",
                        value = totalSubs.toString(),
                        accentColor = AccentBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "TRIAL OFFERS",
                        value = totalTrials.toString(),
                        accentColor = PurpleTrial,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "DISCOUNTS",
                        value = totalDiscounts.toString(),
                        accentColor = WarningYellow,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.scanNow() },
                    enabled = !uiState.isScanning,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    if (uiState.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = DarkBackground,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SCANNING...", color = DarkBackground, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = DarkBackground)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("QUICK SCAN", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onNavigateToScanner,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CUSTOM SCAN", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Recent Scans Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT SCANS",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "VIEW ALL",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentBlue,
                    modifier = Modifier.clickable { onNavigateToHistory() }
                )
            }
        }

        // Recent Scans List
        if (recentScans.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Chưa có bản quét nào. Bấm 'QUICK SCAN' để kiểm tra.",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            items(recentScans.take(5)) { scan ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectScan(scan.id) },
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Scan #${scan.id}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatter.format(Date(scan.timestamp)),
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "${scan.totalProducts} prods",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "${scan.totalTrials} trials",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = PurpleTrial
                            )
                        }
                    }
                }
            }
        }
    }
}
