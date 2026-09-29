package com.adr.checkiap.ui.scanner

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

data class ScannerUiState(
    val inputIds: String = "premium_monthly\npremium_yearly\nremove_ads\ncoins_100",
    val selectedType: ProductType? = null, // null means scan both
    val isScanning: Boolean = false,
    val scannedProducts: List<ProductModel> = emptyList(),
    val latestSnapshot: ScanSnapshot? = null,
    val errorMessage: String? = null
)

class ScannerViewModel(
    private val billingClientManager: BillingClientManager,
    private val scanDao: ScanDao,
    private val scanProductsUseCase: ScanProductsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun updateInput(input: String) {
        _uiState.value = _uiState.value.copy(inputIds = input)
    }

    fun selectType(type: ProductType?) {
        _uiState.value = _uiState.value.copy(selectedType = type)
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
                        // Guess or query both
                        val subs = lines.filter { it.contains("subs") || it.contains("month") || it.contains("year") || it.contains("week") }
                        val inApp = lines.filter { it !in subs }
                        Pair(if (inApp.isEmpty() && subs.isEmpty()) lines else inApp, subs.ifEmpty { lines })
                    }
                }

                val snapshot = scanProductsUseCase(inAppIds, subsIds, emptyList())
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scannedProducts = snapshot.products,
                    latestSnapshot = snapshot
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
