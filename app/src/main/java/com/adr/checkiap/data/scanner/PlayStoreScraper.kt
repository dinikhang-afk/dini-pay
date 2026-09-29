package com.adr.checkiap.data.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Scrape public Play Store listing for IAP pricing info.
 * Google Play web shows "In-app purchases" range and subscription details.
 */
object PlayStoreScraper {

    data class StoreListingInfo(
        val appName: String = "",
        val developer: String = "",
        val iapPriceRange: String = "",        // e.g. "$0.99 - $99.99 per item"
        val containsAds: Boolean = false,
        val category: String = "",
        val rating: String = "",
        val installs: String = "",
        val subscriptionDetails: List<String> = emptyList(), // visible subscription info
        val rawSnippets: List<String> = emptyList()          // relevant text snippets
    )

    /**
     * Fetch public Play Store page and extract IAP-related info.
     */
    suspend fun fetchStoreInfo(packageName: String): StoreListingInfo {
        return withContext(Dispatchers.IO) {
            try {
                val html = fetchPage(
                    "https://play.google.com/store/apps/details?id=$packageName&hl=vi&gl=VN"
                )
                parseStorePage(html)
            } catch (e: Exception) {
                // Try English as fallback
                try {
                    val html = fetchPage(
                        "https://play.google.com/store/apps/details?id=$packageName&hl=en&gl=US"
                    )
                    parseStorePage(html)
                } catch (_: Exception) {
                    StoreListingInfo()
                }
            }
        }
    }

