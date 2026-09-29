package com.adr.checkiap

import com.adr.checkiap.data.model.OfferClassification
import com.adr.checkiap.data.model.OfferModel
import com.adr.checkiap.data.model.PricingPhaseModel
import com.adr.checkiap.data.model.ProductModel
import com.adr.checkiap.data.model.ProductType
import com.adr.checkiap.data.model.ScanSnapshot
import com.adr.checkiap.data.model.ScanStatus
import com.adr.checkiap.domain.analyzer.DiffAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test

class DiffAnalyzerTest {

    @Test
    fun testDiffComparison() {
        val prodOld = ProductModel(
            productId = "premium_monthly",
            productType = ProductType.SUBS,
            title = "Monthly",
            description = "Desc",
            offers = listOf(
                OfferModel(
                    offerId = "trial_7d",
                    basePlanId = "monthly",
                    offerToken = "token1",
                    pricingPhases = emptyList(),
                    offerTags = emptyList(),
                    classification = OfferClassification.FREE_TRIAL,
                    summaryText = "Free trial 7 days"
                )
            ),
            status = ScanStatus.FOUND
        )

        val prodNew = ProductModel(
            productId = "premium_monthly",
            productType = ProductType.SUBS,
            title = "Monthly",
            description = "Desc",
            offers = listOf(
                OfferModel(
                    offerId = "trial_3d",
                    basePlanId = "monthly",
                    offerToken = "token2",
                    pricingPhases = emptyList(),
                    offerTags = emptyList(),
                    classification = OfferClassification.FREE_TRIAL,
                    summaryText = "Free trial 3 days"
                )
            ),
            status = ScanStatus.FOUND
        )

        val scanOld = ScanSnapshot(
            id = 1L,
            timestamp = 1000L,
            totalProducts = 1,
            totalSubs = 1,
            totalTrials = 1,
            totalDiscounts = 0,
            products = listOf(prodOld)
        )

        val scanNew = ScanSnapshot(
            id = 2L,
            timestamp = 2000L,
            totalProducts = 1,
            totalSubs = 1,
            totalTrials = 1,
            totalDiscounts = 0,
            products = listOf(prodNew)
        )

        val diff = DiffAnalyzer.compare(scanOld, scanNew)
        assertEquals(0, diff.addedProducts.size)
        assertEquals(0, diff.removedProducts.size)
        assertEquals(1, diff.modifiedProducts.size)

        val mod = diff.modifiedProducts[0]
        assertEquals("premium_monthly", mod.productId)
        assert(mod.addedOffers.contains("trial_3d"))
        assert(mod.removedOffers.contains("trial_7d"))
    }
}
