package com.adr.checkiap.ui.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adr.checkiap.data.local.ScanDao
import com.adr.checkiap.domain.analyzer.DiffAnalyzer
import com.adr.checkiap.domain.analyzer.DiffResult
import com.adr.checkiap.ui.theme.AccentBlue
import com.adr.checkiap.ui.theme.DarkBackground
import com.adr.checkiap.ui.theme.DarkBorder
import com.adr.checkiap.ui.theme.DarkSurface
import com.adr.checkiap.ui.theme.ErrorRed
import com.adr.checkiap.ui.theme.SuccessGreen
import com.adr.checkiap.ui.theme.TextMuted
import com.adr.checkiap.ui.theme.TextPrimary
import com.adr.checkiap.ui.theme.TextSecondary
import com.adr.checkiap.ui.theme.WarningYellow

@Composable
fun DiffScreen(
    scanId1: Long,
    scanId2: Long,
    scanDao: ScanDao,
    onBack: () -> Unit
) {
    var diffResult by remember { mutableStateOf<DiffResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(scanId1, scanId2) {
        val scan1 = scanDao.getScanById(scanId1)
        val scan2 = scanDao.getScanById(scanId2)
        if (scan1 != null && scan2 != null) {
            diffResult = DiffAnalyzer.compare(scan1.toSnapshot(), scan2.toSnapshot())
        }
        isLoading = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "DIFF ANALYZER",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "So sánh: Scan #$scanId1 ➔ Scan #$scanId2",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        }

        // Note on API response change vs hidden (Section 8 & 13)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "QUY TẮC HIỂN THỊ",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentBlue
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sự khác biệt phản ánh thay đổi phản hồi từ Google Play Billing API (do điều kiện tài khoản, cấu hình Play Console, hoặc vùng). Ứng dụng không suy đoán là offer 'bị ẩn'.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            }
        } else if (diffResult == null) {
            item {
                Text(text = "Không tìm thấy dữ liệu để so sánh.", color = ErrorRed)
            }
        } else {
            val res = diffResult!!

            // Summary changes
            if (res.summaryChanges.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WarningYellow.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = "TỔNG QUAN THAY ĐỔI", style = MaterialTheme.typography.labelSmall, color = WarningYellow)
                            Spacer(modifier = Modifier.height(6.dp))
                            res.summaryChanges.forEach { change ->
                                Text(text = "• $change", fontSize = 12.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            }

            // Added products
            if (res.addedProducts.isNotEmpty()) {
                item {
                    Text(text = "SẢN PHẨM MỚI XUẤT HIỆN (${res.addedProducts.size})", style = MaterialTheme.typography.titleMedium, color = SuccessGreen)
                }
                items(res.addedProducts) { prod ->
                    Text(text = "+ ${prod.productId} (${prod.productType})", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = SuccessGreen)
                }
            }

            // Removed products
            if (res.removedProducts.isNotEmpty()) {
                item {
                    Text(text = "SẢN PHẨM KHÔNG CÒN TRẢ VỀ (${res.removedProducts.size})", style = MaterialTheme.typography.titleMedium, color = ErrorRed)
                }
                items(res.removedProducts) { prod ->
                    Text(text = "- ${prod.productId}", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = ErrorRed)
                }
            }

            // Modified products
            if (res.modifiedProducts.isNotEmpty()) {
                item {
                    Text(text = "SẢN PHẨM CÓ THAY ĐỔI OFFER (${res.modifiedProducts.size})", style = MaterialTheme.typography.titleMedium, color = AccentBlue)
                }
                items(res.modifiedProducts) { detail ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = detail.productId, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            detail.addedOffers.forEach { added ->
                                Text(text = "+ Offer mới: $added", fontSize = 12.sp, color = SuccessGreen)
                            }
                            detail.removedOffers.forEach { removed ->
                                Text(text = "- Offer không còn trả về: $removed", fontSize = 12.sp, color = ErrorRed)
                            }
                            detail.notes.forEach { note ->
                                Text(text = "• $note", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            if (res.addedProducts.isEmpty() && res.removedProducts.isEmpty() && res.modifiedProducts.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(text = "Không có thay đổi nào giữa 2 bản quét.", color = TextMuted)
                    }
                }
            }
        }
    }
}
