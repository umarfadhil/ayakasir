package com.ayakasir.app.feature.product

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.ayakasir.app.core.domain.model.ProductType
import com.ayakasir.app.core.ui.component.ConfirmDialog
import com.ayakasir.app.core.util.CurrencyFormatter

@Composable
fun ProductListScreen(
    onAddProduct: () -> Unit,
    onEditProduct: (String) -> Unit,
    viewModel: ProductManagementViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    var deleteId by remember { mutableStateOf<String?>(null) }

    // Group products by type - Menu Items first, then Raw Materials
    val menuItems = remember(products) {
        products.filter { it.productType == ProductType.MENU_ITEM }
    }
    val rawMaterials = remember(products) {
        products.filter { it.productType == ProductType.RAW_MATERIAL }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProduct) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah Produk")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text(
                text = "Daftar Produk",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (products.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada produk", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Menu Items Section
                    if (menuItems.isNotEmpty()) {
                        item {
                            Text(
                                text = "Menu Item",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(menuItems, key = { it.id }) { product ->
                            ProductCard(
                                product = product,
                                onEdit = { onEditProduct(product.id) },
                                onDelete = { deleteId = product.id }
                            )
                        }
                    }

                    // Raw Materials Section
                    if (rawMaterials.isNotEmpty()) {
                        item {
                            Text(
                                text = "Bahan Baku",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }
                        items(rawMaterials, key = { it.id }) { product ->
                            ProductCard(
                                product = product,
                                onEdit = { onEditProduct(product.id) },
                                onDelete = { deleteId = product.id }
                            )
                        }
                    }
                }
            }
        }
    }

    deleteId?.let { id ->
        ConfirmDialog(
            title = "Hapus Produk",
            message = "Yakin ingin menghapus produk ini?",
            onConfirm = { viewModel.deleteProduct(id); deleteId = null },
            onDismiss = { deleteId = null }
        )
    }
}

@Composable
private fun ProductCard(
    product: com.ayakasir.app.core.domain.model.Product,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(CurrencyFormatter.format(product.price), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (product.variants.isNotEmpty()) {
                    Text("${product.variants.size} varian", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
