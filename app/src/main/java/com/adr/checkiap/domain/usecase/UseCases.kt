package com.adr.checkiap.domain.usecase

import com.adr.checkiap.data.billing.BillingClientManager
import com.adr.checkiap.data.local.ScanDao
import com.adr.checkiap.data.local.ScanEntity
import com.adr.checkiap.data.model.OfferClassification
import com.adr.checkiap.data.model.ProductModel
import com.adr.checkiap.data.model.ProductType
import com.adr.checkiap.data.model.ScanSnapshot
import com.adr.checkiap.domain.analyzer.DiffAnalyzer
import com.adr.checkiap.domain.analyzer.DiffResult

class ScanProductsUseCase(
    private val billingClientManager: BillingClientManager,
    private val scanDao: ScanDao
) {
    suspend operator fun invoke(
        inAppIds: List<String>,
        subsIds: List<String>,
        collectedLogs: List<String>
    ): ScanSnapshot {
        val inAppResults = billingClientManager.queryProducts(inAppIds, ProductType.INAPP)
        val subsResults = billingClientManager.queryProducts(subsIds, ProductType.SUBS)

        val allProducts = inAppResults + subsResults

        val totalTrials = allProducts.sumOf { prod ->
            prod.offers.count { it.classification == OfferClassification.FREE_TRIAL }
        }

        val totalDiscounts = allProducts.sumOf { prod ->
            prod.offers.count { it.classification == OfferClassification.INTRO_DISCOUNT }
        }

        val snapshot = ScanSnapshot(
            timestamp = System.currentTimeMillis(),
            totalProducts = allProducts.size,
            totalSubs = subsResults.size,
            totalTrials = totalTrials,
            totalDiscounts = totalDiscounts,
            products = allProducts,
            logs = collectedLogs
        )

        val insertedId = scanDao.insertScan(ScanEntity.fromSnapshot(snapshot))
        return snapshot.copy(id = insertedId)
    }
}

class CompareScansUseCase(
    private val scanDao: ScanDao
) {
    suspend operator fun invoke(olderScanId: Long, newerScanId: Long): DiffResult? {
        val olderEntity = scanDao.getScanById(olderScanId) ?: return null
        val newerEntity = scanDao.getScanById(newerScanId) ?: return null
        return DiffAnalyzer.compare(olderEntity.toSnapshot(), newerEntity.toSnapshot())
    }
}
