package com.ayakasir.app.core.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.ayakasir.app.core.data.local.entity.GoodsReceivingEntity
import com.ayakasir.app.core.data.local.entity.GoodsReceivingItemEntity
import com.ayakasir.app.core.data.local.entity.VendorEntity

data class GoodsReceivingWithVendorAndItems(
    @Embedded val receiving: GoodsReceivingEntity,
    @Relation(
        parentColumn = "vendor_id",
        entityColumn = "id"
    )
    val vendor: VendorEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "receiving_id"
    )
    val items: List<GoodsReceivingItemEntity>
)
