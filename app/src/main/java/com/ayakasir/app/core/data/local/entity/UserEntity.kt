package com.ayakasir.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ayakasir.app.core.domain.model.SyncStatus

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    @ColumnInfo(name = "pin_hash") val pinHash: String,
    @ColumnInfo(name = "pin_salt") val pinSalt: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String? = null,
    @ColumnInfo(name = "password_salt") val passwordSalt: String? = null,
    val role: String, // OWNER or CASHIER
    @ColumnInfo(name = "restaurant_id") val restaurantId: String? = null,
    @ColumnInfo(name = "feature_access") val featureAccess: String? = null,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "sync_status") val syncStatus: String = SyncStatus.PENDING.name,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
