package com.ayakasir.app.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayakasir.app.core.domain.model.InventoryItem
import com.ayakasir.app.core.domain.model.PaymentMethod
import com.ayakasir.app.core.domain.model.Transaction
import com.ayakasir.app.core.util.CurrencyFormatter
import com.ayakasir.app.core.util.DateTimeUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val todayTotal by viewModel.todayTotal.collectAsStateWithLifecycle()
    val todayCount by viewModel.todayCount.collectAsStateWithLifecycle()
    val todayCash by viewModel.todayCash.collectAsStateWithLifecycle()
    val todayQris by viewModel.todayQris.collectAsStateWithLifecycle()
    val lowStockCount by viewModel.lowStockCount.collectAsStateWithLifecycle()
    val lowStockItems by viewModel.lowStockItems.collectAsStateWithLifecycle()
    val todaySales by viewModel.todaySales.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val productSummary = remember(todaySales) { buildProductSummary(todaySales) }
    var showLowStockDialog by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 600
    val contentPadding = if (isCompact) 16.dp else 24.dp
    val cardSpacing = if (isCompact) 12.dp else 16.dp

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(cardSpacing)
    ) {
        item {
            Column {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(cardSpacing)) {
                        StatCard(
                            title = "Penjualan Hari Ini",
                            value = CurrencyFormatter.format(todayTotal),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = 16.dp
                        )
                        StatCard(
                            title = "Jumlah Transaksi",
                            value = "$todayCount",
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = 16.dp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(cardSpacing)
                    ) {
                        StatCard(
                            title = "Penjualan Hari Ini",
                            value = CurrencyFormatter.format(todayTotal),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Jumlah Transaksi",
                            value = "$todayCount",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(cardSpacing)) {
                        StatCard(
                            title = "Tunai",
                            value = CurrencyFormatter.format(todayCash),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = 16.dp
                        )
                        StatCard(
                            title = "QRIS",
                            value = CurrencyFormatter.format(todayQris),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = 16.dp
                        )
                        StatCard(
                            title = "Stok Rendah",
                            value = "$lowStockCount item",
                            modifier = Modifier.fillMaxWidth(),
                            isAlert = lowStockCount > 0,
                            onClick = { showLowStockDialog = true },
                            contentPadding = 16.dp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(cardSpacing)
                    ) {
                        StatCard(
                            title = "Tunai",
                            value = CurrencyFormatter.format(todayCash),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "QRIS",
                            value = CurrencyFormatter.format(todayQris),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Stok Rendah",
                            value = "$lowStockCount item",
                            modifier = Modifier.weight(1f),
                            isAlert = lowStockCount > 0,
                            onClick = { showLowStockDialog = true }
                        )
                    }
                }
            }
        }

        item {
            SalesSummaryCard(transactions = todaySales)
        }

        item {
            ProductSummaryCard(summary = productSummary)
        }
    }
    } // PullToRefreshBox

    if (showLowStockDialog) {
        LowStockDialog(
            items = lowStockItems,
            onDismiss = { showLowStockDialog = false }
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    isAlert: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: androidx.compose.ui.unit.Dp = 20.dp
) {
    val cardColors = CardDefaults.cardColors(
        containerColor = if (isAlert) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceVariant
    )

    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(contentPadding)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isAlert) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isAlert) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            colors = cardColors
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier,
            colors = cardColors
        ) {
            content()
        }
    }
}

@Composable
private fun SalesSummaryCard(
    transactions: List<Transaction>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Ringkasan Penjualan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (transactions.isEmpty()) {
                Text(
                    text = "Belum ada penjualan hari ini",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SalesTableHeader()
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                transactions.forEachIndexed { index, transaction ->
                    SalesTableRow(transaction = transaction)
                    if (index != transactions.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Waktu",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.7f)
        )
        Text(
            text = "Metode",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.9f)
        )
        Text(
            text = "Detail",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(2f)
        )
        Text(
            text = "Total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f)
        )
    }
}

@Composable
private fun SalesTableRow(transaction: Transaction) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        val itemsSubtotal = transaction.items.sumOf { it.subtotal }
        Text(
            text = DateTimeUtil.formatTime(transaction.date),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.7f)
        )
        Text(
            text = paymentMethodLabel(transaction.paymentMethod),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.9f)
        )
        Column(
            modifier = Modifier.weight(2f)
        ) {
            if (transaction.items.isEmpty()) {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                transaction.items.forEach { item ->
                    Text(
                        text = formatItemLine(
                            item.productName,
                            item.variantName,
                            item.qty,
                            item.unitPrice,
                            item.subtotal
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Text(
            text = CurrencyFormatter.format(itemsSubtotal),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f)
        )
    }
}

private fun paymentMethodLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> "Tunai"
    PaymentMethod.QRIS -> "QRIS"
}

private fun formatItemLine(
    productName: String,
    variantName: String?,
    qty: Int,
    unitPrice: Long,
    subtotal: Long
): String {
    val variantLabel = if (!variantName.isNullOrBlank()) " (${variantName})" else ""
    val unitLabel = CurrencyFormatter.format(unitPrice)
    val subtotalLabel = CurrencyFormatter.format(subtotal)
    return "$productName$variantLabel x$qty @ $unitLabel = $subtotalLabel"
}

@Composable
private fun LowStockDialog(
    items: List<InventoryItem>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stok Rendah") },
        text = {
            if (items.isEmpty()) {
                Text(
                    text = "Tidak ada item dengan stok rendah saat ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    Text(
                        text = "Item yang perlu dibeli:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items, key = { "${it.productId}:${it.variantId}" }) { item ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = inventoryItemLabel(item),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Stok ${item.currentQty}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

private fun inventoryItemLabel(item: InventoryItem): String {
    val variantLabel = if (!item.variantName.isNullOrBlank()) " (${item.variantName})" else ""
    return item.productName + variantLabel
}

private data class ProductSummaryRow(
    val label: String,
    val qty: Int,
    val total: Long
)

private fun buildProductSummary(transactions: List<Transaction>): List<ProductSummaryRow> {
    val summaryMap = linkedMapOf<String, ProductSummaryRow>()
    transactions.forEach { transaction ->
        transaction.items.forEach { item ->
            val label = if (!item.variantName.isNullOrBlank()) {
                "${item.productName} (${item.variantName})"
            } else {
                item.productName
            }
            val existing = summaryMap[label]
            summaryMap[label] = if (existing == null) {
                ProductSummaryRow(label = label, qty = item.qty, total = item.subtotal)
            } else {
                existing.copy(qty = existing.qty + item.qty, total = existing.total + item.subtotal)
            }
        }
    }
    return summaryMap.values.sortedWith(
        compareByDescending<ProductSummaryRow> { it.qty }.thenByDescending { it.total }
    )
}

@Composable
private fun ProductSummaryCard(
    summary: List<ProductSummaryRow>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Ringkasan Produk Terjual",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (summary.isEmpty()) {
                Text(
                    text = "Belum ada penjualan hari ini",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ProductSummaryHeader()
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                summary.forEachIndexed { index, row ->
                    ProductSummaryRowItem(row = row)
                    if (index != summary.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductSummaryHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Produk",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(2.2f)
        )
        Text(
            text = "Qty",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.8f)
        )
        Text(
            text = "Total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
private fun ProductSummaryRowItem(row: ProductSummaryRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(2.2f)
        )
        Text(
            text = row.qty.toString(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.8f)
        )
        Text(
            text = CurrencyFormatter.format(row.total),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )
    }
}
