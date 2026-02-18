package com.ayakasir.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ayakasir.app.core.domain.model.SyncStatus

@Entity(
    tableName = "product_components",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_product_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["component_product_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("parent_product_id"),
        Index("component_product_id")
    ]
)
data class ProductComponentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "parent_product_id") val parentProductId: String,
    @ColumnInfo(name = "component_product_id") val componentProductId: String,
    @ColumnInfo(name = "component_variant_id") val componentVariantId: String,
    @ColumnInfo(name = "required_qty") val requiredQty: Int,
    val unit: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "restaurant_id") val restaurantId: String = "",
    @ColumnInfo(name = "sync_status") val syncStatus: String = SyncStatus.PENDING.name,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
