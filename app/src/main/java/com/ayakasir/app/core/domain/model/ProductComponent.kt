package com.ayakasir.app.core.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class ProductComponent(
    val id: String,
    val componentProductId: String,
    val componentVariantId: String,
    val componentProductName: String,
    val componentVariantName: String?,
    val requiredQty: Int,
    val unit: String
)
