package com.adr.checkiap.data.scanner

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

class ApkAnalyzer(private val context: Context) {

    data class ExtractedIap(
        val productId: String,
        val confidence: Confidence,
        val category: IapCategory,
        val trialInfo: String? = null,      // e.g. "7 days free", "3-day trial"
        val pricingHint: String? = null,     // e.g. "$9.99/month", "₫249.000"
        val periodHint: String? = null,      // e.g. "monthly", "P1M", "yearly"
        val groupName: String? = null,       // e.g. "IT Membership", "Premium Plan"
        val isHidden: Boolean = false        // appears in code but flagged as hidden/internal
    )

    data class AppIapSummary(
        val packageName: String,
        val products: List<ExtractedIap>,
        val subscriptionGroups: List<SubscriptionGroup>,
        val rawTrialStrings: List<String>,
        val rawPricingStrings: List<String>,
        val hasBillingCode: Boolean
    )

    data class SubscriptionGroup(
        val name: String,
        val productIds: List<String>
    )

    enum class Confidence { HIGH, MEDIUM, LOW }

    enum class IapCategory {
        SUBSCRIPTION,
        ONE_TIME,
        CONSUMABLE,
        UNKNOWN
    }

    /**
     * Full analysis: extract product IDs + trial/pricing context + subscription groups.
     */
    fun analyzeApp(packageName: String): AppIapSummary {
        val apkPaths = getAllApkPaths(packageName)
        if (apkPaths.isEmpty()) return emptyResult(packageName)

        val allStrings = mutableListOf<String>()
        val resourceStrings = mutableListOf<String>()

        for (path in apkPaths) {
            allStrings.addAll(extractDexStrings(path))
            resourceStrings.addAll(extractResourceStrings(path))
        }
        val combined = allStrings + resourceStrings

        val hasBilling = hasBillingCode(combined)
        val trialStrings = findTrialStrings(combined)
        val pricingStrings = findPricingStrings(combined)
        val groups = findSubscriptionGroups(combined)
        val products = detectProductIds(combined, hasBilling, trialStrings, pricingStrings, groups)

        return AppIapSummary(
            packageName = packageName,
            products = products,
            subscriptionGroups = groups,
            rawTrialStrings = trialStrings,
            rawPricingStrings = pricingStrings,
            hasBillingCode = hasBilling
        )
    }

    // Legacy method for compatibility
    fun extractProductIds(packageName: String): List<ExtractedIap> {
        return analyzeApp(packageName).products
    }

    private fun emptyResult(pkg: String) = AppIapSummary(
        pkg, emptyList(), emptyList(), emptyList(), emptyList(), false
    )

