package com.ayakasir.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ayakasir.app.core.domain.model.SyncStatus

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "category_type") val categoryType: String = "MENU",
    @ColumnInfo(name = "restaurant_id") val restaurantId: String = "",
    @ColumnInfo(name = "sync_status") val syncStatus: String = SyncStatus.PENDING.name,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
