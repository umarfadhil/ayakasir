package com.ayakasir.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayakasir.app.core.ui.component.NumericKeypad
import com.ayakasir.app.core.ui.component.PinInputField

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 600

    // Responsive sizing
    val horizontalPadding = if (isCompact) 24.dp else 48.dp
    val verticalPadding = if (isCompact) 16.dp else 48.dp
    val titleSpacing = if (isCompact) 4.dp else 8.dp
    val afterTitleSpacing = if (isCompact) 24.dp else 40.dp
    val beforeKeypadSpacing = if (isCompact) 16.dp else 24.dp
    val afterKeypadSpacing = if (isCompact) 12.dp else 24.dp
    val keypadMaxWidth = if (isCompact) 280.dp else 320.dp

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
            onLoginSuccess()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AyaKasir",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(titleSpacing))

            Text(
                text = "Masukkan PIN untuk masuk",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(afterTitleSpacing))

            PinInputField(
                pinLength = 6,
                filledCount = uiState.pin.length
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                // Spacer to keep layout stable
                Text(
                    text = " ",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(beforeKeypadSpacing))

            NumericKeypad(
                onDigitClick = { viewModel.onDigitEntered(it) },
                onBackspace = { viewModel.onBackspace() },
                onConfirm = { },
                modifier = Modifier.widthIn(max = keypadMaxWidth),
                isCompact = isCompact
            )

            Spacer(modifier = Modifier.height(afterKeypadSpacing))

            if (uiState.isLoading) {
                Text(
                    text = "Memverifikasi...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
