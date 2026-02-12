package com.ayakasir.app.core.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.ayakasir.app.core.data.local.entity.GoodsReceivingEntity
import com.ayakasir.app.core.data.local.entity.GoodsReceivingItemEntity

data class GoodsReceivingWithItems(
    @Embedded val receiving: GoodsReceivingEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "receiving_id"
    )
    val items: List<GoodsReceivingItemEntity>
)
