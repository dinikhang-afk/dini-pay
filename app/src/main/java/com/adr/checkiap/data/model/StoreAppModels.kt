package com.adr.checkiap.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppInfoModel(
    val packageName: String,
    val appName: String,
    val developer: String,
    val iconUrl: String = "",
    val totalIap: Int = 0,
    val trialCount: Int = 0,
    val discountCount: Int = 0,
    val category: String = "Video & Ảnh"
)

@Serializable
data class IapItemModel(
    val id: String,
    val title: String,
    val sku: String,
    val priceText: String,
    val renewalPriceText: String = "",
    val trialPeriod: String? = null, // e.g. "7days free", "3days free"
    val isHidden: Boolean = false,
    val isTrial: Boolean = false,
    val isDiscount: Boolean = false,
    val isCurrentActive: Boolean = false,
    val groupTag: String = "Default" // e.g. "IT Membership", "UK Membership"
)
