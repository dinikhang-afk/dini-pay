package com.adr.checkiap.ui.explore

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adr.checkiap.data.billing.BillingClientManager
import com.adr.checkiap.data.model.OfferClassification
import com.adr.checkiap.data.model.OfferModel
import com.adr.checkiap.data.model.ProductModel
import com.adr.checkiap.data.model.ScanStatus
import com.adr.checkiap.ui.components.OfferBadge
import com.adr.checkiap.ui.components.StatusBadge
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

@Composable
fun ProductDetailScreen(
    product: ProductModel,
    billingManager: BillingClientManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
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
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CHI TIẾT SẢN PHẨM",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Dữ liệu từ Google Play Billing API",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                StatusBadge(status = product.status)
            }
        }

        // Product Info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow("Product ID", product.productId)
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow("Loại", product.productType.name)
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow("Tên", product.title)
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow("Mô tả", product.description)
                    if (product.formattedBasePrice.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoRow("Giá cơ bản", product.formattedBasePrice)
                    }
                }
            }
        }

        // Offers section
        if (product.offers.isNotEmpty()) {
            item {
                Text(
                    text = "OFFERS (${product.offers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            items(product.offers) { offer ->
                OfferCard(
                    offer = offer,
                    productId = product.productId,
                    canPurchase = product.status == ScanStatus.FOUND && activity != null,
                    onPurchase = {
                        if (activity != null) {
                            billingManager.launchBillingFlow(
                                activity = activity,
                                productId = product.productId,
                                selectedOfferToken = offer.offerToken.ifBlank { null }
                            )
                        }
                    }
                )
            }
        }

        // Error
        if (!product.errorMessage.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1515)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF85149).copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = product.errorMessage ?: "",
                        modifier = Modifier.padding(14.dp),
                        color = Color(0xFFF85149),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferCard(
    offer: OfferModel,
    productId: String,
    canPurchase: Boolean,
    onPurchase: () -> Unit
) {
    val borderColor = when (offer.classification) {
        OfferClassification.FREE_TRIAL -> PurpleTrial
        OfferClassification.INTRO_DISCOUNT -> WarningYellow
        OfferClassification.REGULAR -> DarkBorder
        OfferClassification.UNKNOWN -> DarkBorder
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OfferBadge(classification = offer.classification)
                if (offer.offerId != null) {
                    Text(
                        text = offer.offerId,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = AccentBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            InfoRow("Base Plan ID", offer.basePlanId)

            if (offer.offerTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                InfoRow("Tags", offer.offerTags.joinToString(", "))
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = offer.summaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            // Pricing phases
            if (offer.pricingPhases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "PRICING PHASES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))

                offer.pricingPhases.forEachIndexed { index, phase ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkCard)
                            .border(0.5.dp, DarkBorder, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "Phase ${index + 1}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentBlue
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = phase.formattedPrice,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (phase.priceAmountMicros == 0L) SuccessGreen else TextPrimary
                                )
                                Text(
                                    text = phase.billingPeriod.ifBlank { "one-time" },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            if (phase.billingCycleCount > 0) {
                                Text(
                                    text = "${phase.billingCycleCount} cycle(s) • recurrence=${phase.recurrenceMode}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                    if (index < offer.pricingPhases.size - 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            // Purchase button
            if (canPurchase) {
                Spacer(modifier = Modifier.height(12.dp))

                val buttonColor = when (offer.classification) {
                    OfferClassification.FREE_TRIAL -> SuccessGreen
                    OfferClassification.INTRO_DISCOUNT -> WarningYellow
                    else -> AccentBlue
                }

                val buttonText = when (offer.classification) {
                    OfferClassification.FREE_TRIAL -> "DÙNG THỬ MIỄN PHÍ"
                    OfferClassification.INTRO_DISCOUNT -> "MUA VỚI GIÁ ƯU ĐÃI"
                    else -> {
                        val price = offer.pricingPhases.lastOrNull()?.formattedPrice ?: ""
                        if (price.isNotBlank()) "MUA $price" else "MUA NGAY"
                    }
                }

                Button(
                    onClick = onPurchase,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = DarkBackground,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buttonText,
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextMuted,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}
