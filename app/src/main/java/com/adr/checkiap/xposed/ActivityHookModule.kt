package com.adr.checkiap.xposed

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.ref.WeakReference

/**
 * Hooks Activity lifecycle in target apps to receive remote commands and Intent triggers
 * from the main ADR Check IAP app, and executes Google Play Billing flows on the active Activity.
 */
class ActivityHookModule : IXposedHookLoadPackage {

    companion object {
        const val ACTION_QUERY = "com.adr.checkiap.QUERY_OFFERS"
        const val ACTION_LAUNCH = "com.adr.checkiap.LAUNCH_BILLING"
        const val ACTION_OFFERS_RESULT = "com.adr.checkiap.OFFERS_RESULT"
        const val ACTION_LAUNCH_RESULT = "com.adr.checkiap.LAUNCH_RESULT"

        const val EXTRA_TRIGGER_PRODUCT = "com.adr.checkiap.PRODUCT_ID"
        const val EXTRA_TRIGGER_TOKEN = "com.adr.checkiap.OFFER_TOKEN"
    }

    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null
    private var receiverRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "com.adr.checkiap") return
        if (lpparam.packageName.startsWith("com.android.")) return

        hookActivityLifecycle(lpparam)
    }

    private fun hookActivityLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                lpparam.classLoader,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as Activity
                        currentActivityRef = WeakReference(activity)
                        registerCommandReceiver(activity.applicationContext, lpparam)

                        // Check if launched with IAP trigger Intent extras
                        val intent = activity.intent
                        val productId = intent?.getStringExtra(EXTRA_TRIGGER_PRODUCT)
                        if (!productId.isNullOrBlank()) {
                            intent.removeExtra(EXTRA_TRIGGER_PRODUCT)
                            val offerToken = intent.getStringExtra(EXTRA_TRIGGER_TOKEN)
                            intent.removeExtra(EXTRA_TRIGGER_TOKEN)

                            XposedBridge.log("[${IapHookModule.TAG}] Activity onResume trigger for $productId")
                            mainHandler.postDelayed({
                                executeBillingFlow(activity, productId, offerToken)
                            }, 500)
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                lpparam.classLoader,
                "onNewIntent",
                Intent::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as Activity
                        currentActivityRef = WeakReference(activity)
                        val intent = param.args[0] as? Intent ?: return
                        val productId = intent.getStringExtra(EXTRA_TRIGGER_PRODUCT)
                        if (!productId.isNullOrBlank()) {
                            intent.removeExtra(EXTRA_TRIGGER_PRODUCT)
                            val offerToken = intent.getStringExtra(EXTRA_TRIGGER_TOKEN)
                            intent.removeExtra(EXTRA_TRIGGER_TOKEN)

                            XposedBridge.log("[${IapHookModule.TAG}] Activity onNewIntent trigger for $productId")
                            mainHandler.postDelayed({
                                executeBillingFlow(activity, productId, offerToken)
                            }, 400)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("[${IapHookModule.TAG}] Failed to hook Activity lifecycle: ${e.message}")
        }
    }

    private fun registerCommandReceiver(appContext: Context, lpparam: XC_LoadPackage.LoadPackageParam) {
        if (receiverRegistered) return
        receiverRegistered = true

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_QUERY -> {
                        XposedBridge.log("[${IapHookModule.TAG}] Received QUERY_OFFERS command")
                        val offers = OfferStore.offers
                        val resultIntent = Intent(ACTION_OFFERS_RESULT).apply {
                            putExtra("package", lpparam.packageName)
                            putExtra("count", offers.size)
                            offers.forEachIndexed { i, offer ->
                                putExtra("product_$i", offer.productId)
                                putExtra("token_$i", offer.offerToken)
                                putExtra("baseplan_$i", offer.basePlanId)
                            }
                        }
                        context.sendBroadcast(resultIntent)
                    }

                    ACTION_LAUNCH -> {
                        val productId = intent.getStringExtra("product_id") ?: return
                        val offerToken = intent.getStringExtra("offer_token")
                        XposedBridge.log("[${IapHookModule.TAG}] Received LAUNCH_BILLING: product=$productId, token=$offerToken")

                        val activity = currentActivityRef?.get()
                        if (activity != null && !activity.isFinishing) {
                            executeBillingFlow(activity, productId, offerToken)
                        } else {
                            XposedBridge.log("[${IapHookModule.TAG}] No active foreground Activity available for billing")
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_QUERY)
            addAction(ACTION_LAUNCH)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
        XposedBridge.log("[${IapHookModule.TAG}] Command receiver registered for ${lpparam.packageName}")
    }

    private fun executeBillingFlow(activity: Activity, productId: String, offerToken: String?) {
        val targetAct = currentActivityRef?.get() ?: activity
        if (targetAct.isFinishing || (Build.VERSION.SDK_INT >= 17 && targetAct.isDestroyed)) {
            XposedBridge.log("[${IapHookModule.TAG}] Target activity is finishing/destroyed")
            return
        }

        targetAct.runOnUiThread {
            Toast.makeText(
                targetAct,
                "⚡ [ADR Check IAP] Đang gọi Google Play cho gói: $productId...",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Try cached ProductDetails first
        val cachedDetails = ProductDetailsCache.get(productId)
        if (cachedDetails != null) {
            XposedBridge.log("[${IapHookModule.TAG}] Using cached ProductDetails for $productId")
            targetAct.runOnUiThread {
                val ok = BillingClientHolder.launchBillingFlow(targetAct, cachedDetails, offerToken)
                if (ok) {
                    Toast.makeText(targetAct, "✓ [ADR Check IAP] Đã mở Google Play!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(targetAct, "⚠️ [ADR Check IAP] launchBillingFlow thất bại", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        val cl = BillingClientHolder.classLoader ?: targetAct.classLoader
        BillingClientHolder.classLoader = cl

        ensureBillingClient(targetAct, cl,
            onReady = { client ->
                triggerBillingFlow(targetAct, targetAct.applicationContext, productId, offerToken, cl, client)
            },
            onError = { err ->
                XposedBridge.log("[${IapHookModule.TAG}] $err")
                targetAct.runOnUiThread {
                    Toast.makeText(targetAct, "⚠️ [ADR Check IAP] $err", Toast.LENGTH_LONG).show()
                }
                broadcastError(targetAct.applicationContext, err)
            }
        )
    }

    private fun ensureBillingClient(
        activity: Activity,
        cl: ClassLoader,
        onReady: (Any) -> Unit,
        onError: (String) -> Unit
    ) {
        val existing = BillingClientHolder.client
        if (existing != null) {
            val isReady = try {
                XposedHelpers.callMethod(existing, "isReady") as? Boolean ?: false
            } catch (_: Throwable) { false }

            if (isReady) {
                onReady(existing)
                return
            }
        }

        try {
            val billingClientClass = XposedHelpers.findClass(
                "com.android.billingclient.api.BillingClient", cl
            )
            val builder = XposedHelpers.callStaticMethod(billingClientClass, "newBuilder", activity)

            try {
                XposedHelpers.callMethod(builder, "enablePendingPurchases")
            } catch (_: Throwable) {
                try {
                    val paramsClass = XposedHelpers.findClass(
                        "com.android.billingclient.api.PendingPurchasesParams", cl
                    )
                    val paramsBuilder = XposedHelpers.callStaticMethod(paramsClass, "newBuilder")
                    XposedHelpers.callMethod(paramsBuilder, "enableOneTimeProducts")
                    val params = XposedHelpers.callMethod(paramsBuilder, "build")
                    XposedHelpers.callMethod(builder, "enablePendingPurchases", params)
                } catch (_: Throwable) {}
            }

            val listenerClass = XposedHelpers.findClass(
                "com.android.billingclient.api.PurchasesUpdatedListener", cl
            )
            val dummyListener = java.lang.reflect.Proxy.newProxyInstance(
                cl, arrayOf(listenerClass)
            ) { proxy, method, args ->
                when (method.name) {
                    "onPurchasesUpdated" -> null
                    "toString" -> "PurchasesUpdatedListenerProxy"
                    "hashCode" -> 1
                    "equals" -> args?.getOrNull(0) === proxy
                    else -> null
                }
            }
            XposedHelpers.callMethod(builder, "setListener", dummyListener)

            val client = XposedHelpers.callMethod(builder, "build")

            val stateListenerClass = XposedHelpers.findClass(
                "com.android.billingclient.api.BillingClientStateListener", cl
            )
            val stateListener = java.lang.reflect.Proxy.newProxyInstance(
                cl, arrayOf(stateListenerClass)
            ) { proxy, method, args ->
                when (method.name) {
                    "onBillingSetupFinished" -> {
                        val result = args!![0]
                        val code = XposedHelpers.callMethod(result, "getResponseCode") as Int
                        if (code == 0) {
                            BillingClientHolder.client = client
                            BillingClientHolder.classLoader = cl
                            activity.runOnUiThread { onReady(client) }
                        } else {
                            val debug = try {
                                XposedHelpers.callMethod(result, "getDebugMessage") as? String ?: ""
                            } catch (_: Throwable) { "" }
                            onError("Lỗi kết nối BillingClient (code: $code $debug)")
                        }
                        null
                    }
                    "onBillingServiceDisconnected" -> null
                    "toString" -> "BillingClientStateListenerProxy"
                    "hashCode" -> 2
                    "equals" -> args?.getOrNull(0) === proxy
                    else -> null
                }
            }
            XposedHelpers.callMethod(client, "startConnection", stateListener)
        } catch (e: Throwable) {
            XposedBridge.log("[${IapHookModule.TAG}] ensureBillingClient failed: ${e.message}")
            onError(e.message ?: "Failed to initialize BillingClient")
        }
    }

    private fun broadcastError(context: Context, message: String) {
        context.sendBroadcast(Intent(ACTION_LAUNCH_RESULT).apply {
            putExtra("success", false)
            putExtra("error", message)
        })
    }

    private fun triggerBillingFlow(
        activity: Activity,
        context: Context,
        productId: String,
        offerToken: String?,
        cl: ClassLoader,
        client: Any
    ) {
        try {
            // Try SUBS first
            queryAndLaunch(activity, context, productId, offerToken, "subs", cl, client) {
                // Fallback: try INAPP
                queryAndLaunch(activity, context, productId, null, "inapp", cl, client) {
                    XposedBridge.log("[${IapHookModule.TAG}] Product $productId not found on Google Play")
                    val msg = "Gói '$productId' không tồn tại trên Google Play của app này"
                    activity.runOnUiThread {
                        Toast.makeText(activity, "⚠️ [ADR Check IAP] $msg", Toast.LENGTH_LONG).show()
                    }
                    broadcastError(context, msg)
                }
            }
        } catch (e: Throwable) {
            XposedBridge.log("[${IapHookModule.TAG}] triggerBillingFlow error: ${e.message}")
            broadcastError(context, e.message ?: "Unknown error")
        }
    }

    private fun queryAndLaunch(
        activity: Activity,
        context: Context,
        productId: String,
        offerToken: String?,
        productType: String,
        cl: ClassLoader,
        client: Any,
        onNotFound: () -> Unit
    ) {
        val productClass = XposedHelpers.findClass(
            "com.android.billingclient.api.QueryProductDetailsParams\$Product", cl
        )
        val productBuilder = XposedHelpers.callStaticMethod(productClass, "newBuilder")
        XposedHelpers.callMethod(productBuilder, "setProductId", productId)
        XposedHelpers.callMethod(productBuilder, "setProductType", productType)
        val product = XposedHelpers.callMethod(productBuilder, "build")

        val queryParamsClass = XposedHelpers.findClass(
            "com.android.billingclient.api.QueryProductDetailsParams", cl
        )
        val queryBuilder = XposedHelpers.callStaticMethod(queryParamsClass, "newBuilder")
        XposedHelpers.callMethod(queryBuilder, "setProductList", listOf(product))
        val queryParams = XposedHelpers.callMethod(queryBuilder, "build")

        val listenerClass = XposedHelpers.findClass(
            "com.android.billingclient.api.ProductDetailsResponseListener", cl
        )

        val listener = java.lang.reflect.Proxy.newProxyInstance(
            cl, arrayOf(listenerClass)
        ) { proxy, method, args ->
            if (method.name == "onProductDetailsResponse") {
                val billingResult = args!![0]
                val responseCode = XposedHelpers.callMethod(billingResult, "getResponseCode") as Int
                val detailsList = args[1] as? List<*>
                val details = detailsList?.firstOrNull()

                if (responseCode == 0 && details != null) {
                    XposedBridge.log("[${IapHookModule.TAG}] Got ProductDetails for $productId ($productType)")
                    ProductDetailsCache.put(productId, details)

                    val targetAct = currentActivityRef?.get() ?: activity
                    targetAct.runOnUiThread {
                        val result = BillingClientHolder.launchBillingFlow(targetAct, details, offerToken)
                        if (result) {
                            Toast.makeText(targetAct, "✓ [ADR Check IAP] Đã mở Google Play!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(targetAct, "⚠️ [ADR Check IAP] launchBillingFlow thất bại", Toast.LENGTH_SHORT).show()
                        }
                        context.sendBroadcast(Intent(ACTION_LAUNCH_RESULT).apply {
                            putExtra("success", result)
                            putExtra("product_id", productId)
                        })
                    }
                } else {
                    onNotFound()
                }
            }
            when (method.name) {
                "toString" -> "ProductDetailsResponseListenerProxy"
                "hashCode" -> 3
                "equals" -> args?.getOrNull(0) === proxy
                else -> null
            }
        }

        XposedHelpers.callMethod(client, "queryProductDetailsAsync", queryParams, listener)
    }
}
