package com.adr.checkiap.data.repository

import com.adr.checkiap.data.model.AppInfoModel
import com.adr.checkiap.data.model.IapItemModel

object StoreAppRepository {

    val popularApps = listOf(
        AppInfoModel(
            packageName = "com.lemon.lvoverseas",
            appName = "CapCut: Photo & Video Editor",
            developer = "Bytedance Pte. Ltd",
            category = "Video & Ảnh",
            totalIap = 354,
            trialCount = 65,
            discountCount = 50
        ),
        AppInfoModel(
            packageName = "com.openai.chatgpt",
            appName = "ChatGPT",
            developer = "OpenAI",
            category = "AI & Trợ lý",
            totalIap = 12,
            trialCount = 3,
            discountCount = 4
        ),
        AppInfoModel(
            packageName = "com.duolingo",
            appName = "Duolingo: Học Ngoại Ngữ",
            developer = "Duolingo",
            category = "Giáo dục",
            totalIap = 48,
            trialCount = 14,
            discountCount = 10
        ),
        AppInfoModel(
            packageName = "com.canva.editor",
            appName = "Canva: Thiết kế & Video",
            developer = "Canva Pty Ltd - Thiết kế đồ họa",
            category = "Đồ họa",
            totalIap = 86,
            trialCount = 20,
            discountCount = 15
        ),
        AppInfoModel(
            packageName = "com.adobe.lrmobile",
            appName = "Adobe Lightroom",
            developer = "Adobe - Chỉnh sửa ảnh chuyên nghiệp",
            category = "Nhiếp ảnh",
            totalIap = 62,
            trialCount = 18,
            discountCount = 12
        ),
        AppInfoModel(
            packageName = "org.telegram.messenger",
            developer = "Telegram FZ-LLC",
            appName = "Telegram Messenger",
            category = "Mạng xã hội",
            totalIap = 24,
            trialCount = 0,
            discountCount = 6
        ),
        AppInfoModel(
            packageName = "com.picsart.studio",
            appName = "PicsArt AI Photo Editor",
            developer = "PicsArt, Inc.",
            category = "Video & Ảnh",
            totalIap = 142,
            trialCount = 35,
            discountCount = 28
        )
    )

    fun getIapForApp(packageName: String): List<IapItemModel> {
        return if (packageName == "com.lemon.lvoverseas") {
            listOf(
                IapItemModel(
                    id = "capcut_monthly_trial_7d",
                    title = "MonthlySubscription-7days free",
                    sku = "com.lemon.lvoverseas.monthly_7dfree",
                    priceText = "₫249.000",
                    renewalPriceText = "Hết trial - 1 tháng - 249.000đ - Hidden",
                    trialPeriod = "7days free",
                    isHidden = true,
                    isTrial = true,
                    isCurrentActive = true,
                    groupTag = "IT Membership Subscription Package"
                ),
                IapItemModel(
                    id = "capcut_monthly_regular",
                    title = "Monthly Subscription",
                    sku = "com.lemon.lvoverseas.monthly_regular",
                    priceText = "₫229.000",
                    renewalPriceText = "Nguyên giá (hết lượt trial) - 1 tháng - ₫229.000 - Hidden",
                    trialPeriod = null,
                    isHidden = true,
                    isTrial = false,
                    isCurrentActive = false,
                    groupTag = "IT Membership Subscription Package"
                ),
                IapItemModel(
                    id = "capcut_monthly_trial_uk",
                    title = "MonthlySubscription-7days free",
                    sku = "com.lemon.lvoverseas.uk_monthly_7d",
                    priceText = "Đang dùng",
                    renewalPriceText = "Dùng - 1 tháng - Hidden",
                    trialPeriod = "7days free",
                    isHidden = true,
                    isTrial = true,
                    isCurrentActive = true,
                    groupTag = "UK Membership Subscription Package"
                ),
                IapItemModel(
                    id = "capcut_yearly_trial_7d",
                    title = "YearlySubscription-7days free",
                    sku = "com.lemon.lvoverseas.yearly_7dfree",
                    priceText = "Dùng thử",
                    renewalPriceText = "Dùng thử 7 ngày - 1 năm - Sau đó ₫1.899.000 - Hidden",
                    trialPeriod = "7days free",
                    isHidden = true,
                    isTrial = true,
                    isCurrentActive = false,
                    groupTag = "UK Membership Subscription Package"
                ),
                IapItemModel(
                    id = "capcut_yearly_regular",
                    title = "Yearly Subscription",
                    sku = "com.lemon.lvoverseas.yearly_regular",
                    priceText = "₫1.899.000",
                    renewalPriceText = "Hết trial - 1 năm - ₫1.899.000 - Hidden",
                    trialPeriod = null,
                    isHidden = true,
                    isTrial = false,
                    isCurrentActive = false,
                    groupTag = "UK Membership Subscription Package"
                ),
                IapItemModel(
                    id = "capcut_monthly_discount_189k",
                    title = "Monthly Subscription - Promo",
                    sku = "com.lemon.lvoverseas.monthly_promo_189",
                    priceText = "₫189.000",
                    renewalPriceText = "Ưu đãi đặc biệt - 1 tháng - ₫189.000 - Hidden",
                    trialPeriod = null,
                    isHidden = true,
                    isDiscount = true,
                    isCurrentActive = false,
                    groupTag = "Global Promo Package"
                )
            )
        } else {
            listOf(
                IapItemModel(
                    id = "${packageName}_monthly_trial",
                    title = "Pro Monthly - 7 Days Free",
                    sku = "$packageName.monthly_trial",
                    priceText = "Dùng thử",
                    renewalPriceText = "Dùng thử 7 ngày, sau đó 199.000đ/tháng",
                    trialPeriod = "7days free",
                    isHidden = false,
                    isTrial = true,
                    groupTag = "VIP Membership"
                ),
                IapItemModel(
                    id = "${packageName}_yearly_discount",
                    title = "Pro Annual (50% OFF)",
                    sku = "$packageName.yearly_discount",
                    priceText = "₫899.000",
                    renewalPriceText = "Giảm 50% năm đầu tiên",
                    trialPeriod = null,
                    isHidden = true,
                    isDiscount = true,
                    groupTag = "VIP Membership"
                )
            )
        }
    }
}
