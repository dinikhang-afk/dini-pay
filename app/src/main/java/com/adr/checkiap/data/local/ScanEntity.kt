package com.adr.checkiap.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.adr.checkiap.data.model.ProductModel
import com.adr.checkiap.data.model.ScanSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "scan_history")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long,
    val totalProducts: Int,
    val totalSubs: Int,
    val totalTrials: Int,
    val totalDiscounts: Int,
    val productsJson: String,
    val logsJson: String
) {
    fun toSnapshot(): ScanSnapshot {
        val json = Json { ignoreUnknownKeys = true }
        val products = try {
            json.decodeFromString<List<ProductModel>>(productsJson)
        } catch (_: Exception) {
            emptyList()
        }
        val logs = try {
            json.decodeFromString<List<String>>(logsJson)
        } catch (_: Exception) {
            emptyList()
        }
        return ScanSnapshot(
            id = id,
            timestamp = timestamp,
            totalProducts = totalProducts,
            totalSubs = totalSubs,
            totalTrials = totalTrials,
            totalDiscounts = totalDiscounts,
            products = products,
            logs = logs
        )
    }

    companion object {
        fun fromSnapshot(snapshot: ScanSnapshot): ScanEntity {
            val json = Json { ignoreUnknownKeys = true }
            return ScanEntity(
                id = snapshot.id,
                timestamp = snapshot.timestamp,
                totalProducts = snapshot.totalProducts,
                totalSubs = snapshot.totalSubs,
                totalTrials = snapshot.totalTrials,
                totalDiscounts = snapshot.totalDiscounts,
                productsJson = json.encodeToString(snapshot.products),
                logsJson = json.encodeToString(snapshot.logs)
            )
        }
    }
}
