package com.ayakasir.app.core.data.local.relation

data class GoodsReceivingItemWithProduct(
    val id: String,
    val receivingId: String,
    val productId: String,
    val variantId: String,
    val qty: Int,
    val costPerUnit: Long,
    val unit: String,
    val productName: String?,
    val variantName: String?
)
