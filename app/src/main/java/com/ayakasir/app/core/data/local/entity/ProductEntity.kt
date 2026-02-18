package com.ayakasir.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ayakasir.app.core.domain.model.SyncStatus

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("category_id")]
)
data class ProductEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    val name: String,
    val description: String? = null,
    val price: Long,
    @ColumnInfo(name = "image_path") val imagePath: String? = null,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "product_type") val productType: String = "MENU_ITEM",
    @ColumnInfo(name = "restaurant_id") val restaurantId: String = "",
    @ColumnInfo(name = "sync_status") val syncStatus: String = SyncStatus.PENDING.name,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
