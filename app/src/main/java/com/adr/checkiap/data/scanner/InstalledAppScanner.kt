package com.adr.checkiap.data.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val icon: Drawable?,
    val installerPackage: String?,
    val hasBillingPermission: Boolean,
    val isSystemApp: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long
)

class InstalledAppScanner(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    fun scanAll(includeSystemApps: Boolean = false): List<InstalledAppInfo> {
        val flags = PackageManager.GET_PERMISSIONS
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(flags)
        }

        return packages
            .filter { pkg ->
                if (!includeSystemApps) {
                    // Filter out system apps unless requested
                    (pkg.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) == 0
                } else true
            }
            .map { it.toInstalledAppInfo() }
            .sortedByDescending { it.hasBillingPermission } // Billing apps first
    }

    fun scanBillingAppsOnly(): List<InstalledAppInfo> {
        return scanAll(includeSystemApps = false).filter { it.hasBillingPermission }
    }

    private fun PackageInfo.toInstalledAppInfo(): InstalledAppInfo {
        val hasBilling = requestedPermissions?.any {
            it == "com.android.vending.BILLING"
        } == true

        val appLabel = applicationInfo?.let {
            pm.getApplicationLabel(it).toString()
        } ?: packageName

        val icon = try {
            applicationInfo?.let { pm.getApplicationIcon(it) }
        } catch (_: Exception) { null }

        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } catch (_: Exception) { null }
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(packageName)
        }

        val isSystem = (applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0

        val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }

        return InstalledAppInfo(
            packageName = packageName,
            appName = appLabel,
            versionName = versionName ?: "",
            versionCode = vCode,
            icon = icon,
            installerPackage = installer,
            hasBillingPermission = hasBilling,
            isSystemApp = isSystem,
            firstInstallTime = firstInstallTime,
            lastUpdateTime = lastUpdateTime
        )
    }
}
