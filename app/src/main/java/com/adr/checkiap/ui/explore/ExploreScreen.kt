package com.adr.checkiap.ui.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adr.checkiap.data.model.ProductModel
import com.adr.checkiap.data.model.ProductType
import com.adr.checkiap.ui.components.OfferBadge
import com.adr.checkiap.ui.components.StatusBadge
import com.adr.checkiap.ui.theme.AccentBlue
import com.adr.checkiap.ui.theme.DarkBackground
import com.adr.checkiap.ui.theme.DarkBorder
import com.adr.checkiap.ui.theme.DarkSurface
import com.adr.checkiap.ui.theme.ErrorRed
import com.adr.checkiap.ui.theme.TextMuted
import com.adr.checkiap.ui.theme.TextPrimary
import com.adr.checkiap.ui.theme.TextSecondary

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onProductSelected: (ProductModel) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "QUÉT IAP",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "Nhập Product ID thật để truy vấn Google Play Billing API",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        // Input card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = uiState.inputIds,
                        onValueChange = { viewModel.updateInput(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        placeholder = {
                            Text(
                                "Nhập mỗi dòng một Product ID\n\nVí dụ:\npremium_monthly\npremium_yearly\nremove_ads",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        },
                        shape = RoundedCornerShape(6.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.selectedType == null,
                            onClick = { viewModel.selectType(null) },
                            label = { Text("Tất cả") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                                selectedLabelColor = AccentBlue
                            )
                        )
                        FilterChip(
                            selected = uiState.selectedType == ProductType.SUBS,
                            onClick = { viewModel.selectType(ProductType.SUBS) },
                            label = { Text("Subscription") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                                selectedLabelColor = AccentBlue
                            )
                        )
                        FilterChip(
                            selected = uiState.selectedType == ProductType.INAPP,
                            onClick = { viewModel.selectType(ProductType.INAPP) },
                            label = { Text("In-App") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                                selectedLabelColor = AccentBlue
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.performScan() },
                        enabled = !uiState.isScanning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        if (uiState.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = DarkBackground,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ĐANG TRUY VẤN GOOGLE PLAY...", color = DarkBackground, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, tint = DarkBackground)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("QUÉT BILLING API", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!uiState.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = ErrorRed,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Results
        if (uiState.scannedProducts.isNotEmpty()) {
            item {
                Text(
                    text = "KẾT QUẢ (${uiState.scannedProducts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            items(uiState.scannedProducts) { product ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProductSelected(product) },
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = product.productId,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = product.title.ifBlank { product.productType.name },
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                            StatusBadge(status = product.status)
                        }

                        if (product.formattedBasePrice.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = product.formattedBasePrice,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentBlue
                            )
                        }

                        if (product.offers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                product.offers.take(4).forEach { offer ->
                                    OfferBadge(classification = offer.classification)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            product.offers.forEach { offer ->
                                Text(
                                    text = buildString {
                                        if (offer.offerId != null) append("${offer.offerId}: ")
                                        append(offer.summaryText)
                                    },
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        if (!product.errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = product.errorMessage ?: "",
                                fontSize = 11.sp,
                                color = ErrorRed
                            )
                        }
                    }
                }
            }
        } else if (!uiState.isScanning) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Chưa có kết quả",
                            color = TextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nhập Product ID rồi nhấn 'QUÉT BILLING API' để truy vấn thật",
                            color = TextMuted.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
