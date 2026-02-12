package com.ayakasir.app.core.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.ayakasir.app.core.data.local.entity.TransactionEntity
import com.ayakasir.app.core.data.local.entity.TransactionItemEntity

data class TransactionWithItems(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transaction_id"
    )
    val items: List<TransactionItemEntity>
)
