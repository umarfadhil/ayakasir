package com.ayakasir.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ayakasir.app.core.domain.model.SyncStatus

@Entity(
    tableName = "transactions",
    indices = [
        Index("user_id"),
        Index("date"),
        Index("sync_status")
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val date: Long,
    val total: Long,
    @ColumnInfo(name = "payment_method") val paymentMethod: String, // CASH or QRIS
    val status: String, // COMPLETED or VOIDED
    @ColumnInfo(name = "restaurant_id") val restaurantId: String = "",
    @ColumnInfo(name = "sync_status") val syncStatus: String = SyncStatus.PENDING.name,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
