package com.adr.checkiap.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adr.checkiap.data.billing.BillingClientManager
import com.adr.checkiap.data.local.ScanDao
import com.adr.checkiap.data.model.ProductModel
import com.adr.checkiap.data.model.ProductType
import com.adr.checkiap.data.model.ScanSnapshot
import com.adr.checkiap.domain.usecase.ScanProductsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExploreUiState(
    val inputIds: String = "",
    val selectedType: ProductType? = null,
    val isScanning: Boolean = false,
    val scannedProducts: List<ProductModel> = emptyList(),
    val latestSnapshot: ScanSnapshot? = null,
    val errorMessage: String? = null,
    val selectedProduct: ProductModel? = null
)

class ExploreViewModel(
    private val billingClientManager: BillingClientManager,
    private val scanDao: ScanDao,
    private val scanProductsUseCase: ScanProductsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    fun updateInput(input: String) {
        _uiState.value = _uiState.value.copy(inputIds = input)
    }

    fun selectType(type: ProductType?) {
        _uiState.value = _uiState.value.copy(selectedType = type)
    }

    fun selectProduct(product: ProductModel?) {
        _uiState.value = _uiState.value.copy(selectedProduct = product)
    }

    fun performScan() {
        val lines = _uiState.value.inputIds.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Vui lòng nhập ít nhất một Product ID.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, errorMessage = null)
            try {
                val (inAppIds, subsIds) = when (_uiState.value.selectedType) {
                    ProductType.INAPP -> Pair(lines, emptyList())
                    ProductType.SUBS -> Pair(emptyList(), lines)
                    null -> {
                        // Query both types with all IDs
                        Pair(lines, lines)
                    }
                }

                val snapshot = scanProductsUseCase(inAppIds, subsIds, emptyList())
                // Deduplicate: if queried both types, same ID may appear twice (one FOUND, one NOT_FOUND)
                val deduped = snapshot.products
                    .groupBy { it.productId }
                    .map { (_, models) ->
                        models.firstOrNull { it.status == com.adr.checkiap.data.model.ScanStatus.FOUND } ?: models.first()
                    }

                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scannedProducts = deduped,
                    latestSnapshot = snapshot.copy(products = deduped)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    errorMessage = e.localizedMessage ?: "Lỗi khi quét sản phẩm"
                )
            }
        }
    }
}
