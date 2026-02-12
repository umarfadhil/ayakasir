package com.ayakasir.app.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ayakasir.app.core.util.CurrencyFormatter

@Composable
fun CashWithdrawalDialog(
    onDismiss: () -> Unit,
    onWithdraw: (amount: Long, reason: String) -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var amountString by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    val amount = amountString.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (step == 1) "Tarik Tunai - Jumlah" else "Tarik Tunai - Alasan",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (step == 1) {
                    // Step 1: Amount entry with numeric keypad
                    Text(
                        text = CurrencyFormatter.format(amount),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    )

                    NumericKeypad(
                        onDigitClick = { digit ->
                            // Limit to reasonable amount (999,999,999)
                            if (amountString.length < 9) {
                                amountString += digit
                            }
                        },
                        onBackspace = {
                            if (amountString.isNotEmpty()) {
                                amountString = amountString.dropLast(1)
                            }
                        },
                        onConfirm = {
                            if (amount > 0) {
                                step = 2
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Step 2: Reason entry
                    Text(
                        text = "Jumlah: ${CurrencyFormatter.format(amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Alasan Penarikan") },
                        placeholder = { Text("Contoh: Bayar supplier, Belanja operasional") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Masukkan alasan penarikan tunai untuk audit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (step == 1) {
                        if (amount > 0) {
                            step = 2
                        }
                    } else {
                        if (reason.isNotBlank()) {
                            onWithdraw(amount, reason)
                        }
                    }
                },
                enabled = if (step == 1) amount > 0 else reason.isNotBlank()
            ) {
                Text(if (step == 1) "Lanjut" else "Tarik Tunai")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (step == 2) {
                    step = 1
                } else {
                    onDismiss()
                }
            }) {
                Text(if (step == 2) "Kembali" else "Batal")
            }
        }
    )
}
