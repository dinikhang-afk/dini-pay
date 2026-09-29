package com.adr.checkiap.domain.analyzer

import com.adr.checkiap.data.model.OfferClassification
import com.adr.checkiap.data.model.PricingPhaseModel

object OfferClassifier {

    fun classify(pricingPhases: List<PricingPhaseModel>): OfferClassification {
        if (pricingPhases.isEmpty()) return OfferClassification.UNKNOWN

        val firstPhase = pricingPhases.first()

        // 1. Free Trial: first phase price is 0
        if (firstPhase.priceAmountMicros == 0L) {
            return OfferClassification.FREE_TRIAL
        }

        // 2. Intro/Discount: first phase is cheaper than a following regular phase
        if (pricingPhases.size > 1) {
            val regularPhase = pricingPhases.drop(1).firstOrNull { it.priceAmountMicros > firstPhase.priceAmountMicros }
            if (regularPhase != null) {
                return OfferClassification.INTRO_DISCOUNT
            }
        }

        // 3. Regular
        return OfferClassification.REGULAR
    }

    fun buildSummary(
        classification: OfferClassification,
        pricingPhases: List<PricingPhaseModel>
    ): String {
        if (pricingPhases.isEmpty()) return "Không có thông tin giá"

        return when (classification) {
            OfferClassification.FREE_TRIAL -> {
                val trialPhase = pricingPhases.first()
                val readablePeriod = PricingAnalyzer.formatIsoPeriod(trialPhase.billingPeriod)
                val nextPhase = pricingPhases.getOrNull(1)
                if (nextPhase != null) {
                    val nextPeriod = PricingAnalyzer.formatIsoPeriod(nextPhase.billingPeriod)
                    "Dùng thử miễn phí $readablePeriod, sau đó ${nextPhase.formattedPrice} / $nextPeriod"
                } else {
                    "Dùng thử miễn phí $readablePeriod"
                }
            }
            OfferClassification.INTRO_DISCOUNT -> {
                val introPhase = pricingPhases.first()
                val introPeriod = PricingAnalyzer.formatIsoPeriod(introPhase.billingPeriod)
                val regularPhase = pricingPhases.getOrNull(1)
                if (regularPhase != null) {
                    val regularPeriod = PricingAnalyzer.formatIsoPeriod(regularPhase.billingPeriod)
                    "Ưu đãi ${introPhase.formattedPrice} / $introPeriod, sau đó ${regularPhase.formattedPrice} / $regularPeriod"
                } else {
                    "Giá ưu đãi: ${introPhase.formattedPrice} / $introPeriod"
                }
            }
            OfferClassification.REGULAR -> {
                val phase = pricingPhases.first()
                val period = PricingAnalyzer.formatIsoPeriod(phase.billingPeriod)
                "${phase.formattedPrice} / $period"
            }
            OfferClassification.UNKNOWN -> {
                pricingPhases.firstOrNull()?.formattedPrice ?: "Không rõ"
            }
        }
    }
}
