package com.adr.checkiap

import android.app.Application
import com.adr.checkiap.data.billing.BillingClientManager
import com.adr.checkiap.data.local.AppDatabase
import com.adr.checkiap.domain.usecase.CompareScansUseCase
import com.adr.checkiap.domain.usecase.ScanProductsUseCase

class AdrCheckIapApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var billingManager: BillingClientManager
        private set

    lateinit var scanProductsUseCase: ScanProductsUseCase
        private set

    lateinit var compareScansUseCase: CompareScansUseCase
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        billingManager = BillingClientManager(this)
        scanProductsUseCase = ScanProductsUseCase(billingManager, database.scanDao())
        compareScansUseCase = CompareScansUseCase(database.scanDao())
    }
}
