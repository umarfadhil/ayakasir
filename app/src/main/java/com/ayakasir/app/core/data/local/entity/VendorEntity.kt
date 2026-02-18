package com.ayakasir.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ayakasir.app.core.domain.model.SyncStatus

@Entity(tableName = "vendors")
data class VendorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    @ColumnInfo(name = "restaurant_id") val restaurantId: String = "",
    @ColumnInfo(name = "sync_status") val syncStatus: String = SyncStatus.PENDING.name,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
