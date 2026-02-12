package com.ayakasir.app.core.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    private val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))

    fun format(amount: Long): String {
        return "Rp${formatter.format(amount)}"
    }

    fun formatCompact(amount: Long): String {
        return when {
            amount >= 1_000_000_000 -> "Rp${formatter.format(amount / 1_000_000_000)}M"
            amount >= 1_000_000 -> "Rp${formatter.format(amount / 1_000_000)}jt"
            amount >= 1_000 -> "Rp${formatter.format(amount / 1_000)}rb"
            else -> format(amount)
        }
    }
}
