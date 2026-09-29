package com.adr.checkiap.data.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast

/**
 * Handles callbacks from PackageInstaller install sessions.
 * Crucial for Android 12+: catches STATUS_PENDING_USER_ACTION and
 * launches the system confirmation dialog via Intent.EXTRA_INTENT.
 */
class InstallStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.d("InstallStatusReceiver", "Install callback received. Status: $status, message: $message")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // System requires user to confirm installation
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirmIntent)
                    } catch (e: Exception) {
                        Log.e("InstallStatusReceiver", "Failed to launch install confirmation dialog", e)
                        Toast.makeText(
                            context,
                            "Không thể mở xác nhận cài đặt: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(context, "Lỗi: Không nhận được intent xác nhận từ hệ thống", Toast.LENGTH_LONG).show()
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(
                    context,
                    "✓ Cài đặt thành công! Hãy mở app để kích hoạt offer.",
                    Toast.LENGTH_LONG
                ).show()
            }

            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                val errorDesc = when (status) {
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> "Xung đột chữ ký! Hãy gỡ app gốc trước khi cài."
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Không tương thích! Hãy gỡ app gốc trước."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Bộ nhớ thiết bị không đủ!"
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Bị chặn cài đặt bởi hệ thống!"
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Cài đặt bị hủy bởi người dùng."
                    else -> message ?: "Mã lỗi $status"
                }
                Toast.makeText(context, "Cài đặt thất bại: $errorDesc", Toast.LENGTH_LONG).show()
            }
        }
    }
}
