package com.ayakasir.app.core.domain.model

import com.ayakasir.app.core.util.CurrencyFormatter
import com.ayakasir.app.core.util.UnitConverter

data class InventoryItem(
    val productId: String,
    val variantId: String,
    val productName: String,
    val variantName: String?,
    val categoryId: String,
    val categoryName: String,
    val currentQty: Int,
    val minQty: Int,
    val unit: String = "pcs",
    val avgCogs: Long = 0L,  // Rp per base unit (g, mL, or pcs)
    val itemValue: Long = 0L // avgCogs × currentQty
) {
    val isLowStock: Boolean get() = currentQty <= minQty
    val displayQty: String get() = UnitConverter.formatForDisplay(currentQty, unit)
    val displayMinQty: String get() = UnitConverter.formatForDisplay(minQty, unit)

    /** Average COGS formatted in the natural display unit (per kg, per L, or per pcs). */
    val displayAvgCogs: String get() = if (avgCogs == 0L) "-" else when (unit) {
        "g" -> "${CurrencyFormatter.format(avgCogs * 1000)}/kg"
        "mL" -> "${CurrencyFormatter.format(avgCogs * 1000)}/L"
        else -> "${CurrencyFormatter.format(avgCogs)}/pcs"
    }

    val displayItemValue: String get() = CurrencyFormatter.format(itemValue)
}
