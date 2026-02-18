package com.ayakasir.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.ayakasir.app.core.domain.model.SyncStatus

@Entity(
    tableName = "inventory",
    primaryKeys = ["product_id", "variant_id"]
)
data class InventoryEntity(
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "variant_id") val variantId: String, // "" for no variant
    @ColumnInfo(name = "current_qty") val currentQty: Int = 0,
    @ColumnInfo(name = "min_qty") val minQty: Int = 0,
    @ColumnInfo(name = "restaurant_id") val restaurantId: String = "",
    @ColumnInfo(name = "sync_status") val syncStatus: String = SyncStatus.PENDING.name,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
