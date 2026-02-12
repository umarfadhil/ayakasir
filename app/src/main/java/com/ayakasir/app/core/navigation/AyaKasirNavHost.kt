package com.ayakasir.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ayakasir.app.feature.auth.LoginScreen
import com.ayakasir.app.feature.dashboard.DashboardScreen
import com.ayakasir.app.feature.inventory.InventoryScreen
import com.ayakasir.app.feature.pos.PosScreen
import com.ayakasir.app.feature.product.CategoryFormScreen
import com.ayakasir.app.feature.product.CategoryListScreen
import com.ayakasir.app.feature.product.ProductFormScreen
import com.ayakasir.app.feature.product.ProductListScreen
import com.ayakasir.app.feature.purchasing.GoodsReceivingFormScreen
import com.ayakasir.app.feature.purchasing.GoodsReceivingListScreen
import com.ayakasir.app.feature.purchasing.VendorFormScreen
import com.ayakasir.app.feature.purchasing.VendorListScreen
import com.ayakasir.app.feature.settings.InitialBalanceSettingScreen
import com.ayakasir.app.feature.settings.PrinterSettingsScreen
import com.ayakasir.app.feature.settings.QrisSettingsScreen
import com.ayakasir.app.feature.settings.SettingsScreen
import com.ayakasir.app.feature.settings.UserManagementScreen

@Composable
fun AyaKasirNavHost(
    navController: NavHostController,
    startDestination: Screen,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<Screen.Login> {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Pos) {
                        popUpTo(Screen.Login) { inclusive = true }
                    }
                }
            )
        }

        composable<Screen.Pos> {
            PosScreen()
        }

        composable<Screen.Dashboard> {
            DashboardScreen()
        }

        composable<Screen.Inventory> {
            InventoryScreen()
        }

        composable<Screen.VendorList> {
            VendorListScreen(
                onAddVendor = { navController.navigate(Screen.VendorForm) },
                onEditVendor = { id -> navController.navigate(Screen.VendorFormEdit(id)) }
            )
        }

        composable<Screen.VendorForm> {
            VendorFormScreen(
                vendorId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.VendorFormEdit> { backStackEntry ->
            val vendorId = backStackEntry.arguments?.getString("vendorId")
            VendorFormScreen(
                vendorId = vendorId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.GoodsReceivingList> {
            GoodsReceivingListScreen(
                onAddReceiving = { navController.navigate(Screen.GoodsReceivingForm) },
                onEditReceiving = { id -> navController.navigate(Screen.GoodsReceivingFormEdit(id)) }
            )
        }

        composable<Screen.GoodsReceivingForm> {
            GoodsReceivingFormScreen(
                receivingId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.GoodsReceivingFormEdit> { backStackEntry ->
            val receivingId = backStackEntry.arguments?.getString("receivingId")
            GoodsReceivingFormScreen(
                receivingId = receivingId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.ProductList> {
            ProductListScreen(
                onAddProduct = { navController.navigate(Screen.ProductForm) },
                onEditProduct = { id -> navController.navigate(Screen.ProductFormEdit(id)) }
            )
        }

        composable<Screen.ProductForm> {
            ProductFormScreen(
                productId = null,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable<Screen.ProductFormEdit> { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId")
            ProductFormScreen(
                productId = productId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable<Screen.CategoryList> {
            CategoryListScreen(
                onNavigateBack = { navController.popBackStack() },
                onAddCategory = { navController.navigate(Screen.CategoryForm) },
                onEditCategory = { id -> navController.navigate(Screen.CategoryFormEdit(id)) }
            )
        }

        composable<Screen.CategoryForm> {
            CategoryFormScreen(
                categoryId = null,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable<Screen.CategoryFormEdit> { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getString("categoryId")
            CategoryFormScreen(
                categoryId = categoryId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable<Screen.Settings> {
            SettingsScreen(
                onNavigateToPrinter = { navController.navigate(Screen.PrinterSettings) },
                onNavigateToInitialBalance = { navController.navigate(Screen.InitialBalanceSetting) },
                onNavigateToUsers = { navController.navigate(Screen.UserManagement) },
                onNavigateToCategories = { navController.navigate(Screen.CategoryList) },
                onNavigateToQris = { navController.navigate(Screen.QrisSettings) }
            )
        }

        composable<Screen.InitialBalanceSetting> {
            InitialBalanceSettingScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.PrinterSettings> {
            PrinterSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.QrisSettings> {
            QrisSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.UserManagement> {
            UserManagementScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
