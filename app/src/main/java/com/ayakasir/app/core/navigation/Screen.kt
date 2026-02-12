package com.ayakasir.app.core.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen {
    @Serializable data object Login : Screen
    @Serializable data object Pos : Screen
    @Serializable data object Dashboard : Screen
    @Serializable data object Inventory : Screen
    @Serializable data object VendorList : Screen
    @Serializable data object VendorForm : Screen
    @Serializable data class VendorFormEdit(val vendorId: String) : Screen
    @Serializable data object GoodsReceivingList : Screen
    @Serializable data object GoodsReceivingForm : Screen
    @Serializable data class GoodsReceivingFormEdit(val receivingId: String) : Screen
    @Serializable data object ProductList : Screen
    @Serializable data object ProductForm : Screen
    @Serializable data class ProductFormEdit(val productId: String) : Screen
    @Serializable data object CategoryList : Screen
    @Serializable data object CategoryForm : Screen
    @Serializable data class CategoryFormEdit(val categoryId: String) : Screen
    @Serializable data object Settings : Screen
    @Serializable data object InitialBalanceSetting : Screen
    @Serializable data object PrinterSettings : Screen
    @Serializable data object QrisSettings : Screen
    @Serializable data object UserManagement : Screen
}
