package com.adr.checkiap.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adr.checkiap.data.billing.BillingClientManager
import com.adr.checkiap.data.billing.BillingConnectionState
import com.adr.checkiap.data.local.AppDatabase
import com.adr.checkiap.data.model.ProductType
import com.adr.checkiap.data.model.ScanSnapshot
import com.adr.checkiap.domain.usecase.ScanProductsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isScanning: Boolean = false,
    val latestSnapshot: ScanSnapshot? = null
)

class DashboardViewModel(
    val billingManager: BillingClientManager,
    private val database: AppDatabase,
    private val scanProductsUseCase: ScanProductsUseCase
) : ViewModel() {

    val connectionState: StateFlow<BillingConnectionState> = billingManager.connectionState

    val recentScans = database.scanDao().getAllScans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        billingManager.startConnection()
        loadLatestSnapshot()
    }

    private fun loadLatestSnapshot() {
        viewModelScope.launch {
            val latest = database.scanDao().getLatestScan()
            if (latest != null) {
                _uiState.value = _uiState.value.copy(latestSnapshot = latest.toSnapshot())
            }
        }
    }

    fun scanNow(customInApp: List<String> = emptyList(), customSubs: List<String> = emptyList()) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            try {
                val configured = database.productConfigDao().getAll().first()
                val inApp = if (customInApp.isNotEmpty()) customInApp else configured.filter { it.productType == ProductType.INAPP }.map { it.productId }
                val subs = if (customSubs.isNotEmpty()) customSubs else configured.filter { it.productType == ProductType.SUBS }.map { it.productId }

                val snapshot = scanProductsUseCase(inApp, subs, emptyList())
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    latestSnapshot = snapshot
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isScanning = false)
            }
        }
    }
}
