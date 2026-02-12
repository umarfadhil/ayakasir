package com.ayakasir.app.core.domain.model

data class InventoryItem(
    val productId: String,
    val variantId: String,
    val productName: String,
    val variantName: String?,
    val categoryId: String,
    val categoryName: String,
    val currentQty: Int,
    val minQty: Int
) {
    val isLowStock: Boolean get() = currentQty <= minQty
}
