package com.ayakasir.app.core.domain.model

data class GoodsReceiving(
    val id: String,
    val vendorId: String,
    val vendorName: String? = null,
    val date: Long,
    val notes: String? = null,
    val items: List<GoodsReceivingItem> = emptyList()
)
