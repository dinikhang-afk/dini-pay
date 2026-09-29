package com.adr.checkiap.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.adr.checkiap.data.model.ProductModel
import com.adr.checkiap.data.model.ProductType
import com.adr.checkiap.data.model.ScanStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface BillingConnectionState {
    data object Disconnected : BillingConnectionState
    data object Connecting : BillingConnectionState
    data object Connected : BillingConnectionState
    data class Error(val responseCode: Int, val message: String) : BillingConnectionState
}

sealed interface PurchaseResult {
    data class Success(val productIds: List<String>, val orderId: String?) : PurchaseResult
    data object Cancelled : PurchaseResult
    data class Failed(val code: Int, val message: String) : PurchaseResult
}

class BillingClientManager(
    private val context: Context
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // IDs sản phẩm dạng consumable — thêm product ID vào đây để consume thay vì acknowledge
    private val consumableProductIds = mutableSetOf<String>()

    private val _connectionState = MutableStateFlow<BillingConnectionState>(BillingConnectionState.Disconnected)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()

    private val _logs = MutableSharedFlow<String>(replay = 50)
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    private val _purchaseResults = MutableSharedFlow<PurchaseResult>(replay = 1)
    val purchaseResults: SharedFlow<PurchaseResult> = _purchaseResults.asSharedFlow()

    private val cachedProductDetails = mutableMapOf<String, ProductDetails>()

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private fun log(message: String) {
        val timestamp = timeFormatter.format(Date())
        _logs.tryEmit("[$timestamp] $message")
    }

    fun markAsConsumable(vararg productIds: String) {
        consumableProductIds.addAll(productIds)
    }

    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
    }

    fun startConnection(onReady: (() -> Unit)? = null) {
        if (billingClient.isReady) {
            _connectionState.value = BillingConnectionState.Connected
            onReady?.invoke()
            return
        }

        _connectionState.value = BillingConnectionState.Connecting
        log("Đang kết nối Google Play Billing Service...")

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _connectionState.value = BillingConnectionState.Connected
                    log("Google Play Billing kết nối THÀNH CÔNG (OK)")
                    onReady?.invoke()
                } else {
                    val errMsg = "Lỗi kết nối Play Billing: mã ${billingResult.responseCode} - ${billingResult.debugMessage}"
                    _connectionState.value = BillingConnectionState.Error(billingResult.responseCode, billingResult.debugMessage)
                    log(errMsg)
                }
            }

            override fun onBillingServiceDisconnected() {
                _connectionState.value = BillingConnectionState.Disconnected
                log("Mất kết nối với Google Play Store service. Sẽ tự động kết nối lại khi cần.")
            }
        })
    }

    suspend fun queryProducts(
        productIds: List<String>,
        productType: ProductType
    ): List<ProductModel> {
        if (productIds.isEmpty()) return emptyList()

        if (!billingClient.isReady) {
            log("Billing chưa sẵn sàng. Đang kết nối lại...")
            startConnection()
        }

        val googleProductType = when (productType) {
            ProductType.INAPP -> BillingClient.ProductType.INAPP
            ProductType.SUBS -> BillingClient.ProductType.SUBS
        }

        val productList = productIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(googleProductType)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        log("Đang truy vấn ${productIds.size} mã sản phẩm dạng $productType...")

        return try {
            val result = billingClient.queryProductDetails(params)
            val billingResult = result.billingResult
            val detailsList = result.productDetailsList.orEmpty()

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                log("Truy vấn thành công! Nhận về ${detailsList.size} kết quả từ Google Play.")
                detailsList.forEach { details ->
                    cachedProductDetails[details.productId] = details
                    log("-> Tìm thấy: ${details.productId} (${details.title})")
                }

                val foundIds = detailsList.map { it.productId }.toSet()
                val mappedFound = detailsList.map { BillingMapper.mapToProductModel(it) }

                // Detect missing product IDs
                val missingProducts = productIds.filter { it !in foundIds }.map { missingId ->
                    log("-> KHÔNG TÌM THẤY trên Play Console: $missingId")
                    ProductModel(
                        productId = missingId,
                        productType = productType,
                        title = missingId,
                        description = "Google Play không trả về thông tin cho mã này.",
                        status = ScanStatus.NOT_FOUND
                    )
                }

                mappedFound + missingProducts
            } else {
                val err = "Lỗi truy vấn Play Billing (${billingResult.responseCode}): ${billingResult.debugMessage}"
                log(err)
                productIds.map { id ->
                    ProductModel(
                        productId = id,
                        productType = productType,
                        title = id,
                        description = err,
                        status = ScanStatus.ERROR,
                        errorMessage = err
                    )
                }
            }
        } catch (e: Exception) {
            val exMsg = "Exception khi gọi queryProductDetails: ${e.localizedMessage}"
            log(exMsg)
            productIds.map { id ->
                ProductModel(
                    productId = id,
                    productType = productType,
                    title = id,
                    description = exMsg,
                    status = ScanStatus.ERROR,
                    errorMessage = exMsg
                )
            }
        }
    }

    fun launchBillingFlow(
        activity: Activity,
        productId: String,
        selectedOfferToken: String?
    ): BillingResult {
        val details = cachedProductDetails[productId]
            ?: return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
                .setDebugMessage("Sản phẩm chưa được nạp chi tiết từ Google Play.")
                .build()

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)

        if (!selectedOfferToken.isNullOrBlank()) {
            productDetailsParamsBuilder.setOfferToken(selectedOfferToken)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        log("Bắt đầu mở giao diện thanh toán Google Play cho $productId...")
        return billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    val ids = purchase.products
                    log("MUA HÀNG THÀNH CÔNG: ${ids.joinToString()} | Order: ${purchase.orderId}")
                    log("  State: ${purchase.purchaseState} | Token: ${purchase.purchaseToken.take(20)}...")

                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        scope.launch { handlePurchaseCompletion(purchase) }
                    }
                }
                val allIds = purchases?.flatMap { it.products } ?: emptyList()
                val orderId = purchases?.firstOrNull()?.orderId
                _purchaseResults.tryEmit(PurchaseResult.Success(allIds, orderId))
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                log("Người dùng đã hủy giao diện mua hàng Google Play.")
                _purchaseResults.tryEmit(PurchaseResult.Cancelled)
            }
            else -> {
                log("Giao dịch kết thúc với mã lỗi: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                _purchaseResults.tryEmit(
                    PurchaseResult.Failed(billingResult.responseCode, billingResult.debugMessage)
                )
            }
        }
    }

    private suspend fun handlePurchaseCompletion(purchase: Purchase) {
        val ids = purchase.products
        val isConsumable = ids.any { it in consumableProductIds }

        if (isConsumable) {
            // Consume — cho phép mua lại (coins, gems, etc.)
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val result = billingClient.consumePurchase(consumeParams)
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                log("CONSUME thành công: ${ids.joinToString()}")
            } else {
                log("CONSUME thất bại (${result.billingResult.responseCode}): ${result.billingResult.debugMessage}")
            }
        } else if (!purchase.isAcknowledged) {
            // Acknowledge — bắt buộc trong 3 ngày, nếu không Google tự refund
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val result = billingClient.acknowledgePurchase(ackParams)
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                log("ACKNOWLEDGE thành công: ${ids.joinToString()}")
            } else {
                log("ACKNOWLEDGE thất bại (${result.responseCode}): ${result.debugMessage}")
            }
        } else {
            log("Purchase đã được acknowledge trước đó: ${ids.joinToString()}")
        }
    }

    fun endConnection() {
        if (billingClient.isReady) {
            billingClient.endConnection()
            _connectionState.value = BillingConnectionState.Disconnected
            log("Đã đóng kết nối Google Play Billing.")
        }
    }
}
