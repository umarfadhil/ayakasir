package com.ayakasir.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ayakasir.app.core.domain.model.SyncStatus

@Entity(
    tableName = "general_ledger",
    indices = [
        Index("restaurant_id"),
        Index("type"),
        Index("date"),
        Index("reference_id"),
        Index("sync_status")
    ]
)
data class GeneralLedgerEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "restaurant_id") val restaurantId: String = "",
    val type: String,
    val amount: Long,
    @ColumnInfo(name = "reference_id") val referenceId: String? = null,
    val description: String,
    val date: Long,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "sync_status") val syncStatus: String = SyncStatus.PENDING.name,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)