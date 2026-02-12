package com.ayakasir.app.core.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Product(
    val id: String,
    val categoryId: String,
    val name: String,
    val description: String? = null,
    val price: Long,
    val imagePath: String? = null,
    val isActive: Boolean = true,
    val productType: ProductType = ProductType.MENU_ITEM,
    val variants: List<Variant> = emptyList()
)
