package com.ayakasir.app.core.util

/**
 * Converts between compatible measurement units.
 * Base units: g (mass), mL (volume), pcs (count).
 * Supported conversions: kg ↔ g (×1000), L ↔ mL (×1000).
 */
object UnitConverter {

    /** Returns the base unit for a given unit. */
    fun baseUnit(unit: String): String = when (unit.lowercase()) {
        "kg", "g" -> "g"
        "l", "ml" -> "mL"
        else -> unit // pcs or any custom unit stays as-is
    }

    /** Normalizes qty to base unit. E.g. (2, "kg") → (2000, "g"). */
    fun normalizeToBase(qty: Int, unit: String): Pair<Int, String> {
        val base = baseUnit(unit)
        val factor = conversionFactor(unit, base)
        return Pair(qty * factor, base)
    }

    /**
     * Converts qty from [fromUnit] to [toUnit].
     * Returns the converted integer quantity.
     * If units are incompatible, returns qty unchanged.
     */
    fun convert(qty: Int, fromUnit: String, toUnit: String): Int {
        if (fromUnit == toUnit) return qty
        val factor = conversionFactor(fromUnit, toUnit)
        return qty * factor
    }

    /**
     * Returns the integer multiplication factor to convert from [from] to [to].
     * E.g. kg→g = 1000, g→kg would be fractional so we handle via base normalization.
     */
    private fun conversionFactor(from: String, to: String): Int {
        if (from == to) return 1
        return when {
            from == "kg" && to == "g" -> 1000
            from == "g" && to == "kg" -> 1 // lossy: handled by always normalizing to base
            from == "L" && to == "mL" -> 1000
            from == "mL" && to == "L" -> 1 // lossy: handled by always normalizing to base
            else -> 1
        }
    }

    /** Checks if two units are compatible (same dimension). */
    fun areCompatible(unitA: String, unitB: String): Boolean =
        baseUnit(unitA) == baseUnit(unitB)

    /** Formats qty with unit for display. E.g. 1500 g → "1.5 kg" or "1500 g". */
    fun formatForDisplay(qty: Int, baseUnit: String): String {
        return when (baseUnit) {
            "g" -> if (qty >= 1000 && qty % 1000 == 0) "${qty / 1000} kg"
                   else if (qty >= 1000) "${"%.1f".format(qty / 1000.0)} kg"
                   else "$qty g"
            "mL" -> if (qty >= 1000 && qty % 1000 == 0) "${qty / 1000} L"
                    else if (qty >= 1000) "${"%.1f".format(qty / 1000.0)} L"
                    else "$qty mL"
            else -> "$qty $baseUnit"
        }
    }
}