package com.ayakasir.app.feature.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayakasir.app.core.domain.model.CartItem
import com.ayakasir.app.core.domain.model.DiscountType
import com.ayakasir.app.core.domain.model.PaymentMethod
import com.ayakasir.app.core.domain.model.Product
import com.ayakasir.app.core.ui.component.CashBalanceCard
import com.ayakasir.app.core.ui.component.CashBalanceDetailDialog
import com.ayakasir.app.core.ui.component.CashWithdrawalDialog
import com.ayakasir.app.core.ui.component.ConfirmDialog
import com.ayakasir.app.core.util.CurrencyFormatter
import com.ayakasir.app.core.util.DateTimeUtil
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    viewModel: PosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val cashBalance by viewModel.cashBalance.collectAsStateWithLifecycle()
    val isQrisConfigured by viewModel.isQrisConfigured.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var discountTarget by remember { mutableStateOf<CartItem?>(null) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left pane: Balance + Categories + Product Grid
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            // Cash Balance Card
            CashBalanceCard(
                balance = cashBalance.currentBalance,
                onClick = { viewModel.showBalanceDetail() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category tabs
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedCategoryId == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("Semua") }
                    )
                }
                items(categories) { category ->
                    FilterChip(
                        selected = uiState.selectedCategoryId == category.id,
                        onClick = { viewModel.selectCategory(category.id) },
                        label = { Text(category.name) }
                    )
                }
            }

            // Product grid
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
            if (products.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Belum ada produk", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(products, key = { it.id }) { product ->
                        ProductCard(
                            product = product,
                            onClick = { viewModel.onProductClick(product) }
                        )
                    }
                }
            }
            } // PullToRefreshBox
        }

        VerticalDivider()

        // Right pane: Cart
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            Text(
                text = "Keranjang",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.cart.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Keranjang kosong", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.cart, key = { "${it.productId}:${it.variantId}" }) { item ->
                        CartItemRow(
                            item = item,
                            onIncrease = { viewModel.updateCartItemQty(item.productId, item.variantId, item.qty + 1) },
                            onDecrease = { viewModel.updateCartItemQty(item.productId, item.variantId, item.qty - 1) },
                            onRemove = { viewModel.removeFromCart(item.productId, item.variantId) },
                            onEditDiscount = { discountTarget = item }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    CurrencyFormatter.format(uiState.cartTotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.clearCart() },
                    enabled = uiState.cart.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Hapus") }
                Button(
                    onClick = { viewModel.checkout(PaymentMethod.CASH) },
                    enabled = uiState.cart.isNotEmpty() && !uiState.isProcessing,
                    modifier = Modifier.weight(1f)
                ) { Text("Bayar Tunai") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.startQrisPayment() },
                enabled = uiState.cart.isNotEmpty() && !uiState.isProcessing && isQrisConfigured && uiState.qrisPayment == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Bayar QRIS")
            }

            if (!isQrisConfigured) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "QRIS belum dikonfigurasi. Atur di Pengaturan > QRIS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = uiState.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { viewModel.showCashWithdrawalDialog() },
                enabled = !uiState.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tarik Tunai")
            }
        }
    }

    // Variant selector bottom sheet
    uiState.showVariantSelector?.let { product ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissVariantSelector() },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pilih varian:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                product.variants.forEach { variant ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { viewModel.addToCart(product, variant) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(variant.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                CurrencyFormatter.format(product.price + variant.priceAdjustment),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Cash withdrawal dialog
    if (uiState.showCashWithdrawalDialog) {
        CashWithdrawalDialog(
            onDismiss = { viewModel.dismissCashWithdrawalDialog() },
            onWithdraw = { amount, reason ->
                viewModel.showWithdrawalConfirmation(amount, reason)
            }
        )
    }

    // Withdrawal confirmation dialog
    if (uiState.showWithdrawalConfirmation) {
        ConfirmDialog(
            title = "Konfirmasi Penarikan Tunai",
            message = "Tarik tunai ${CurrencyFormatter.format(uiState.pendingWithdrawal?.amount ?: 0L)}?\n\nAlasan: ${uiState.pendingWithdrawal?.reason}",
            confirmText = "Ya, Tarik",
            dismissText = "Batal",
            onConfirm = { viewModel.confirmCashWithdrawal() },
            onDismiss = { viewModel.dismissWithdrawalConfirmation() }
        )
    }

    // Cash balance detail dialog
    if (uiState.showBalanceDetail) {
        CashBalanceDetailDialog(
            balance = cashBalance,
            onDismiss = { viewModel.dismissBalanceDetail() }
        )
    }

    discountTarget?.let { item ->
        DiscountDialog(
            item = item,
            onDismiss = { discountTarget = null },
            onApply = { type, value ->
                viewModel.updateCartItemDiscount(item.productId, item.variantId, type, value)
                discountTarget = null
            }
        )
    }

    uiState.qrisPayment?.let { payment ->
        Dialog(onDismissRequest = { viewModel.dismissQrisPayment() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Pembayaran QRIS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (!payment.merchantName.isNullOrBlank()) {
                        Text(
                            text = payment.merchantName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = payment.providerName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (payment.qrCodeData.isNotBlank()) {
                        AsyncImage(
                            model = payment.qrCodeData,
                            contentDescription = "QRIS",
                            modifier = Modifier.height(440.dp).width(440.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = "QRIS tidak tersedia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = "Total: ${CurrencyFormatter.format(payment.amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    payment.expiresAt?.let { expiresAt ->
                        Text(
                            text = "Berlaku sampai ${DateTimeUtil.formatTime(expiresAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "Minta pelanggan scan QR, lalu konfirmasi setelah pembayaran berhasil.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.dismissQrisPayment() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Batal")
                        }
                        Button(
                            onClick = { viewModel.confirmQrisPayment() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Konfirmasi")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: Product, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = CurrencyFormatter.format(product.price),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            if (product.variants.isNotEmpty()) {
                Text(
                    text = "${product.variants.size} varian",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CartItemRow(
    item: CartItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
    onEditDiscount: () -> Unit
) {
    val hasDiscount = item.discountType != DiscountType.NONE && item.discountValue > 0

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.productName + if (item.variantName != null) " (${item.variantName})" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = CurrencyFormatter.format(item.unitPrice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasDiscount) {
                    Text(
                        text = "Setelah diskon: ${CurrencyFormatter.format(item.discountedUnitPrice)} / item",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrease) {
                    Icon(Icons.Filled.Remove, contentDescription = "Kurang")
                }
                Text(
                    text = "${item.qty}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = onIncrease) {
                    Icon(Icons.Filled.Add, contentDescription = "Tambah")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                text = CurrencyFormatter.format(item.subtotal),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(80.dp),
                textAlign = TextAlign.End
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val discountLabel = when (item.discountType) {
                DiscountType.NONE -> "Diskon: -"
                DiscountType.AMOUNT -> "Diskon: ${CurrencyFormatter.format(item.discountPerUnit)} / item"
                DiscountType.PERCENT -> "Diskon: ${item.discountValue}%"
            }
            Text(
                text = discountLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onEditDiscount) {
                Text(if (hasDiscount) "Ubah Diskon" else "Tambah Diskon")
            }
        }
    }
}

@Composable
private fun DiscountDialog(
    item: CartItem,
    onDismiss: () -> Unit,
    onApply: (DiscountType, Long) -> Unit
) {
    var selectedType by rememberSaveable(item.productId, item.variantId) {
        mutableStateOf(item.discountType)
    }
    var valueText by rememberSaveable(item.productId, item.variantId, item.discountType, item.discountValue) {
        val initial = if (item.discountType == DiscountType.NONE || item.discountValue == 0L) "" else item.discountValue.toString()
        mutableStateOf(initial)
    }

    val parsedValue = valueText.toLongOrNull() ?: 0L
    val normalizedValue = when (selectedType) {
        DiscountType.NONE -> 0L
        DiscountType.AMOUNT -> parsedValue.coerceAtLeast(0L).coerceAtMost(item.unitPrice)
        DiscountType.PERCENT -> parsedValue.coerceIn(0L, 100L)
    }

    val previewDiscountPerUnit = when (selectedType) {
        DiscountType.NONE -> 0L
        DiscountType.AMOUNT -> normalizedValue
        DiscountType.PERCENT -> (item.unitPrice * normalizedValue) / 100L
    }
    val previewUnitPrice = (item.unitPrice - previewDiscountPerUnit).coerceAtLeast(0L)
    val previewSubtotal = previewUnitPrice * item.qty

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Diskon Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = item.productName + if (item.variantName != null) " (${item.variantName})" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedType == DiscountType.NONE,
                        onClick = {
                            selectedType = DiscountType.NONE
                            valueText = ""
                        },
                        label = { Text("Tidak Ada") }
                    )
                    FilterChip(
                        selected = selectedType == DiscountType.AMOUNT,
                        onClick = { selectedType = DiscountType.AMOUNT },
                        label = { Text("Rp") }
                    )
                    FilterChip(
                        selected = selectedType == DiscountType.PERCENT,
                        onClick = { selectedType = DiscountType.PERCENT },
                        label = { Text("%") }
                    )
                }

                OutlinedTextField(
                    value = valueText,
                    onValueChange = { input ->
                        if (selectedType != DiscountType.NONE) {
                            valueText = input.filter { it.isDigit() }
                        }
                    },
                    label = {
                        Text(
                            when (selectedType) {
                                DiscountType.PERCENT -> "Diskon (%)"
                                DiscountType.AMOUNT -> "Diskon (Rp)"
                                DiscountType.NONE -> "Diskon"
                            }
                        )
                    },
                    enabled = selectedType != DiscountType.NONE,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    supportingText = {
                        Text(
                            when (selectedType) {
                                DiscountType.PERCENT -> "Maksimal 100%"
                                DiscountType.AMOUNT -> "Maksimal ${CurrencyFormatter.format(item.unitPrice)} per item"
                                DiscountType.NONE -> "Pilih jenis diskon"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Subtotal setelah diskon: ${CurrencyFormatter.format(previewSubtotal)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selectedType, normalizedValue) }) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
