package com.ayakasir.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("transaction_id")]
)
data class TransactionItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "variant_id") val variantId: String, // "" for no variant
    @ColumnInfo(name = "product_name") val productName: String, // snapshot
    @ColumnInfo(name = "variant_name") val variantName: String?, // snapshot
    val qty: Int,
    @ColumnInfo(name = "unit_price") val unitPrice: Long,
    val subtotal: Long,
    val synced: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
