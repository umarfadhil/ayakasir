package com.ayakasir.app.core.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Variant(
    val id: String,
    val productId: String,
    val name: String,
    val priceAdjustment: Long = 0
)
