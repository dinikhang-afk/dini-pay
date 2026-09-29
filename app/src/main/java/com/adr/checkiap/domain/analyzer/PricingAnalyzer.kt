package com.adr.checkiap.domain.analyzer

import java.util.regex.Pattern

object PricingAnalyzer {

    /**
     * Converts ISO 8601 duration format (e.g., P1W, P7D, P1M, P3M, P1Y) into readable format.
     */
    fun formatIsoPeriod(isoPeriod: String): String {
        if (isoPeriod.isBlank()) return "Một lần"

        val pattern = Pattern.compile("P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?")
        val matcher = pattern.matcher(isoPeriod.uppercase())

        if (matcher.matches()) {
            val years = matcher.group(1)?.toIntOrNull() ?: 0
            val months = matcher.group(2)?.toIntOrNull() ?: 0
            val weeks = matcher.group(3)?.toIntOrNull() ?: 0
            val days = matcher.group(4)?.toIntOrNull() ?: 0

            return when {
                years > 0 -> if (years == 1) "1 năm" else "$years năm"
                months > 0 -> if (months == 1) "1 tháng" else "$months tháng"
                weeks > 0 -> if (weeks == 1) "1 tuần" else "$weeks tuần"
                days > 0 -> if (days == 1) "1 ngày" else "$days ngày"
                else -> isoPeriod
            }
        }

        return isoPeriod
    }

    /**
     * Calculate discount percentage between original price and discounted price.
     */
    fun calculateDiscountPercent(regularMicros: Long, discountedMicros: Long): Int {
        if (regularMicros <= 0 || discountedMicros >= regularMicros) return 0
        val diff = regularMicros - discountedMicros
        return ((diff.toDouble() / regularMicros.toDouble()) * 100).toInt()
    }
}
