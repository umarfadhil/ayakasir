package com.ayakasir.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ayakasir.app.core.domain.model.UserFeature
import com.ayakasir.app.core.domain.model.UserFeatureAccess
import com.ayakasir.app.core.domain.model.UserRole
import com.ayakasir.app.core.navigation.AyaKasirNavHost
import com.ayakasir.app.core.navigation.Screen
import com.ayakasir.app.core.session.SessionManager
import kotlinx.coroutines.launch

data class NavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val feature: UserFeature,
    val adminOnly: Boolean = false
)

val navItems = listOf(
    NavItem(Screen.Pos, "Kasir", Icons.Filled.PointOfSale, feature = UserFeature.POS),
    NavItem(Screen.Dashboard, "Dashboard", Icons.Filled.Dashboard, feature = UserFeature.DASHBOARD, adminOnly = true),
    NavItem(Screen.ProductList, "Menu", Icons.Filled.Restaurant, feature = UserFeature.MENU, adminOnly = true),
    NavItem(Screen.Inventory, "Stok", Icons.Filled.Inventory2, feature = UserFeature.INVENTORY),
    NavItem(Screen.GoodsReceivingList, "Pembelian", Icons.Filled.ShoppingCart, feature = UserFeature.PURCHASING, adminOnly = true),
    NavItem(Screen.Settings, "Pengaturan", Icons.Filled.Settings, feature = UserFeature.SETTINGS, adminOnly = true)
)

@Composable
fun MainScaffold(
    navController: NavHostController,
    sessionManager: SessionManager,
    authStartDestination: Screen
) {
    val currentUser by sessionManager.currentUser.collectAsStateWithLifecycle()
    val isOwner = currentUser?.role == UserRole.OWNER
    val user = currentUser
    val allowedFeatures = when {
        isOwner -> UserFeatureAccess.allFeatures
        user?.featureAccess?.isNotEmpty() == true -> user.featureAccess
        else -> UserFeatureAccess.defaultCashierFeatures
    }
    val visibleItems = navItems.filter { allowedFeatures.contains(it.feature) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Don't show nav rail on auth screens
    val isAuthScreen = currentRoute?.contains("Landing") == true ||
        currentRoute?.contains("Login") == true ||
        currentRoute?.contains("EmailLogin") == true ||
        currentRoute?.contains("Registration") == true

    val scope = rememberCoroutineScope()
    val handleLogout: () -> Unit = {
        scope.launch {
            sessionManager.logout()
        }
        navController.navigate(Screen.Landing) {
            popUpTo(0) { inclusive = true }
        }
    }

    if (isAuthScreen || currentUser == null) {
        AyaKasirNavHost(
            navController = navController,
            startDestination = authStartDestination,
            onLogout = handleLogout,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                visibleItems.forEach { item ->
                    NavigationRailItem(
                        selected = currentRoute?.contains(item.screen::class.simpleName ?: "") == true,
                        onClick = {
                            navController.navigate(item.screen) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }

            Scaffold { innerPadding ->
                AyaKasirNavHost(
                    navController = navController,
                    startDestination = Screen.Pos,
                    onLogout = handleLogout,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}
