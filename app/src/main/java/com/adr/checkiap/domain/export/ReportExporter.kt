package com.adr.checkiap.domain.export

import com.adr.checkiap.data.model.ScanSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun toMarkdown(snapshot: ScanSnapshot): String {
        val dateStr = dateFormatter.format(Date(snapshot.timestamp))
        val sb = StringBuilder()

        sb.appendLine("# ADR IAP Report")
        sb.appendLine()
        sb.appendLine("- **Thời gian quét:** $dateStr")
        sb.appendLine("- **Tổng sản phẩm:** ${snapshot.totalProducts}")
        sb.appendLine("- **Subscriptions:** ${snapshot.totalSubs}")
        sb.appendLine("- **Free Trials:** ${snapshot.totalTrials}")
        sb.appendLine("- **Intro/Discounts:** ${snapshot.totalDiscounts}")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        for (product in snapshot.products) {
            sb.appendLine("## Product: `${product.productId}`")
            sb.appendLine("- **Loại:** ${product.productType}")
            sb.appendLine("- **Tên hiển thị:** ${product.title}")
            sb.appendLine("- **Trạng thái API:** ${product.status}")
            if (!product.errorMessage.isNullOrBlank()) {
                sb.appendLine("- **Lỗi:** ${product.errorMessage}")
            }
            sb.appendLine()

            if (product.offers.isNotEmpty()) {
                sb.appendLine("### Offers & Base Plans")
                sb.appendLine("| Base Plan | Offer ID | Loại | Chi tiết giá |")
                sb.appendLine("|---|---|---|---|")
                for (offer in product.offers) {
                    val offerId = offer.offerId ?: "—"
                    val type = offer.classification.name
                    val priceSummary = offer.summaryText.replace("|", "/")
                    sb.appendLine("| `${offer.basePlanId}` | `$offerId` | $type | $priceSummary |")
                }
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    fun toJson(snapshot: ScanSnapshot): String {
        val json = Json { prettyPrint = true }
        return json.encodeToString(snapshot)
    }

    fun toCsv(snapshot: ScanSnapshot): String {
        val sb = StringBuilder()
        sb.appendLine("ProductId,ProductType,Title,Status,BasePlanId,OfferId,Classification,PriceSummary")

        for (product in snapshot.products) {
            if (product.offers.isEmpty()) {
                sb.appendLine(
                    "\"${product.productId}\",\"${product.productType}\",\"${product.title}\",\"${product.status}\",\"\",\"\",\"\",\"\""
                )
            } else {
                for (offer in product.offers) {
                    val offerId = offer.offerId ?: ""
                    sb.appendLine(
                        "\"${product.productId}\",\"${product.productType}\",\"${product.title}\",\"${product.status}\",\"${offer.basePlanId}\",\"$offerId\",\"${offer.classification}\",\"${offer.summaryText}\""
                    )
                }
            }
        }

        return sb.toString()
    }
}
