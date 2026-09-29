package com.adr.checkiap.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scan_history WHERE id = :scanId LIMIT 1")
    suspend fun getScanById(scanId: Long): ScanEntity?

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestScan(): ScanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanEntity): Long

    @Query("DELETE FROM scan_history WHERE id = :scanId")
    suspend fun deleteScan(scanId: Long)

    @Query("DELETE FROM scan_history")
    suspend fun clearAll()
}
