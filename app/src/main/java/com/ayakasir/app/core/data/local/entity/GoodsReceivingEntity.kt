package com.ayakasir.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "goods_receiving",
    foreignKeys = [
        ForeignKey(
            entity = VendorEntity::class,
            parentColumns = ["id"],
            childColumns = ["vendor_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("vendor_id")]
)
data class GoodsReceivingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "vendor_id") val vendorId: String?,
    val date: Long,
    val notes: String? = null,
    val synced: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
