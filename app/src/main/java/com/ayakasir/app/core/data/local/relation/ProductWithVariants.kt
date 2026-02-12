package com.ayakasir.app.core.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.ayakasir.app.core.data.local.entity.ProductEntity
import com.ayakasir.app.core.data.local.entity.VariantEntity

data class ProductWithVariants(
    @Embedded val product: ProductEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "product_id"
    )
    val variants: List<VariantEntity>
)
