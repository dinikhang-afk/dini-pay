package com.adr.checkiap.data.scanner

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

/**
 * Extracts APK files from installed apps for LSPatch processing.
 * Handles both single APK and split APK (App Bundle) apps.
 * Output goes to Downloads/IAPCheck/ for easy access from LSPatch Manager.
 */
object ApkExtractor {

    data class ExtractionResult(
        val success: Boolean,
        val outputPath: String,
        val fileSize: Long = 0,
        val splitCount: Int = 0,
        val error: String? = null
    )

    /**
     * Extract ALL APK files (base + splits) of the given package.
     * For App Bundle apps, copies every split APK to Downloads/IAPCheck/<packageName>/
     * For single APK apps, copies to Downloads/IAPCheck/<packageName>.apk
     */
    fun extractApk(context: Context, packageName: String): ExtractionResult {
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }

            val baseApk = File(appInfo.sourceDir)
            if (!baseApk.exists()) {
                return ExtractionResult(false, "", error = "APK source not found: ${appInfo.sourceDir}")
            }

            val splitSourceDirs = appInfo.splitSourceDirs
            val isSplitApk = !splitSourceDirs.isNullOrEmpty()

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            if (isSplitApk) {
                // App Bundle — extract base + all splits to a folder
                val outputDir = File(downloadsDir, "IAPCheck/$packageName")
                outputDir.mkdirs()

                // Clear old extractions
                outputDir.listFiles()?.forEach { it.delete() }

                var totalSize = 0L
                var fileCount = 0

                // Copy base APK
                val baseOut = File(outputDir, "base.apk")
                copyFile(baseApk, baseOut)
                totalSize += baseOut.length()
                fileCount++

                // Copy each split APK
                splitSourceDirs.forEach { splitPath ->
                    val splitFile = File(splitPath)
                    if (splitFile.exists()) {
                        val splitName = splitFile.name
                        val splitOut = File(outputDir, splitName)
                        copyFile(splitFile, splitOut)
                        totalSize += splitOut.length()
                        fileCount++
                    }
                }

                ExtractionResult(
                    success = true,
                    outputPath = outputDir.absolutePath,
                    fileSize = totalSize,
                    splitCount = fileCount
                )
            } else {
                // Single APK — simple copy
                val outputDir = File(downloadsDir, "IAPCheck")
                outputDir.mkdirs()

                val outputFile = File(outputDir, "${packageName}.apk")
                copyFile(baseApk, outputFile)

                ExtractionResult(
                    success = true,
                    outputPath = outputFile.absolutePath,
                    fileSize = outputFile.length(),
                    splitCount = 1
                )
            }
        } catch (e: PackageManager.NameNotFoundException) {
            ExtractionResult(false, "", error = "Package not found: $packageName")
        } catch (e: SecurityException) {
            ExtractionResult(false, "", error = "Permission denied: ${e.message}")
        } catch (e: Exception) {
            ExtractionResult(false, "", error = "Extract failed: ${e.message}")
        }
    }

    private fun copyFile(src: File, dst: File) {
        src.inputStream().use { input ->
            FileOutputStream(dst).use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        }
    }

    /**
     * Check if LSPatch Manager is installed
     */
    fun isLSPatchInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    "org.lsposed.lspatch",
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo("org.lsposed.lspatch", 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
