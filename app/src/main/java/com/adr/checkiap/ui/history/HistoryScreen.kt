package com.adr.checkiap.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adr.checkiap.data.local.ScanDao
import com.adr.checkiap.data.local.ScanEntity
import com.adr.checkiap.domain.export.ReportExporter
import com.adr.checkiap.ui.theme.AccentBlue
import com.adr.checkiap.ui.theme.DarkBackground
import com.adr.checkiap.ui.theme.DarkBorder
import com.adr.checkiap.ui.theme.DarkSurface
import com.adr.checkiap.ui.theme.ErrorRed
import com.adr.checkiap.ui.theme.PurpleTrial
import com.adr.checkiap.ui.theme.TextMuted
import com.adr.checkiap.ui.theme.TextPrimary
import com.adr.checkiap.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    scanDao: ScanDao,
    onCompareSelected: (Long, Long) -> Unit
) {
    val scans by scanDao.getAllScans().collectAsState(initial = emptyList())
    val selectedIds = remember { mutableStateListOf<Long>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SCAN HISTORY",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Chọn 2 bản quét để so sánh (Diff) hoặc xuất báo cáo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }

            if (selectedIds.size == 2) {
                Button(
                    onClick = {
                        val first = selectedIds[0]
                        val second = selectedIds[1]
                        onCompareSelected(minOf(first, second), maxOf(first, second))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.CompareArrows, contentDescription = null, tint = DarkBackground)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("DIFF (2)", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (scans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Chưa có bản ghi lịch sử quét nào.", color = TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(scans) { scan ->
                    val isChecked = scan.id in selectedIds
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isChecked) selectedIds.remove(scan.id)
                                else if (selectedIds.size < 2) selectedIds.add(scan.id)
                            },
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isChecked) AccentBlue else DarkBorder
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked && selectedIds.size < 2) selectedIds.add(scan.id)
                                    else selectedIds.remove(scan.id)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = AccentBlue)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Scan #${scan.id}",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = formatter.format(Date(scan.timestamp)),
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "${scan.totalProducts} prods",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "${scan.totalTrials} trials",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = PurpleTrial
                                    )
                                }
                            }

                            // Export Markdown button
                            IconButton(
                                onClick = {
                                    val snapshot = scan.toSnapshot()
                                    val md = ReportExporter.toMarkdown(snapshot)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("IAP Report", md))
                                    Toast.makeText(context, "Đã copy Markdown Report #${scan.id}", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Export Markdown", tint = AccentBlue)
                            }

                            // Delete button
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        scanDao.deleteScan(scan.id)
                                        selectedIds.remove(scan.id)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = ErrorRed.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}
