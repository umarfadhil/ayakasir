package com.ayakasir.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ayakasir.app.core.domain.model.SyncStatus

@Entity(
    tableName = "cash_withdrawals",
    indices = [
        Index("user_id"),
        Index("date"),
        Index("sync_status")
    ]
)
data class CashWithdrawalEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val amount: Long,
    val reason: String,
    val date: Long,
    @ColumnInfo(name = "restaurant_id") val restaurantId: String = "",
    @ColumnInfo(name = "sync_status") val syncStatus: String = SyncStatus.PENDING.name,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
