package com.ayakasir.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ayakasir.app.core.domain.model.SyncStatus

@Entity(tableName = "restaurants")
data class RestaurantEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "owner_email") val ownerEmail: String,
    @ColumnInfo(name = "owner_phone") val ownerPhone: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "sync_status") val syncStatus: String = SyncStatus.PENDING.name,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
