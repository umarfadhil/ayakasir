package com.ayakasir.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index("user_id"),
        Index("date"),
        Index("synced")
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val date: Long,
    val total: Long,
    @ColumnInfo(name = "payment_method") val paymentMethod: String, // CASH or QRIS
    val status: String, // COMPLETED or VOIDED
    val synced: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
