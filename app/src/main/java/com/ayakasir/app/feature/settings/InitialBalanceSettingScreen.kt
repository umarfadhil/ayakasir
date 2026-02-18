package com.ayakasir.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayakasir.app.core.ui.component.ConfirmDialog
import com.ayakasir.app.core.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitialBalanceSettingScreen(
    onNavigateBack: () -> Unit,
    viewModel: InitialBalanceViewModel = hiltViewModel()
) {
    val currentBalance by viewModel.initialBalance.collectAsStateWithLifecycle()
    var amountText by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saldo Awal Kas") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Saldo awal adalah titik awal perhitungan saldo kas. Ubah hanya saat melakukan penyesuaian saldo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Saldo Awal Saat Ini",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        CurrencyFormatter.format(currentBalance),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { char -> char.isDigit() } },
                label = { Text("Saldo Awal Baru") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    if (amountText.isNotEmpty()) {
                        Text("= ${CurrencyFormatter.format(amountText.toLongOrNull() ?: 0L)}")
                    }
                }
            )

            Button(
                onClick = { showConfirmDialog = true },
                enabled = amountText.isNotEmpty() && amountText.toLongOrNull() != currentBalance,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Simpan Perubahan")
            }
        }
    }

    if (showConfirmDialog) {
        val newAmount = amountText.toLongOrNull() ?: 0L
        ConfirmDialog(
            title = "Ubah Saldo Awal?",
            message = "Saldo awal akan diubah menjadi ${CurrencyFormatter.format(newAmount)}.\n\nPerubahan ini akan mempengaruhi perhitungan saldo kas.",
            confirmText = "Ya, Ubah",
            dismissText = "Batal",
            onConfirm = {
                viewModel.saveInitialBalance(newAmount)
                showConfirmDialog = false
                onNavigateBack()
            },
            onDismiss = { showConfirmDialog = false }
        )
    }
}
