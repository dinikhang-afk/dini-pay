package com.adr.checkiap.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adr.checkiap.data.scanner.InstalledAppInfo
import com.adr.checkiap.data.scanner.InstalledAppScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InstalledAppsUiState(
    val allApps: List<InstalledAppInfo> = emptyList(),
    val filteredApps: List<InstalledAppInfo> = emptyList(),
    val searchQuery: String = "",
    val filter: AppFilter = AppFilter.BILLING_ONLY,
    val isLoading: Boolean = false,
    val totalApps: Int = 0,
    val billingApps: Int = 0,
    val playStoreApps: Int = 0
)

class InstalledAppsViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = InstalledAppScanner(application)

    private val _uiState = MutableStateFlow(InstalledAppsUiState())
    val uiState: StateFlow<InstalledAppsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val apps = scanner.scanAll(includeSystemApps = false)
            val billingCount = apps.count { it.hasBillingPermission }
            val playCount = apps.count { it.installerPackage == "com.android.vending" }

            _uiState.value = _uiState.value.copy(
                allApps = apps,
                totalApps = apps.size,
                billingApps = billingCount,
                playStoreApps = playCount,
                isLoading = false
            )
            applyFilters()
        }
    }

    fun updateSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    fun setFilter(filter: AppFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        val query = state.searchQuery.lowercase()

        val filtered = state.allApps
            .filter { app ->
                when (state.filter) {
                    AppFilter.ALL -> true
                    AppFilter.BILLING_ONLY -> app.hasBillingPermission
                    AppFilter.PLAY_STORE -> app.installerPackage == "com.android.vending"
                }
            }
            .filter { app ->
                if (query.isBlank()) true
                else app.appName.lowercase().contains(query) ||
                        app.packageName.lowercase().contains(query)
            }

        _uiState.value = state.copy(filteredApps = filtered)
    }
}
