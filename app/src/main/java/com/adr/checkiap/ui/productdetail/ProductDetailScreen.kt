package com.adr.checkiap.ui.productdetail

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adr.checkiap.data.billing.BillingClientManager
import com.adr.checkiap.data.model.ProductModel
import com.adr.checkiap.domain.analyzer.PricingAnalyzer
import com.adr.checkiap.ui.components.OfferBadge
import com.adr.checkiap.ui.components.StatusBadge
import com.adr.checkiap.ui.theme.AccentBlue
import com.adr.checkiap.ui.theme.DarkBackground
import com.adr.checkiap.ui.theme.DarkBorder
import com.adr.checkiap.ui.theme.DarkCard
import com.adr.checkiap.ui.theme.DarkSurface
import com.adr.checkiap.ui.theme.TextMuted
import com.adr.checkiap.ui.theme.TextPrimary
import com.adr.checkiap.ui.theme.TextSecondary

@Composable
fun ProductDetailScreen(
    product: ProductModel,
    billingClientManager: BillingClientManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PRODUCT DETAIL",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
        }

        // Basic Info Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = product.productId,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AccentBlue
                        )
                        StatusBadge(status = product.status)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "Loại sản phẩm: ${product.productType.name}", fontSize = 13.sp, color = TextSecondary)
                    Text(text = "Tên hiển thị: ${product.title.ifBlank { "—" }}", fontSize = 13.sp, color = TextSecondary)
                    Text(text = "Mô tả: ${product.description.ifBlank { "—" }}", fontSize = 13.sp, color = TextSecondary)
                }
            }
        }

        // Section Title: Offers & Base Plans
        item {
            Text(
                text = "OFFERS & BASE PLANS (${product.offers.size})",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }

        if (product.offers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Không có offer nào được Google Play trả về cho tài khoản này.",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(product.offers) { offer ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Base Plan: ${offer.basePlanId}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            OfferBadge(classification = offer.classification)
                        }

                        if (!offer.offerId.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Offer ID: ${offer.offerId}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = offer.summaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = DarkBorder)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Pricing phases breakdown table
                        Text(
                            text = "PRICING PHASES (${offer.pricingPhases.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        offer.pricingPhases.forEachIndexed { index, phase ->
                            val period = PricingAnalyzer.formatIsoPeriod(phase.billingPeriod)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Phase ${index + 1}: $period",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = phase.formattedPrice,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                            }
                        }

                        // Purchase test button (Section 10)
                        if (activity != null && offer.offerToken.isNotBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    billingClientManager.launchBillingFlow(
                                        activity = activity,
                                        productId = product.productId,
                                        selectedOfferToken = offer.offerToken
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("TEST PURCHASE (GOOGLE PLAY OFFICIAL)", fontSize = 11.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
