package com.ayakasir.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ayakasir.app.feature.auth.EmailLoginScreen
import com.ayakasir.app.feature.auth.LandingScreen
import com.ayakasir.app.feature.auth.LoginScreen
import com.ayakasir.app.feature.auth.RegistrationScreen
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
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<Screen.Landing> {
            LandingScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.EmailLogin)
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Registration)
                }
            )
        }

        composable<Screen.Registration> {
            RegistrationScreen(
                onRegistrationSuccess = {
                    navController.navigate(Screen.Landing) {
                        popUpTo(Screen.Registration) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<Screen.EmailLogin> {
            EmailLoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Pos) {
                        popUpTo(Screen.EmailLogin) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<Screen.Login> {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Pos) {
                        popUpTo(Screen.Login) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
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
                onNavigateBack = { navController.popBackStack() },
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
                onNavigateBack = { navController.popBackStack() },
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
                onNavigateToVendors = { navController.navigate(Screen.VendorList) },
                onNavigateToProducts = { navController.navigate(Screen.ProductList) },
                onNavigateToQris = { navController.navigate(Screen.QrisSettings) },
                onLogout = onLogout
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