    private fun fetchPage(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent",
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        conn.setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000

        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parseStorePage(html: String): StoreListingInfo {
        val snippets = mutableListOf<String>()

        // Extract IAP price range - Google shows this in the app details
        val iapRange = extractIapRange(html)

        // Check for "Contains ads" / "In-app purchases"
        val containsAds = html.contains("Contains ads", ignoreCase = true) ||
                          html.contains("Chứa quảng cáo", ignoreCase = true)

        // Extract app name
        val appName = TITLE_PATTERN.find(html)?.groupValues?.getOrNull(1)?.decodeHtmlEntities() ?: ""

        // Extract developer
        val developer = DEVELOPER_PATTERN.find(html)?.groupValues?.getOrNull(1)?.decodeHtmlEntities() ?: ""

        // Extract category
        val category = CATEGORY_PATTERN.find(html)?.groupValues?.getOrNull(1)?.decodeHtmlEntities() ?: ""

        // Extract rating
        val rating = RATING_PATTERN.find(html)?.groupValues?.getOrNull(1) ?: ""

        // Extract install count
        val installs = INSTALLS_PATTERN.find(html)?.groupValues?.getOrNull(1)?.decodeHtmlEntities() ?: ""

        // Find subscription-related text (exclude review and update dates)
        val subDetails = mutableListOf<String>()
        SUBSCRIPTION_TEXT_PATTERNS.forEach { pattern ->
            pattern.findAll(html).forEach { match ->
                val text = match.groupValues.getOrNull(1)?.decodeHtmlEntities()?.trim()
                if (text != null && text.length in 5..200 && !DATE_PATTERN.containsMatchIn(text)) {
                    subDetails.add(text)
                }
            }
        }

        // Find pricing-related snippets in the page
        PRICE_SNIPPET_PATTERN.findAll(html).forEach { match ->
            val text = match.value.decodeHtmlEntities().trim()
            if (text.length in 3..100) snippets.add(text)
        }

        return StoreListingInfo(
            appName = appName,
            developer = developer,
            iapPriceRange = iapRange,
            containsAds = containsAds,
            category = category,
            rating = rating,
            installs = installs,
            subscriptionDetails = subDetails.distinct().take(20),
            rawSnippets = snippets.distinct().take(30)
        )
    }

    private fun extractIapRange(html: String): String {
        // Google Play shows IAP range like "$0.99 - $99.99 per item" or "₫22.000 - ₫4.499.000 mỗi mặt hàng"
        for (pattern in IAP_RANGE_PATTERNS) {
            pattern.find(html)?.let { match ->
                return match.groupValues.getOrNull(1)?.decodeHtmlEntities()?.trim()
                    ?: match.value.decodeHtmlEntities().trim()
            }
        }

        // Fallback: look for "In-app purchases" / "Mua hàng trong ứng dụng" text near price-like strings
        val iapIndex = html.indexOf("In-app purchases", ignoreCase = true)
            .takeIf { it >= 0 }
            ?: html.indexOf("Mua hàng trong ứng dụng", ignoreCase = true)
                .takeIf { it >= 0 }
            ?: html.indexOf("in-app", ignoreCase = true)
                .takeIf { it >= 0 }
            ?: return ""

        // Search nearby for price range
        val vicinity = html.substring(
            maxOf(0, iapIndex - 200),
            minOf(html.length, iapIndex + 500)
        )

        PRICE_RANGE_IN_VICINITY.find(vicinity)?.let {
            return it.value.decodeHtmlEntities().trim()
        }

        return "Có mua hàng trong ứng dụng"
    }

    private fun String.decodeHtmlEntities(): String {
        return this
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(HTML_TAG_PATTERN, "")
    }

    private val HTML_TAG_PATTERN = Regex("<[^>]+>")

    private val TITLE_PATTERN = Regex(
        """<meta\s+property="og:title"\s+content="([^"]+)"""",
        RegexOption.IGNORE_CASE
    )

    private val DEVELOPER_PATTERN = Regex(
        """<meta\s+property="og:site_name"\s+content="([^"]+)"""",
        RegexOption.IGNORE_CASE
    )

    private val CATEGORY_PATTERN = Regex(
        """<a[^>]*itemprop="genre"[^>]*>([^<]+)</a>""",
        RegexOption.IGNORE_CASE
    )

    private val RATING_PATTERN = Regex(
        """<meta[^>]*itemprop="ratingValue"[^>]*content="([^"]+)"""",
        RegexOption.IGNORE_CASE
    )

    private val INSTALLS_PATTERN = Regex(
        """>([\d,.]+ (?:downloads|lượt tải|lượt cài đặt))<""",
        RegexOption.IGNORE_CASE
    )

    private val IAP_RANGE_PATTERNS = listOf(
        Regex("""([\$€£¥₫₹][\d.,]+\s*[-–]\s*[\$€£¥₫₹][\d.,]+\s*(?:per item|mỗi mặt hàng|/item))""", RegexOption.IGNORE_CASE),
        Regex("""([\d.,]+\s*₫?\s*[-–]\s*[\d.,]+\s*₫?\s*mỗi mặt hàng)""", RegexOption.IGNORE_CASE),
        Regex("""(?:In-app purchases|Mua hàng trong ứng dụng)[^<]*?([\$€£¥₫₹][\d.,]+\s*[-–]\s*[\$€£¥₫₹][\d.,]+)""", RegexOption.IGNORE_CASE)
    )

    private val PRICE_RANGE_IN_VICINITY = Regex(
        """[\$€£¥₫₹][\d.,]+\s*[-–]\s*[\$€£¥₫₹][\d.,]+"""
    )

    private val DATE_PATTERN = Regex(
        """\d{1,2}\s+(?:tháng\s+\d{1,2}|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[,\s]+\d{4}""",
        RegexOption.IGNORE_CASE
    )

    private val SUBSCRIPTION_TEXT_PATTERNS = listOf(
        Regex(""">([^<]*(?:subscription|subscribe|trial|free trial|dùng thử|gói hội viên|đăng ký)[^<]*)<""", RegexOption.IGNORE_CASE),
        Regex(""">([^<]*(?:per month|per year|per week|/month|/year|/week|mỗi tháng|mỗi năm|mỗi tuần|hàng tháng|hàng năm|hàng tuần)[^<]*)<""", RegexOption.IGNORE_CASE),
        Regex(""">([^<]*(?:₫[\d.,]+|[\$€£][\d.,]+)\s*(?:/\s*|\s*mỗi\s*|\s*per\s*)(?:month|year|week|tháng|năm|tuần)[^<]*)<""", RegexOption.IGNORE_CASE)
    )

    private val PRICE_SNIPPET_PATTERN = Regex(
        """[\$€£¥₫₹]\s*[\d.,]+(?:\s*/\s*(?:month|year|week|tháng|năm|tuần))?""",
        RegexOption.IGNORE_CASE
    )
}
