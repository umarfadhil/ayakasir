package com.ayakasir.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.ayakasir.app.core.session.SessionManager
import com.ayakasir.app.core.sync.SyncScheduler
import com.ayakasir.app.core.ui.theme.AyaKasirTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Schedule periodic sync
        syncScheduler.schedulePeriodicSync()

        setContent {
            AyaKasirTheme {
                val navController = rememberNavController()
                MainScaffold(
                    navController = navController,
                    sessionManager = sessionManager
                )
            }
        }
    }
}
