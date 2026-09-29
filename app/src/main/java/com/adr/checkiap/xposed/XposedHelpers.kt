package com.adr.checkiap.xposed

import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Holds reference to the target app's BillingClient instance and classloader.
 * Used to trigger launchBillingFlow from within the target app's process context.
 */
object BillingClientHolder {
    var client: Any? = null
    var classLoader: ClassLoader? = null

    fun launchBillingFlow(activity: Any, productDetails: Any, offerToken: String?): Boolean {
        val cl = classLoader ?: return false
        val billingClient = client ?: return false

        if (activity is android.app.Activity) {
            if (activity.isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) {
                XposedBridge.log("[${IapHookModule.TAG}] launchBillingFlow failed: Activity is finishing or destroyed")
                return false
            }
        }

        return try {
            val pdpBuilder = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass(
                    "com.android.billingclient.api.BillingFlowParams\$ProductDetailsParams", cl
                ),
                "newBuilder"
            )
            XposedHelpers.callMethod(pdpBuilder, "setProductDetails", productDetails)

            // Subscription products require an offer token in Billing v5+
            val tokenToUse = if (!offerToken.isNullOrBlank()) {
                offerToken
            } else {
                extractBestOfferToken(productDetails)
            }

            if (!tokenToUse.isNullOrBlank()) {
                XposedHelpers.callMethod(pdpBuilder, "setOfferToken", tokenToUse)
            }

            val pdp = XposedHelpers.callMethod(pdpBuilder, "build")

            val flowBuilder = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass(
                    "com.android.billingclient.api.BillingFlowParams", cl
                ),
                "newBuilder"
            )
            XposedHelpers.callMethod(flowBuilder, "setProductDetailsParamsList", listOf(pdp))
            val flowParams = XposedHelpers.callMethod(flowBuilder, "build")

            val result = XposedHelpers.callMethod(billingClient, "launchBillingFlow", activity, flowParams)
            val code = XposedHelpers.callMethod(result, "getResponseCode") as Int
            XposedBridge.log("[${IapHookModule.TAG}] launchBillingFlow result: $code")
            code == 0 // BillingResponseCode.OK
        } catch (e: Throwable) {
            XposedBridge.log("[${IapHookModule.TAG}] launchBillingFlow failed: ${e.message}")
            false
        }
    }

    private fun extractBestOfferToken(productDetails: Any): String? {
        try {
            val offers = XposedHelpers.callMethod(productDetails, "getSubscriptionOfferDetails") as? List<*>
            if (offers.isNullOrEmpty()) return null

            // Prioritize free trial (priceAmountMicros == 0)
            for (offer in offers) {
                offer ?: continue
                val phases = XposedHelpers.callMethod(offer, "getPricingPhases")
                val phaseList = XposedHelpers.callMethod(phases, "getPricingPhaseList") as? List<*>
                val hasTrial = phaseList?.any { p ->
                    p != null && (XposedHelpers.callMethod(p, "getPriceAmountMicros") as Long) == 0L
                } ?: false
                if (hasTrial) {
                    val token = XposedHelpers.callMethod(offer, "getOfferToken") as? String
                    if (!token.isNullOrBlank()) return token
                }
            }

            // Fallback to first available offer
            val first = offers.firstOrNull() ?: return null
            return XposedHelpers.callMethod(first, "getOfferToken") as? String
        } catch (_: Throwable) {
            return null
        }
    }
}

/**
 * Caches ProductDetails objects intercepted from queryProductDetails responses.
 * Keyed by product ID — allows triggering billing flow without re-querying.
 */
object ProductDetailsCache {
    private val cache = mutableMapOf<String, Any>()

    fun put(productId: String, details: Any) {
        cache[productId] = details
    }

    fun get(productId: String): Any? = cache[productId]

    fun clear() = cache.clear()
}

/**
 * Stores discovered trial offers for reporting back to the main app
 */
object OfferStore {
    data class TrialOffer(
        val productId: String,
        val offerToken: String,
        val basePlanId: String
    )

    private val _offers = mutableListOf<TrialOffer>()
    val offers: List<TrialOffer> get() = _offers.toList()

    fun addTrialOffer(productId: String, offerToken: String, basePlanId: String) {
        val offer = TrialOffer(productId, offerToken, basePlanId)
        if (_offers.none { it.productId == productId && it.basePlanId == basePlanId }) {
            _offers.add(offer)
            XposedBridge.log("[${IapHookModule.TAG}] Stored trial offer: $productId/$basePlanId")
        }
    }

    fun clear() = _offers.clear()
}
