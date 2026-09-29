package com.adr.checkiap.ui.qa

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adr.checkiap.data.billing.BillingClientManager
import com.adr.checkiap.data.billing.BillingConnectionState
import com.adr.checkiap.ui.components.ConsoleBox
import com.adr.checkiap.ui.theme.AccentBlue
import com.adr.checkiap.ui.theme.DarkBackground
import com.adr.checkiap.ui.theme.DarkBorder
import com.adr.checkiap.ui.theme.DarkCard
import com.adr.checkiap.ui.theme.DarkSurface
import com.adr.checkiap.ui.theme.ErrorRed
import com.adr.checkiap.ui.theme.SuccessGreen
import com.adr.checkiap.ui.theme.TextMuted
import com.adr.checkiap.ui.theme.TextPrimary
import com.adr.checkiap.ui.theme.TextSecondary
import com.adr.checkiap.ui.theme.WarningYellow

@Composable
fun TestCenterScreen(
    billingManager: BillingClientManager
) {
    val context = LocalContext.current
    val connectionState by billingManager.connectionState.collectAsState()
    val logList = remember { mutableStateListOf<String>() }

    // Collect debug logs
    androidx.compose.runtime.LaunchedEffect(Unit) {
        billingManager.logs.collect { newLog ->
            logList.add(newLog)
            if (logList.size > 200) logList.removeAt(0)
        }
    }

    // Play store package inspection
    val isPlayStoreInstalled = remember {
        try {
            context.packageManager.getPackageInfo("com.android.vending", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "TEST CENTER / QA",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "Kiểm tra chẩn đoán môi trường thanh toán và kiểm thử Google Play",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        // Diagnostics Card (Section 11)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DIAGNOSTICS CHECKLIST",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentBlue
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    DiagnosticRow(
                        label = "Google Play Store cài đặt",
                        status = if (isPlayStoreInstalled) "CÓ (SẴN SÀNG)" else "KHÔNG TÌM THẤY",
                        isSuccess = isPlayStoreInstalled
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val (connStatus, connSuccess) = when (connectionState) {
                        is BillingConnectionState.Connected -> Pair("KẾT NỐI THÀNH CÔNG", true)
                        is BillingConnectionState.Connecting -> Pair("ĐANG KẾT NỐI...", false)
                        is BillingConnectionState.Disconnected -> Pair("MẤT KẾT NỐI", false)
                        is BillingConnectionState.Error -> Pair("LỖI KẾT NỐI", false)
                    }
                    DiagnosticRow(
                        label = "Billing Client Connection",
                        status = connStatus,
                        isSuccess = connSuccess
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    DiagnosticRow(
                        label = "Play Billing Test Environment",
                        status = "License Tester / Internal Track",
                        isSuccess = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { billingManager.startConnection() },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RECONNECT / CHẨN ĐOÁN LẠI", color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Debug Logs Section (Section 21)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REAL-TIME DEBUG LOG",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Button(
                    onClick = {
                        val allLogs = logList.joinToString("\n")
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("IAP Debug Log", allLogs))
                        Toast.makeText(context, "Đã sao chép Debug Log vào clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("COPY DEBUG LOG", color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (logList.isEmpty()) {
            item {
                ConsoleBox(text = "[Hệ thống] Chưa có log phát sinh. Hãy thực hiện quét hoặc kết nối lại.")
            }
        } else {
            items(logList.reversed()) { logLine ->
                ConsoleBox(text = logLine)
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    status: String,
    isSuccess: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = TextSecondary)
        Text(
            text = status,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = if (isSuccess) SuccessGreen else WarningYellow
        )
    }
}
