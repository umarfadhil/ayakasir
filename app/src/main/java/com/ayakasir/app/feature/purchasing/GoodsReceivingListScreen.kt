package com.ayakasir.app.feature.purchasing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayakasir.app.core.util.CurrencyFormatter
import com.ayakasir.app.core.util.DateTimeUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoodsReceivingListScreen(
    onAddReceiving: () -> Unit,
    onEditReceiving: (String) -> Unit = {},
    viewModel: PurchasingViewModel = hiltViewModel()
) {
    val receivingList by viewModel.receivingList.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var receivingToDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddReceiving) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah Penerimaan Barang")
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            Text("Penerimaan Barang", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            if (receivingList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada penerimaan barang", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(receivingList, key = { it.id }) { receiving ->
                        var isExpanded by remember { mutableStateOf(false) }

                        Card(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            DateTimeUtil.formatDate(receiving.date),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${receiving.items.size} item",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onEditReceiving(receiving.id) }
                                        ) {
                                            Icon(
                                                Icons.Filled.Edit,
                                                contentDescription = "Edit",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                receivingToDelete = receiving.id
                                                showDeleteDialog = true
                                            }
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Hapus",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Tutup" else "Buka",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    receiving.vendorName ?: "Vendor tidak diketahui",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (receiving.notes != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        receiving.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Expandable items detail section
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(modifier = Modifier.padding(top = 12.dp)) {
                                        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                                        Text(
                                            "Detail Item",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        receiving.items.forEach { item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        item.productName ?: "Produk ID: ${item.productId.take(8)}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    if (!item.variantName.isNullOrBlank()) {
                                                        Text(
                                                            item.variantName,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Text(
                                                        "${item.qty} ${item.unit}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Text(
                                                    CurrencyFormatter.format(item.costPerUnit),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            if (item != receiving.items.last()) {
                                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                            }
                                        }

                                        // Total calculation
                                        val total = receiving.items.sumOf { it.qty * it.costPerUnit }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "Total",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                CurrencyFormatter.format(total),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        } // PullToRefreshBox

        // Delete confirmation dialog
        if (showDeleteDialog && receivingToDelete != null) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    receivingToDelete = null
                },
                title = { Text("Hapus Penerimaan Barang") },
                text = { Text("Apakah Anda yakin ingin menghapus penerimaan barang ini? Stok barang akan dikurangi sesuai dengan jumlah yang diterima.") },
                confirmButton = {
                    Button(
                        onClick = {
                            receivingToDelete?.let { viewModel.deleteReceiving(it) }
                            showDeleteDialog = false
                            receivingToDelete = null
                        }
                    ) {
                        Text("Hapus")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            receivingToDelete = null
                        }
                    ) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}
