package com.adr.checkiap

import com.adr.checkiap.data.model.OfferClassification
import com.adr.checkiap.data.model.PricingPhaseModel
import com.adr.checkiap.domain.analyzer.OfferClassifier
import com.adr.checkiap.domain.analyzer.PricingAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test

class OfferClassifierTest {

    @Test
    fun testFreeTrialDetection() {
        val phases = listOf(
            PricingPhaseModel(
                priceAmountMicros = 0L,
                formattedPrice = "0 ₫",
                priceCurrencyCode = "VND",
                billingPeriod = "P7D",
                recurrenceMode = 2,
                billingCycleCount = 1
            ),
            PricingPhaseModel(
                priceAmountMicros = 199000000000L,
                formattedPrice = "199.000 ₫",
                priceCurrencyCode = "VND",
                billingPeriod = "P1M",
                recurrenceMode = 1,
                billingCycleCount = 0
            )
        )

        val classification = OfferClassifier.classify(phases)
        assertEquals(OfferClassification.FREE_TRIAL, classification)

        val summary = OfferClassifier.buildSummary(classification, phases)
        assert(summary.contains("Dùng thử miễn phí 7 ngày"))
    }

    @Test
    fun testIntroDiscountDetection() {
        val phases = listOf(
            PricingPhaseModel(
                priceAmountMicros = 99000000000L,
                formattedPrice = "99.000 ₫",
                priceCurrencyCode = "VND",
                billingPeriod = "P1M",
                recurrenceMode = 2,
                billingCycleCount = 1
            ),
            PricingPhaseModel(
                priceAmountMicros = 199000000000L,
                formattedPrice = "199.000 ₫",
                priceCurrencyCode = "VND",
                billingPeriod = "P1M",
                recurrenceMode = 1,
                billingCycleCount = 0
            )
        )

        val classification = OfferClassifier.classify(phases)
        assertEquals(OfferClassification.INTRO_DISCOUNT, classification)
    }

    @Test
    fun testRegularOfferDetection() {
        val phases = listOf(
            PricingPhaseModel(
                priceAmountMicros = 199000000000L,
                formattedPrice = "199.000 ₫",
                priceCurrencyCode = "VND",
                billingPeriod = "P1M",
                recurrenceMode = 1,
                billingCycleCount = 0
            )
        )

        val classification = OfferClassifier.classify(phases)
        assertEquals(OfferClassification.REGULAR, classification)
    }

    @Test
    fun testPeriodParsing() {
        assertEquals("7 ngày", PricingAnalyzer.formatIsoPeriod("P7D"))
        assertEquals("1 tuần", PricingAnalyzer.formatIsoPeriod("P1W"))
        assertEquals("1 tháng", PricingAnalyzer.formatIsoPeriod("P1M"))
        assertEquals("3 tháng", PricingAnalyzer.formatIsoPeriod("P3M"))
        assertEquals("1 năm", PricingAnalyzer.formatIsoPeriod("P1Y"))
    }
}
