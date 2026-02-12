package com.ayakasir.app.core.domain.model

data class GoodsReceivingItem(
    val id: String,
    val receivingId: String,
    val productId: String,
    val variantId: String,
    val productName: String? = null,
    val variantName: String? = null,
    val qty: Int,
    val costPerUnit: Long,
    val unit: String = "pcs"
)
