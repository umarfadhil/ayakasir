package com.ayakasir.app.core.domain.model

enum class UserFeature(
    val label: String,
    val description: String
) {
    POS("Kasir", "Transaksi penjualan dan pembayaran"),
    DASHBOARD("Dashboard", "Ringkasan penjualan"),
    MENU("Menu", "Kelola menu dan kategori"),
    INVENTORY("Stok", "Lihat dan penyesuaian stok"),
    PURCHASING("Pembelian", "Penerimaan barang dan vendor"),
    SETTINGS("Pengaturan", "Akses pengaturan aplikasi")
}

object UserFeatureAccess {
    val allFeatures: Set<UserFeature> = UserFeature.values().toSet()
    val defaultCashierFeatures: Set<UserFeature> = setOf(UserFeature.POS, UserFeature.INVENTORY)

    fun serialize(features: Set<UserFeature>): String? {
        if (features.isEmpty()) return null
        return features
            .sortedBy { it.ordinal }
            .joinToString(",") { it.name }
    }

    fun parse(value: String?): Set<UserFeature> {
        if (value.isNullOrBlank()) return emptySet()
        return value.split(",")
            .mapNotNull { raw ->
                runCatching { UserFeature.valueOf(raw.trim()) }.getOrNull()
            }
            .toSet()
    }
}
