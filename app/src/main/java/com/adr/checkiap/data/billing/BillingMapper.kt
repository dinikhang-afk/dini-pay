package com.adr.checkiap.data.billing

import com.android.billingclient.api.ProductDetails
import com.adr.checkiap.data.model.OfferModel
import com.adr.checkiap.data.model.PricingPhaseModel
import com.adr.checkiap.data.model.ProductModel
import com.adr.checkiap.data.model.ProductType
import com.adr.checkiap.data.model.ScanStatus
import com.adr.checkiap.domain.analyzer.OfferClassifier

object BillingMapper {

    fun mapToProductModel(productDetails: ProductDetails): ProductModel {
        val isSubscription = productDetails.productType == com.android.billingclient.api.BillingClient.ProductType.SUBS

        return if (isSubscription) {
            val offers = productDetails.subscriptionOfferDetails?.map { offerDetails ->
                val phases = offerDetails.pricingPhases.pricingPhaseList.map { phase ->
                    PricingPhaseModel(
                        priceAmountMicros = phase.priceAmountMicros,
                        formattedPrice = phase.formattedPrice,
                        priceCurrencyCode = phase.priceCurrencyCode,
                        billingPeriod = phase.billingPeriod,
                        recurrenceMode = phase.recurrenceMode,
                        billingCycleCount = phase.billingCycleCount
                    )
                }

                val classification = OfferClassifier.classify(phases)
                val summary = OfferClassifier.buildSummary(classification, phases)

                OfferModel(
                    offerId = offerDetails.offerId,
                    basePlanId = offerDetails.basePlanId,
                    offerToken = offerDetails.offerToken,
                    pricingPhases = phases,
                    offerTags = offerDetails.offerTags,
                    classification = classification,
                    summaryText = summary
                )
            } ?: emptyList()

            val basePrice = offers.firstOrNull()?.pricingPhases?.firstOrNull()?.formattedPrice ?: ""

            ProductModel(
                productId = productDetails.productId,
                productType = ProductType.SUBS,
                title = productDetails.title,
                description = productDetails.description,
                formattedBasePrice = basePrice,
                offers = offers,
                status = if (offers.isEmpty()) ScanStatus.NO_ELIGIBLE_OFFER else ScanStatus.FOUND
            )
        } else {
            val oneTime = productDetails.oneTimePurchaseOfferDetails
            val basePrice = oneTime?.formattedPrice ?: ""

            val pricingPhases = if (oneTime != null) {
                listOf(
                    PricingPhaseModel(
                        priceAmountMicros = oneTime.priceAmountMicros,
                        formattedPrice = oneTime.formattedPrice,
                        priceCurrencyCode = oneTime.priceCurrencyCode,
                        billingPeriod = "",
                        recurrenceMode = 0,
                        billingCycleCount = 1
                    )
                )
            } else emptyList()

            ProductModel(
                productId = productDetails.productId,
                productType = ProductType.INAPP,
                title = productDetails.title,
                description = productDetails.description,
                formattedBasePrice = basePrice,
                offers = listOf(
                    OfferModel(
                        offerId = null,
                        basePlanId = "one_time",
                        offerToken = "",
                        pricingPhases = pricingPhases,
                        offerTags = emptyList(),
                        classification = com.adr.checkiap.data.model.OfferClassification.REGULAR,
                        summaryText = basePrice
                    )
                ),
                status = ScanStatus.FOUND
            )
        }
    }
}
