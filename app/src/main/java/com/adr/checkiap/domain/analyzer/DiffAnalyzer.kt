package com.adr.checkiap.domain.analyzer

import com.adr.checkiap.data.model.ProductModel
import com.adr.checkiap.data.model.ScanSnapshot

data class DiffResult(
    val addedProducts: List<ProductModel>,
    val removedProducts: List<ProductModel>,
    val modifiedProducts: List<ProductDiffDetail>,
    val summaryChanges: List<String>
)

data class ProductDiffDetail(
    val productId: String,
    val addedOffers: List<String>,
    val removedOffers: List<String>,
    val notes: List<String>
)

object DiffAnalyzer {

    fun compare(older: ScanSnapshot, newer: ScanSnapshot): DiffResult {
        val oldMap = older.products.associateBy { it.productId }
        val newMap = newer.products.associateBy { it.productId }

        val addedProducts = newer.products.filter { it.productId !in oldMap }
        val removedProducts = older.products.filter { it.productId !in newMap }

        val modified = mutableListOf<ProductDiffDetail>()
        val summaryChanges = mutableListOf<String>()

        if (newer.totalTrials != older.totalTrials) {
            summaryChanges.add("Số lượng Trial thay đổi: ${older.totalTrials} -> ${newer.totalTrials}")
        }
        if (newer.totalDiscounts != older.totalDiscounts) {
            summaryChanges.add("Số lượng Offer ưu đãi thay đổi: ${older.totalDiscounts} -> ${newer.totalDiscounts}")
        }

        for ((productId, newProd) in newMap) {
            val oldProd = oldMap[productId] ?: continue

            val oldOfferIds = oldProd.offers.mapNotNull { it.offerId ?: it.basePlanId }.toSet()
            val newOfferIds = newProd.offers.mapNotNull { it.offerId ?: it.basePlanId }.toSet()

            val addedOffers = (newOfferIds - oldOfferIds).toList()
            val removedOffers = (oldOfferIds - newOfferIds).toList()

            val notes = mutableListOf<String>()
            if (oldProd.status != newProd.status) {
                notes.add("Trạng thái phản hồi: ${oldProd.status} -> ${newProd.status}")
            }

            if (addedOffers.isNotEmpty() || removedOffers.isNotEmpty() || notes.isNotEmpty()) {
                modified.add(
                    ProductDiffDetail(
                        productId = productId,
                        addedOffers = addedOffers,
                        removedOffers = removedOffers,
                        notes = notes
                    )
                )
            }
        }

        return DiffResult(
            addedProducts = addedProducts,
            removedProducts = removedProducts,
            modifiedProducts = modified,
            summaryChanges = summaryChanges
        )
    }
}
