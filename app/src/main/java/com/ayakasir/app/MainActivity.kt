package com.ayakasir.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.ayakasir.app.core.navigation.Screen
import com.ayakasir.app.core.session.SessionManager
import com.ayakasir.app.core.sync.SyncScheduler
import com.ayakasir.app.core.ui.theme.AyaKasirTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var syncScheduler: SyncScheduler

    private var startDestination by mutableStateOf<Screen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Schedule periodic sync
        syncScheduler.schedulePeriodicSync()

        // Determine start destination based on persisted session
        lifecycleScope.launch {
            val persistedUserId = sessionManager.getPersistedUserId()
            startDestination = if (persistedUserId != null) {
                // User did full login before and hasn't logged out → PIN unlock
                Screen.Login
            } else {
                // No persisted session → full auth flow (Landing → EmailLogin)
                Screen.Landing
            }
        }

        setContent {
            AyaKasirTheme {
                val dest = startDestination
                if (dest != null) {
                    val navController = rememberNavController()
                    MainScaffold(
                        navController = navController,
                        sessionManager = sessionManager,
                        authStartDestination = dest
                    )
                }
            }
        }
    }
}
