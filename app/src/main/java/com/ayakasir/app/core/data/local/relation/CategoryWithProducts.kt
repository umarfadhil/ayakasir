package com.ayakasir.app.core.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.ayakasir.app.core.data.local.entity.CategoryEntity
import com.ayakasir.app.core.data.local.entity.ProductEntity

data class CategoryWithProducts(
    @Embedded val category: CategoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "category_id"
    )
    val products: List<ProductEntity>
)
