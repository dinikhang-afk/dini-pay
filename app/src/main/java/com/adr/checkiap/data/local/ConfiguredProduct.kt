package com.adr.checkiap.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.adr.checkiap.data.model.ProductType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "configured_products")
data class ConfiguredProductEntity(
    @PrimaryKey
    val productId: String,
    val productType: ProductType
)

@Dao
interface ProductConfigDao {
    @Query("SELECT * FROM configured_products ORDER BY productId ASC")
    fun getAll(): Flow<List<ConfiguredProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ConfiguredProductEntity)

    @Query("DELETE FROM configured_products WHERE productId = :productId")
    suspend fun delete(productId: String)

    @Query("SELECT COUNT(*) FROM configured_products")
    suspend fun count(): Int
}
