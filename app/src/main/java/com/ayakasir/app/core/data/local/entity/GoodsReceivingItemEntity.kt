package com.ayakasir.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "goods_receiving_items",
    foreignKeys = [
        ForeignKey(
            entity = GoodsReceivingEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiving_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("receiving_id")]
)
data class GoodsReceivingItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "receiving_id") val receivingId: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "variant_id") val variantId: String, // "" for no variant
    val qty: Int,
    @ColumnInfo(name = "cost_per_unit") val costPerUnit: Long,
    val unit: String = "pcs", // pcs, kg, liter, or custom
    val synced: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
