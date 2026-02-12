package com.ayakasir.app.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ayakasir.app.core.domain.model.CashBalance
import com.ayakasir.app.core.util.CurrencyFormatter

@Composable
fun CashBalanceDetailDialog(
    balance: CashBalance,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detail Saldo Kas") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BalanceDetailRow("Saldo Awal", balance.initialBalance)
                HorizontalDivider()
                BalanceDetailRow("Penjualan Tunai", balance.totalCashSales, isPositive = true)
                BalanceDetailRow("Penarikan Tunai", balance.totalWithdrawals, isNegative = true)
                HorizontalDivider()
                BalanceDetailRow(
                    "Saldo Saat Ini",
                    balance.currentBalance,
                    isBold = true,
                    color = if (balance.currentBalance >= 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
private fun BalanceDetailRow(
    label: String,
    amount: Long,
    isPositive: Boolean = false,
    isNegative: Boolean = false,
    isBold: Boolean = false,
    color: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = when {
                isPositive -> "+ ${CurrencyFormatter.format(amount)}"
                isNegative -> "- ${CurrencyFormatter.format(amount)}"
                else -> CurrencyFormatter.format(amount)
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = color
        )
    }
}
