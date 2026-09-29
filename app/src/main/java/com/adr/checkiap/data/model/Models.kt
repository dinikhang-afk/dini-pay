package com.adr.checkiap.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProductType {
    INAPP,
    SUBS
}

@Serializable
enum class OfferClassification {
    FREE_TRIAL,
    INTRO_DISCOUNT,
    REGULAR,
    UNKNOWN
}

@Serializable
enum class ScanStatus {
    FOUND,
    NOT_FOUND,
    ERROR,
    UNSUPPORTED,
    NO_ELIGIBLE_OFFER
}

@Serializable
data class PricingPhaseModel(
    val priceAmountMicros: Long,
    val formattedPrice: String,
    val priceCurrencyCode: String,
    val billingPeriod: String, // e.g. P1W, P1M, P1Y
    val recurrenceMode: Int,
    val billingCycleCount: Int
)

@Serializable
data class OfferModel(
    val offerId: String?,
    val basePlanId: String,
    val offerToken: String,
    val pricingPhases: List<PricingPhaseModel>,
    val offerTags: List<String>,
    val classification: OfferClassification,
    val summaryText: String
)

@Serializable
data class ProductModel(
    val productId: String,
    val productType: ProductType,
    val title: String,
    val description: String,
    val formattedBasePrice: String = "",
    val offers: List<OfferModel> = emptyList(),
    val status: ScanStatus = ScanStatus.FOUND,
    val errorMessage: String? = null
)

@Serializable
data class ScanSnapshot(
    val id: Long = 0L,
    val timestamp: Long,
    val totalProducts: Int,
    val totalSubs: Int,
    val totalTrials: Int,
    val totalDiscounts: Int,
    val products: List<ProductModel>,
    val logs: List<String> = emptyList()
)
