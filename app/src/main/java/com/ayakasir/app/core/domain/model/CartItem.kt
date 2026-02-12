package com.ayakasir.app.core.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class CartItem(
    val productId: String,
    val productName: String,
    val variantId: String?,
    val variantName: String?,
    val qty: Int,
    val unitPrice: Long,
    val discountType: DiscountType = DiscountType.NONE,
    val discountValue: Long = 0L
) {
    val discountPerUnit: Long
        get() = when (discountType) {
            DiscountType.NONE -> 0L
            DiscountType.AMOUNT -> discountValue.coerceAtLeast(0L).coerceAtMost(unitPrice)
            DiscountType.PERCENT -> {
                val percent = discountValue.coerceIn(0L, 100L)
                (unitPrice * percent) / 100L
            }
        }

    val discountedUnitPrice: Long
        get() = (unitPrice - discountPerUnit).coerceAtLeast(0L)

    val subtotal: Long
        get() = discountedUnitPrice * qty

    val discountTotal: Long
        get() = discountPerUnit * qty
}
