package com.adr.checkiap.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Core Xposed hook — runs inside the target app's process.
 * Intercepts Google Play Billing to discover and log ALL product offers,
 * including hidden trials and discounts the app's UI doesn't expose.
 */
class IapHookModule : IXposedHookLoadPackage {

    companion object {
        const val TAG = "IapHook"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "com.adr.checkiap") return
        if (lpparam.packageName.startsWith("com.android.")) return

        XposedBridge.log("[$TAG] Loaded into: ${lpparam.packageName}")

        hookBillingClientBuilder(lpparam)
        hookQueryProductDetails(lpparam)
        hookPurchasesUpdatedListener(lpparam)
    }

    private fun hookBillingClientBuilder(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val builderClass = XposedHelpers.findClass(
                "com.android.billingclient.api.BillingClient\$Builder",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                builderClass,
                "build",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val client = param.result
                        XposedBridge.log("[$TAG] BillingClient built: $client")
                        BillingClientHolder.client = client
                        BillingClientHolder.classLoader = lpparam.classLoader
                    }
                }
            )
            XposedBridge.log("[$TAG] Hooked BillingClient.Builder.build()")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook BillingClient.Builder: ${e.message}")
        }
    }

    private fun hookQueryProductDetails(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val billingClientClass = XposedHelpers.findClass(
                "com.android.billingclient.api.BillingClient",
                lpparam.classLoader
            )

            val listenerClass = XposedHelpers.findClass(
                "com.android.billingclient.api.ProductDetailsResponseListener",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                billingClientClass,
                "queryProductDetailsAsync",
                XposedHelpers.findClass(
                    "com.android.billingclient.api.QueryProductDetailsParams",
                    lpparam.classLoader
                ),
                listenerClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("[$TAG] queryProductDetailsAsync called")

                        val originalListener = param.args[1]
                        val wrappedListener = java.lang.reflect.Proxy.newProxyInstance(
                            lpparam.classLoader,
                            arrayOf(listenerClass)
                        ) { _, method, args ->
                            if (method.name == "onProductDetailsResponse") {
                                val billingResult = args!![0]
                                val productDetailsList = args[1] as? List<*>

                                val responseCode = XposedHelpers.callMethod(billingResult, "getResponseCode") as Int
                                XposedBridge.log("[$TAG] queryProductDetails response: code=$responseCode, products=${productDetailsList?.size ?: 0}")

                                productDetailsList?.forEach { details ->
                                    logProductDetails(details!!)
                                }
                            }
                            method.invoke(originalListener, *args!!)
                        }
                        param.args[1] = wrappedListener
                    }
                }
            )
            XposedBridge.log("[$TAG] Hooked queryProductDetailsAsync")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook queryProductDetailsAsync: ${e.message}")
        }
    }

    private fun logProductDetails(details: Any) {
        try {
            val productId = XposedHelpers.callMethod(details, "getProductId") as String
            val productType = XposedHelpers.callMethod(details, "getProductType") as String
            val title = XposedHelpers.callMethod(details, "getTitle") as String
            val description = XposedHelpers.callMethod(details, "getDescription") as String

            XposedBridge.log("[$TAG] ═══════════════════════════════════════")
            XposedBridge.log("[$TAG] Product: $productId")
            XposedBridge.log("[$TAG] Type: $productType | Title: $title")
            XposedBridge.log("[$TAG] Desc: $description")

            if (productType == "subs") {
                val offerDetailsList = XposedHelpers.callMethod(details, "getSubscriptionOfferDetails") as? List<*>

                if (offerDetailsList.isNullOrEmpty()) {
                    XposedBridge.log("[$TAG]   (no subscription offers)")
                } else {
                    XposedBridge.log("[$TAG]   Total offers: ${offerDetailsList.size}")
                    offerDetailsList.forEachIndexed { index, offerDetails ->
                        offerDetails ?: return@forEachIndexed
                        val offerId = XposedHelpers.callMethod(offerDetails, "getOfferId")
                        val basePlanId = XposedHelpers.callMethod(offerDetails, "getBasePlanId") as String
                        val offerToken = XposedHelpers.callMethod(offerDetails, "getOfferToken") as String
                        val offerTags = XposedHelpers.callMethod(offerDetails, "getOfferTags") as List<*>

                        XposedBridge.log("[$TAG]   ── Offer #${index + 1} ──")
                        XposedBridge.log("[$TAG]   OfferId: $offerId | BasePlan: $basePlanId")
                        XposedBridge.log("[$TAG]   Tags: $offerTags")
                        XposedBridge.log("[$TAG]   Token: ${offerToken.take(40)}...")

                        val pricingPhases = XposedHelpers.callMethod(offerDetails, "getPricingPhases")
                        val phaseList = XposedHelpers.callMethod(pricingPhases, "getPricingPhaseList") as List<*>

                        phaseList.forEachIndexed { pi, phase ->
                            phase ?: return@forEachIndexed
                            val price = XposedHelpers.callMethod(phase, "getFormattedPrice") as String
                            val priceMicros = XposedHelpers.callMethod(phase, "getPriceAmountMicros") as Long
                            val period = XposedHelpers.callMethod(phase, "getBillingPeriod") as String
                            val recurrence = XposedHelpers.callMethod(phase, "getRecurrenceMode") as Int
                            val cycleCount = XposedHelpers.callMethod(phase, "getBillingCycleCount") as Int

                            val phaseType = when {
                                priceMicros == 0L -> "FREE TRIAL"
                                pi == 0 && phaseList.size > 1 -> "INTRO DISCOUNT"
                                else -> "REGULAR"
                            }

                            XposedBridge.log("[$TAG]     Phase ${pi + 1}: $phaseType | $price ($priceMicros µ) | Period: $period | Cycles: $cycleCount | Recurrence: $recurrence")
                        }

                        // Store discovered free trials
                        if (phaseList.any {
                                it != null && (XposedHelpers.callMethod(it, "getPriceAmountMicros") as Long) == 0L
                            }) {
                            XposedBridge.log("[$TAG]   ★ FREE TRIAL DETECTED ★")
                            OfferStore.addTrialOffer(productId, offerToken, basePlanId)
                        }
                    }
                }
            } else {
                val oneTime = XposedHelpers.callMethod(details, "getOneTimePurchaseOfferDetails")
                if (oneTime != null) {
                    val price = XposedHelpers.callMethod(oneTime, "getFormattedPrice") as String
                    val priceMicros = XposedHelpers.callMethod(oneTime, "getPriceAmountMicros") as Long
                    XposedBridge.log("[$TAG]   One-time: $price ($priceMicros µ)")
                }
            }

            // Cache the ProductDetails object for later billing flow trigger
            ProductDetailsCache.put(productId, details)

            XposedBridge.log("[$TAG] ═══════════════════════════════════════")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Error logging product: ${e.message}")
        }
    }

    private fun hookPurchasesUpdatedListener(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val builderClass = XposedHelpers.findClass(
                "com.android.billingclient.api.BillingClient\$Builder",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                builderClass,
                "setListener",
                XposedHelpers.findClass(
                    "com.android.billingclient.api.PurchasesUpdatedListener",
                    lpparam.classLoader
                ),
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val originalListener = param.args[0]

                        val listenerClass = XposedHelpers.findClass(
                            "com.android.billingclient.api.PurchasesUpdatedListener",
                            lpparam.classLoader
                        )

                        val wrappedListener = java.lang.reflect.Proxy.newProxyInstance(
                            lpparam.classLoader,
                            arrayOf(listenerClass)
                        ) { _, method, args ->
                            if (method.name == "onPurchasesUpdated") {
                                val billingResult = args!![0]
                                val purchases = args[1] as? List<*>
                                val code = XposedHelpers.callMethod(billingResult, "getResponseCode") as Int
                                XposedBridge.log("[$TAG] onPurchasesUpdated: code=$code, purchases=${purchases?.size ?: 0}")

                                purchases?.forEach { purchase ->
                                    if (purchase != null) {
                                        val products = XposedHelpers.callMethod(purchase, "getProducts") as List<*>
                                        val orderId = XposedHelpers.callMethod(purchase, "getOrderId")
                                        val state = XposedHelpers.callMethod(purchase, "getPurchaseState") as Int
                                        XposedBridge.log("[$TAG]   Purchase: $products | Order: $orderId | State: $state")
                                    }
                                }
                            }
                            method.invoke(originalListener, *args!!)
                        }
                        param.args[0] = wrappedListener
                    }
                }
            )
            XposedBridge.log("[$TAG] Hooked PurchasesUpdatedListener")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook PurchasesUpdatedListener: ${e.message}")
        }
    }
}