    private fun getAllApkPaths(packageName: String): List<String> {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val list = mutableListOf<String>()
            appInfo.sourceDir?.let { list.add(it) }
            appInfo.splitSourceDirs?.forEach { split ->
                if (!split.isNullOrBlank()) list.add(split)
            }
            list
        } catch (_: Exception) { emptyList() }
    }

    // ── DEX string extraction ──────────────────────────────────────────

    private fun extractDexStrings(apkPath: String): List<String> {
        val strings = mutableListOf<String>()
        try {
            ZipFile(apkPath).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.endsWith(".dex") }
                    .forEach { entry ->
                        try {
                            val bytes = zip.getInputStream(entry).use { it.readBytes() }
                            strings.addAll(parseDexStrings(bytes))
                        } catch (_: Exception) { }
                    }
            }
        } catch (_: Exception) { }
        return strings
    }

    private fun parseDexStrings(dexBytes: ByteArray): List<String> {
        if (dexBytes.size < 112) return emptyList()
        val buf = ByteBuffer.wrap(dexBytes).order(ByteOrder.LITTLE_ENDIAN)
        if (!String(dexBytes, 0, 4).startsWith("dex")) return emptyList()

        val stringIdsSize = buf.getInt(68)
        val stringIdsOff = buf.getInt(72)
        if (stringIdsSize <= 0 || stringIdsOff < 0) return emptyList()

        val cap = minOf(stringIdsSize, 300_000)
        val result = mutableListOf<String>()

        for (i in 0 until cap) {
            try {
                val off = buf.getInt(stringIdsOff + i * 4)
                if (off < 0 || off >= dexBytes.size) continue
                var pos = off
                while (pos < dexBytes.size && (dexBytes[pos].toInt() and 0x80) != 0) pos++
                pos++
                val start = pos
                while (pos < dexBytes.size && dexBytes[pos] != 0.toByte()) pos++
                val len = pos - start
                if (len in 3..70) {
                    val str = String(dexBytes, start, len, Charsets.UTF_8)
                    if (isRelevantString(str)) {
                        result.add(str)
                    }
                }
            } catch (_: Exception) { continue }
        }
        return result
    }

    private fun isRelevantString(s: String): Boolean {
        val lower = s.lowercase()
        return isBillingApiRef(s) ||
               isProductIdCandidate(s) ||
               lower.contains("trial") ||
               lower.contains("sub_") ||
               lower.contains("sub.") ||
               lower.contains("pro_") ||
               lower.contains("vip_") ||
               lower.contains("subscription") ||
               lower.contains("yearly") ||
               lower.contains("monthly") ||
               lower.contains("annual") ||
               lower.contains("membership")
    }

    // ── Resource string extraction ─────────────────────────────────────

    private fun extractResourceStrings(apkPath: String): List<String> {
        // Extract strings from resources.arsc XML string entries
        val strings = mutableListOf<String>()
        try {
            ZipFile(apkPath).use { zip ->
                // Try reading strings from XML resources
                zip.entries().asSequence()
                    .filter { e ->
                        e.name.startsWith("res/values") && e.name.endsWith(".xml") ||
                        e.name == "res/values/strings.xml"
                    }
                    .forEach { entry ->
                        try {
                            val content = zip.getInputStream(entry).use {
                                String(it.readBytes(), Charsets.UTF_8)
                            }
                            // Simple regex to pull string values from compiled/text XML
                            STRING_VALUE_PATTERN.findAll(content).forEach { match ->
                                strings.add(match.groupValues[1])
                            }
                        } catch (_: Exception) { }
                    }
            }
        } catch (_: Exception) { }
        return strings
    }

    // ── Trial/pricing/group detection ──────────────────────────────────

    private fun hasBillingCode(strings: List<String>): Boolean {
        return strings.any { s ->
            s == "com.android.vending.billing.InAppBillingService.BIND" ||
            s.contains("BillingClient") ||
            s.contains("queryProductDetails") ||
            s.contains("querySkuDetails") ||
            s.contains("launchBillingFlow") ||
            s.contains("com.android.billingclient") ||
            s.contains("InAppBillingService")
        }
    }

    /**
     * Find strings that describe trial periods.
     */
    private fun findTrialStrings(strings: List<String>): List<String> {
        return strings.filter { s ->
            val lower = s.lowercase()
            TRIAL_PATTERNS.any { pattern -> pattern.containsMatchIn(lower) }
        }.distinct().take(50)
    }

    /**
     * Find strings that look like pricing.
     */
    private fun findPricingStrings(strings: List<String>): List<String> {
        return strings.filter { s ->
            PRICING_PATTERN.containsMatchIn(s)
        }.distinct().take(100)
    }

    /**
     * Find subscription group / membership names.
     */
    private fun findSubscriptionGroups(strings: List<String>): List<SubscriptionGroup> {
        val groupNames = strings.filter { s ->
            val lower = s.lowercase()
            val isGroupTerm = lower.contains("subscription") || lower.contains("membership") ||
                Regex("""\b(pro plan|vip plan|premium plan|basic plan|starter plan|pricing plan)\b""").containsMatchIn(lower)

            val isBlacklisted = lower.contains("plane") || lower.contains("planar") ||
                lower.contains("focal") || lower.contains("resolution") ||
                lower.contains("exif") || lower.contains("tag_") ||
                lower.contains("format_") || lower.contains("matrix") ||
                lower.contains("camera") || lower.contains("sensor") ||
                lower.contains("buffer") || lower.contains("gl_") ||
                lower.contains("dimension") || lower.contains("unit") ||
                lower.contains("critical")

            isGroupTerm && !isBlacklisted &&
            s.length in 5..60 &&
            !s.contains("/") && !s.contains("://") &&
            !s.startsWith("com.") && !s.startsWith("android.") &&
            s.any { it.isLetter() }
        }.distinct().take(10)

        return groupNames.map { SubscriptionGroup(name = it, productIds = emptyList()) }
    }

    // ── Product ID detection ───────────────────────────────────────────

    private fun detectProductIds(
        allStrings: List<String>,
        hasBilling: Boolean,
        trialStrings: List<String>,
        pricingStrings: List<String>,
        groups: List<SubscriptionGroup>
    ): List<ExtractedIap> {
        // Build billing adjacency map
        val billingAdjacentStrings = mutableSetOf<String>()
        for ((idx, s) in allStrings.withIndex()) {
            if (isBillingApiRef(s)) {
                for (j in maxOf(0, idx - 30)..minOf(allStrings.size - 1, idx + 30)) {
                    billingAdjacentStrings.add(allStrings[j])
                }
            }
        }

        val candidates = mutableMapOf<String, ExtractedIap>()

        for ((idx, s) in allStrings.withIndex()) {
            val trimmed = s.trim()
            if (!isProductIdCandidate(trimmed)) continue

            val isBillingAdjacent = trimmed in billingAdjacentStrings
            val confidence = assessConfidence(trimmed, hasBilling, isBillingAdjacent) ?: continue
            val category = categorize(trimmed)

            // Look for trial/pricing context near this string
            val nearbyStrings = allStrings.subList(
                maxOf(0, idx - 15),
                minOf(allStrings.size, idx + 15)
            )

            val trialInfo = findTrialContext(trimmed, nearbyStrings)
            val pricingHint = findPricingContext(nearbyStrings)
            val periodHint = extractPeriod(trimmed, nearbyStrings)
            val groupName = findGroupContext(nearbyStrings, groups)
            val isHidden = detectHiddenFlag(trimmed, nearbyStrings)

            val existing = candidates[trimmed]
            if (existing == null || confidence.ordinal < existing.confidence.ordinal) {
                candidates[trimmed] = ExtractedIap(
                    productId = trimmed,
                    confidence = confidence,
                    category = category,
                    trialInfo = trialInfo,
                    pricingHint = pricingHint,
                    periodHint = periodHint,
                    groupName = groupName,
                    isHidden = isHidden
                )
            }
        }

        return candidates.values
            .sortedWith(compareBy({ it.confidence.ordinal }, { it.productId }))
            .toList()
    }

    private fun isBillingApiRef(s: String): Boolean {
        return s.contains("ProductDetails") || s.contains("SkuDetails") ||
               s.contains("setProductId") || s.contains("setSku") ||
               s.contains("product_id") || s.contains("productId") ||
               s.contains("BillingFlowParams") || s.contains("SubscriptionOfferDetails") ||
               s.contains("queryProductDetailsAsync") || s.contains("querySkuDetailsAsync") ||
               s.contains("launchBillingFlow") ||
               s.contains("offerToken") || s.contains("offer_token") ||
               s.contains("basePlanId") || s.contains("base_plan_id")
    }

    private fun isProductIdCandidate(s: String): Boolean {
        if (s.length < 3 || s.length > 60) return false
        if (!STRICT_SKU_PATTERN.matches(s)) return false
        if (isFrameworkJunk(s)) return false
        if (!containsIapKeyword(s)) return false
        return true
    }

    private fun isFrameworkJunk(s: String): Boolean {
        for (prefix in FRAMEWORK_PREFIXES) {
            if (s.startsWith(prefix)) return true
        }
        for (pattern in GARBAGE_SUBSTRINGS) {
            if (s.contains(pattern)) return true
        }
        return false
    }

    private fun assessConfidence(s: String, hasBilling: Boolean, billingAdjacent: Boolean): Confidence? {
        val keywordCount = countIapKeywords(s)
        return when {
            hasBilling && billingAdjacent && keywordCount >= 1 -> Confidence.HIGH
            hasBilling && keywordCount >= 2 -> Confidence.HIGH
            hasBilling && keywordCount >= 1 -> Confidence.MEDIUM
            billingAdjacent && keywordCount >= 1 -> Confidence.MEDIUM
            keywordCount >= 2 -> Confidence.LOW
            keywordCount >= 1 && s.length >= 6 -> Confidence.LOW
            else -> null
        }
    }

    private fun categorize(s: String): IapCategory {
        val parts = s.lowercase().split('_', '.', '-')
        return when {
            parts.any { it in SUBSCRIPTION_WORDS } -> IapCategory.SUBSCRIPTION
            parts.any { it in CONSUMABLE_WORDS } -> IapCategory.CONSUMABLE
            parts.any { it in ONE_TIME_WORDS } -> IapCategory.ONE_TIME
            else -> IapCategory.UNKNOWN
        }
    }

    private fun containsIapKeyword(s: String): Boolean {
        return s.lowercase().split('_', '.', '-').any { it in ALL_IAP_WORDS }
    }

    private fun countIapKeywords(s: String): Int {
        return s.lowercase().split('_', '.', '-').count { it in ALL_IAP_WORDS }
    }

    // ── Context extraction ─────────────────────────────────────────────

    private fun findTrialContext(productId: String, nearby: List<String>): String? {
        val lower = productId.lowercase()

        // Check product ID itself for trial hints
        if (lower.contains("trial")) {
            TRIAL_DURATION_IN_ID.find(lower)?.let { m ->
                return "${m.groupValues[1]} ${m.groupValues[2]} free"
            }
            return "free trial"
        }

        // Check nearby strings for trial descriptions
        for (s in nearby) {
            val sl = s.lowercase()
            for (pattern in TRIAL_PATTERNS) {
                pattern.find(sl)?.let { return s.trim().take(60) }
            }
        }
        return null
    }

    private fun findPricingContext(nearby: List<String>): String? {
        for (s in nearby) {
            PRICING_PATTERN.find(s)?.let {
                val matched = s.trim()
                if (matched.length in 3..40) return matched
            }
        }
        return null
    }

    private fun extractPeriod(productId: String, nearby: List<String>): String? {
        val lower = productId.lowercase()
        val parts = lower.split('_', '.', '-')

        // Check product ID itself
        for (part in parts) {
            when (part) {
                "monthly", "month", "m", "p1m" -> return "1 tháng"
                "yearly", "year", "annual", "y", "p1y", "p12m" -> return "1 năm"
                "weekly", "week", "w", "p1w" -> return "1 tuần"
                "quarterly", "quarter", "p3m" -> return "3 tháng"
                "p6m" -> return "6 tháng"
            }
        }

        // ISO 8601 duration in nearby strings
        for (s in nearby) {
            ISO_PERIOD_PATTERN.find(s)?.let { m ->
                return formatIsoPeriod(m.value)
            }
        }
        return null
    }

    private fun findGroupContext(nearby: List<String>, groups: List<SubscriptionGroup>): String? {
        // Check nearby strings for group/membership names
        for (s in nearby) {
            val lower = s.lowercase()
            if ((lower.contains("membership") || lower.contains("subscription") ||
                 lower.contains("vip") || lower.contains("premium")) &&
                !lower.contains("plane") && !lower.contains("planar") &&
                s.length in 5..60 &&
                !s.contains("/") && !s.contains("://") &&
                !s.startsWith("com.") && !s.startsWith("android.") &&
                s.any { it.isUpperCase() || it == ' ' }) {
                return s.trim()
            }
        }
        return null
    }

    private fun detectHiddenFlag(productId: String, nearby: List<String>): Boolean {
        val lower = productId.lowercase()
        if (lower.contains("hidden") || lower.contains("internal") ||
            lower.contains("debug") || lower.contains("test")) return true

        for (s in nearby) {
            val sl = s.lowercase()
            if (sl == "hidden" || sl == "is_hidden" || sl == "ishidden" ||
                sl.contains("visibility") || sl == "internal" ||
                sl.contains("not_visible") || sl.contains("hidden_offer")) {
                return true
            }
        }
        return false
    }

    private fun formatIsoPeriod(iso: String): String {
        return when (iso.uppercase()) {
            "P7D", "P1W" -> "1 tuần"
            "P3D" -> "3 ngày"
            "P1M" -> "1 tháng"
            "P3M" -> "3 tháng"
            "P6M" -> "6 tháng"
            "P1Y", "P12M" -> "1 năm"
            else -> iso
        }
    }

    companion object {
        private val STRICT_SKU_PATTERN = Regex("^[a-z][a-z0-9._\\-]{2,59}$")

        private val TRIAL_PATTERNS = listOf(
            Regex("""(\d+)\s*(?:days?|ngày)\s*(?:free|miễn phí|trial)"""),
            Regex("""free\s*(?:for\s*)?(\d+)\s*(?:days?|ngày)"""),
            Regex("""(\d+)\s*(?:days?|ngày)\s*(?:dùng thử|thử)"""),
            Regex("""free\s*trial"""),
            Regex("""trial\s*period"""),
            Regex("""dùng thử"""),
            Regex("""miễn phí.*(?:ngày|tuần|tháng)"""),
            Regex("""(?:try|trial).*(?:free|days)""")
        )

        private val TRIAL_DURATION_IN_ID = Regex("""(?:trial|free)[_.-]?(\d+)[_.-]?(d|day|days|w|week|m|month)""")

        private val PRICING_PATTERN = Regex("""(?:[\$€£¥₫₹]\s*\d[\d.,]*|\d[\d.,]*\s*(?:đ|₫|VND|USD|EUR|GBP)|\d{1,3}(?:[.,]\d{3})*\s*(?:đ|₫))""")

        private val ISO_PERIOD_PATTERN = Regex("""P(?:\d+[DWMY])+""", RegexOption.IGNORE_CASE)

        private val STRING_VALUE_PATTERN = Regex(""">([^<]+)<""")

        private val FRAMEWORK_PREFIXES = setOf(
            "android.", "androidx.", "com.google.", "com.android.",
            "google_", "googleg_", "google_analytics_", "google_auth_", "ga_",
            "gms_", "firebase_", "fcm_", "fiam_", "crashlytics_", "analytics_",
            "ads_", "admob_", "webkit_", "webview_", "measurement_",
            "base_", "abc_", "compat_", "action_", "actionbar_", "actionmode_",
            "widget_", "design_", "material_", "textappearance", "textinputlayout",
            "preference_", "dialog_", "alert_", "notification_", "status_bar_",
            "navigation_", "toolbar_", "tab_", "search_", "menu_", "popup_",
            "config_", "common_", "cast_", "media_route_",
            "mtrl_", "m3_", "exo_", "glide_",
            "test_", "debug_", "client_msg_", "bundle_key_", "bundle_value_",
            "application_", "content_", "ic_", "img_", "bg_", "btn_",
            "msg_", "err_", "error_", "key_", "pref_", "tag_",
            "grant_", "permission_", "ssl_", "tls_", "http_", "https_",
            "sql_", "db_", "auth_", "oauth_", "log_", "logger_",
            "access_token", "account_", "session_", "service_token",
            "allow_ad_", "app_store_", "play_store_"
        )

        private val GARBAGE_SUBSTRINGS = setOf(
            "appcompat", "textappearance", "textstyle", "layout_", "_layout",
            "toolbar", "actionbar", "drawable", "dimen", "styleable",
            "coordinator", "recyclerview", "viewmodel", "livedata", "lifecycle",
            "fragment", "broadcast", "receiver", "serializer", "deserializer",
            "exception", "throwable", "listener", "callback", "handler",
            "adapter", "holder", "cursor", "loader", "factory", "builder",
            "helper", "util", "utils", "compat", "delegate", "impl_", "_impl",
            "internal", "proto_", "protobuf", "grpc_", "okhttp_", "retrofit",
            "gson_", "glide_", "picasso", "room_", "migration_", "crypto_",
            "cipher", "kotlin.", "kotlinx.", "coroutine", "dispatcher",
            "subtitle", "plane", "planar", "focal", "resolution", "exif",
            "token", "analytics", "measurement"
        )

        private val SUBSCRIPTION_WORDS = setOf(
            "subscription", "subscribe", "sub", "subs",
            "monthly", "yearly", "weekly", "annual", "quarterly",
            "month", "year", "week",
            "p1m", "p1y", "p1w", "p3m", "p6m", "p12m",
            "membership", "tier",
            "recurring",
            "trial", "introductory", "intro",
            "baseplan"
        )

        private val CONSUMABLE_WORDS = setOf(
            "coin", "coins", "gem", "gems", "diamond", "diamonds",
            "crystal", "crystals", "credit", "credits",
            "gold", "silver", "ruby",
            "energy", "lives",
            "booster", "bundle", "chest", "crate"
        )

        private val ONE_TIME_WORDS = setOf(
            "premium", "pro", "vip", "elite",
            "remove_ads", "removeads", "noads", "no_ads", "adfree", "ad_free",
            "unlock", "fullversion",
            "lifetime", "forever", "permanent", "onetime",
            "donate", "donation"
        )

        private val ALL_IAP_WORDS = (SUBSCRIPTION_WORDS + CONSUMABLE_WORDS + ONE_TIME_WORDS)
    }
}
