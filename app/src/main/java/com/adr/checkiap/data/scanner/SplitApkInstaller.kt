package com.adr.checkiap.data.scanner

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.io.FileInputStream

/**
 * Installs split APKs (App Bundle) using Android PackageInstaller session API.
 * No root required — uses standard install session with user confirmation.
 */
object SplitApkInstaller {

    const val INSTALL_ACTION = "com.adr.checkiap.INSTALL_COMPLETE"

    sealed class InstallResult {
        data class Success(val apkCount: Int, val files: List<String>) : InstallResult()
        data class Failure(val message: String) : InstallResult()
    }

    /**
     * Install all APK files from a directory as a single atomic session.
     * Automatically prioritizes patched base APK (*lspatched*.apk) over original base.apk.
     *
     * @param apkDir Directory containing base.apk + split_config_*.apk files
     */
    fun installFromDirectory(context: Context, apkDir: File): InstallResult {
        // 1. Check permission to install unknown apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return InstallResult.Failure("Vui lòng cấp quyền 'Cài đặt ứng dụng không rõ nguồn gốc' cho ADR Check IAP rồi ấn Cài đặt lại.")
            }
        }

        val allApks = apkDir.listFiles { file -> file.extension.equals("apk", ignoreCase = true) }
        if (allApks.isNullOrEmpty()) {
            return InstallResult.Failure("Không tìm thấy file APK nào trong ${apkDir.name}")
        }

        // 2. Identify patched base APK if present (e.g. base-398-lspatched.apk)
        val patchedBaseApk = allApks.firstOrNull { file ->
            val name = file.name.lowercase()
            name.contains("lspatched") || name.contains("patched")
        }

        val apksToInstall = if (patchedBaseApk != null) {
            // Keep patched APK as base, keep all split configs, exclude original unpatched base.apk
            val splits = allApks.filter { file ->
                file != patchedBaseApk && !file.name.equals("base.apk", ignoreCase = true)
            }
            listOf(patchedBaseApk) + splits
        } else {
            allApks.toList()
        }

        return installMultipleApks(context, apksToInstall, patchedBaseApk)
    }

    /**
     * Install multiple APK files as a single atomic session.
     */
    fun installMultipleApks(
        context: Context,
        apkFiles: List<File>,
        patchedBaseFile: File? = null
    ): InstallResult {
        val installer = context.packageManager.packageInstaller

        // Calculate total size
        val totalSize = apkFiles.sumOf { it.length() }

        // Create install session
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setSize(totalSize)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }

        val sessionId: Int
        try {
            sessionId = installer.createSession(params)
        } catch (e: Exception) {
            return InstallResult.Failure("Lỗi tạo install session: ${e.localizedMessage}")
        }

        try {
            installer.openSession(sessionId).use { session ->
                apkFiles.forEachIndexed { index, apkFile ->
                    // Name inside session: base must be recognized as base.apk
                    val sessionName = if (apkFile == patchedBaseFile || apkFile.name.equals("base.apk", ignoreCase = true)) {
                        "base.apk"
                    } else {
                        apkFile.name
                    }

                    session.openWrite(sessionName, 0, apkFile.length()).use { outputStream ->
                        FileInputStream(apkFile).use { inputStream ->
                            inputStream.copyTo(outputStream, bufferSize = 65536)
                        }
                        session.fsync(outputStream)
                    }
                }

                // Explicit intent targeting InstallStatusReceiver
                val intent = Intent(context, InstallStatusReceiver::class.java).apply {
                    action = INSTALL_ACTION
                    setPackage(context.packageName)
                    putExtra("session_id", sessionId)
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val statusReceiver = PendingIntent.getBroadcast(
                    context, sessionId, intent, flags
                )

                // Commit — Android sends STATUS_PENDING_USER_ACTION to InstallStatusReceiver
                // which then displays the system install confirmation dialog!
                session.commit(statusReceiver.intentSender)
            }

            val fileNames = apkFiles.map { it.name }
            return InstallResult.Success(apkFiles.size, fileNames)
        } catch (e: Exception) {
            try {
                installer.abandonSession(sessionId)
            } catch (_: Exception) {}
            return InstallResult.Failure("Lỗi ghi session: ${e.localizedMessage}")
        }
    }
}
