package com.ayakasir.app.feature.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.ayakasir.app.core.domain.model.Product
import com.ayakasir.app.core.domain.model.ProductType
import com.ayakasir.app.core.ui.component.ConfirmDialog
import com.ayakasir.app.core.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    onNavigateBack: () -> Unit,
    onAddProduct: () -> Unit,
    onEditProduct: (String) -> Unit,
    viewModel: ProductManagementViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val menuCategories by viewModel.menuCategories.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var deleteId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }

    val menuItems = remember(products) {
        products.filter { it.productType == ProductType.MENU_ITEM }
    }
    val normalizedQuery = searchQuery.trim()
    val filteredMenuItems = remember(menuItems, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            menuItems
        } else {
            menuItems.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
        }
    }
    val groupedMenuItems = remember(filteredMenuItems, menuCategories) {
        val categoryById = menuCategories.associateBy { it.id }
        buildList {
            menuCategories.forEach { category ->
                val sectionItems = filteredMenuItems.filter { it.categoryId == category.id }
                if (sectionItems.isNotEmpty()) {
                    add(MenuSection(key = category.id, title = category.name, items = sectionItems))
                }
            }

            val uncategorizedItems = filteredMenuItems.filter { item ->
                item.categoryId.isBlank() || categoryById[item.categoryId] == null
            }
            if (uncategorizedItems.isNotEmpty()) {
                add(MenuSection(key = "uncategorized", title = "Tanpa Kategori", items = uncategorizedItems))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daftar Produk") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isSearchVisible) {
                                isSearchVisible = false
                                searchQuery = ""
                            } else {
                                isSearchVisible = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSearchVisible) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (isSearchVisible) "Tutup Pencarian" else "Cari Menu"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProduct) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah Produk")
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                if (isSearchVisible) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Cari Judul Menu") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (menuItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada produk", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (filteredMenuItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Menu tidak ditemukan", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        groupedMenuItems.forEach { section ->
                            item(key = "header_${section.key}") {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(section.items, key = { it.id }) { product ->
                                ProductCard(
                                    product = product,
                                    onEdit = { onEditProduct(product.id) },
                                    onClone = { viewModel.cloneProduct(product.id) },
                                    onDelete = { deleteId = product.id }
                                )
                            }
                        }
                    }
                }
            }
        } // PullToRefreshBox
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

private data class MenuSection(
    val key: String,
    val title: String,
    val items: List<Product>
)

@Composable
private fun ProductCard(
    product: Product,
    onEdit: () -> Unit,
    onClone: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClone) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Clone")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
