package com.ayakasir.app.feature.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayakasir.app.core.domain.model.ProductType
import com.ayakasir.app.core.ui.component.CategoryFormDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    productId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: ProductManagementViewModel = hiltViewModel()
) {
    val form by viewModel.productForm.collectAsStateWithLifecycle()
    val allCategories by viewModel.categories.collectAsStateWithLifecycle()
    val allProducts by viewModel.products.collectAsStateWithLifecycle()
    var showCategoryDialog by remember { mutableStateOf(false) }

    // Filter categories based on selected product type
    val categories = remember(form.productType, allCategories) {
        allCategories.filter { category ->
            when (form.productType) {
                ProductType.MENU_ITEM -> category.categoryType == com.ayakasir.app.core.domain.model.CategoryType.MENU
                ProductType.RAW_MATERIAL -> category.categoryType == com.ayakasir.app.core.domain.model.CategoryType.RAW_MATERIAL
            }
        }
    }

    // Filter products for components - only show raw materials
    val rawMaterialProducts = remember(allProducts) {
        allProducts.filter { it.productType == ProductType.RAW_MATERIAL }
    }

    LaunchedEffect(productId) {
        viewModel.resetProductForm()
        productId?.let { viewModel.loadProduct(it) }
    }

    LaunchedEffect(form.isSaved) {
        if (form.isSaved) onSaved()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (productId != null) "Edit Produk" else "Tambah Produk") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { viewModel.onProductNameChange(it) },
                label = { Text("Nama Produk") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = form.description,
                onValueChange = { viewModel.onProductDescriptionChange(it) },
                label = { Text("Deskripsi") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            OutlinedTextField(
                value = form.price,
                onValueChange = { viewModel.onProductPriceChange(it) },
                label = { Text("Harga (Rp)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Product type selection
            Text("Jenis Produk", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = form.productType == ProductType.MENU_ITEM,
                        onClick = { viewModel.onProductTypeChange(ProductType.MENU_ITEM) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Menu Item")
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = form.productType == ProductType.RAW_MATERIAL,
                        onClick = { viewModel.onProductTypeChange(ProductType.RAW_MATERIAL) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Bahan Baku")
                }
            }

            // Category dropdown
            var expanded by remember { mutableStateOf(false) }
            val selectedCategory = categories.find { it.id == form.categoryId }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "Pilih Kategori",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    // Add new category option
                    DropdownMenuItem(
                        text = {
                            Row {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("+ Kategori Baru")
                            }
                        },
                        onClick = {
                            showCategoryDialog = true
                            expanded = false
                        }
                    )
                    HorizontalDivider()

                    // Existing categories
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = { viewModel.onProductCategoryChange(cat.id); expanded = false }
                        )
                    }
                }
            }

            // Variants
            Text("Varian", style = MaterialTheme.typography.titleSmall)
            form.variants.forEachIndexed { index, variant ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = variant.name,
                        onValueChange = { viewModel.onVariantNameChange(index, it) },
                        label = { Text("Nama Varian") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = variant.priceAdjustment,
                        onValueChange = { viewModel.onVariantPriceChange(index, it) },
                        label = { Text("+/- Harga") },
                        modifier = Modifier.weight(0.7f),
                        singleLine = true
                    )
                    IconButton(onClick = { viewModel.removeVariant(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Hapus varian")
                    }
                }
            }
            TextButton(onClick = { viewModel.addVariant() }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(" Tambah Varian")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Components/Ingredients Section
            Text("Bahan Baku", style = MaterialTheme.typography.titleMedium)
            form.components.forEachIndexed { index, comp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Product dropdown
                        var productExpanded by remember { mutableStateOf(false) }
                        val selectedProduct = rawMaterialProducts.find { it.id == comp.productId }
                        ExposedDropdownMenuBox(
                            expanded = productExpanded,
                            onExpandedChange = { productExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedProduct?.name ?: "Pilih Bahan",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Bahan") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(productExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = productExpanded,
                                onDismissRequest = { productExpanded = false }
                            ) {
                                rawMaterialProducts.forEach { product ->
                                    DropdownMenuItem(
                                        text = { Text(product.name) },
                                        onClick = {
                                            viewModel.updateComponent(index, product.id, "", comp.qty, comp.unit)
                                            productExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Variant dropdown (if selected product has variants)
                        if (selectedProduct?.variants?.isNotEmpty() == true) {
                            var variantExpanded by remember { mutableStateOf(false) }
                            val selectedVariant = selectedProduct.variants.find { it.id == comp.variantId }
                            ExposedDropdownMenuBox(
                                expanded = variantExpanded,
                                onExpandedChange = { variantExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = selectedVariant?.name ?: "Pilih Varian",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Varian") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(variantExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = variantExpanded,
                                    onDismissRequest = { variantExpanded = false }
                                ) {
                                    selectedProduct.variants.forEach { variant ->
                                        DropdownMenuItem(
                                            text = { Text(variant.name) },
                                            onClick = {
                                                viewModel.updateComponent(index, comp.productId, variant.id, comp.qty, comp.unit)
                                                variantExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Quantity input
                            OutlinedTextField(
                                value = comp.qty,
                                onValueChange = { newQty ->
                                    viewModel.updateComponent(index, comp.productId, comp.variantId, newQty, comp.unit)
                                },
                                label = { Text("Jumlah") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            // Unit dropdown
                            var unitExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = unitExpanded,
                                onExpandedChange = { unitExpanded = it },
                                modifier = Modifier.weight(0.8f)
                            ) {
                                OutlinedTextField(
                                    value = comp.unit,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Satuan") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    singleLine = true
                                )
                                ExposedDropdownMenu(
                                    expanded = unitExpanded,
                                    onDismissRequest = { unitExpanded = false }
                                ) {
                                    listOf("pcs", "ml", "g", "kg", "L").forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit) },
                                            onClick = {
                                                viewModel.updateComponent(index, comp.productId, comp.variantId, comp.qty, unit)
                                                unitExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Remove button
                        TextButton(
                            onClick = { viewModel.removeComponent(index) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Hapus Bahan")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Add component button
            OutlinedButton(
                onClick = { viewModel.addComponent() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Tambah Bahan Baku")
            }

            if (form.error != null) {
                Text(form.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.saveProduct(productId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !form.isLoading
            ) {
                Text(if (form.isLoading) "Menyimpan..." else "Simpan")
            }
        }
    }

    // Category dialog
    if (showCategoryDialog) {
        CategoryFormDialog(
            onDismiss = { showCategoryDialog = false },
            onSave = { name, sortOrder ->
                viewModel.onCategoryNameChange(name)
                viewModel.onCategorySortOrderChange(sortOrder.toString())
                // Set category type based on selected product type
                val categoryType = when (form.productType) {
                    ProductType.MENU_ITEM -> com.ayakasir.app.core.domain.model.CategoryType.MENU
                    ProductType.RAW_MATERIAL -> com.ayakasir.app.core.domain.model.CategoryType.RAW_MATERIAL
                }
                viewModel.onCategoryTypeChange(categoryType)
                viewModel.saveCategory(null)
                showCategoryDialog = false
            }
        )
    }
}
