package com.ayakasir.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "pin_hash") val pinHash: String,
    @ColumnInfo(name = "pin_salt") val pinSalt: String,
    val role: String, // OWNER or CASHIER
    @ColumnInfo(name = "feature_access") val featureAccess: String? = null,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    val synced: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
