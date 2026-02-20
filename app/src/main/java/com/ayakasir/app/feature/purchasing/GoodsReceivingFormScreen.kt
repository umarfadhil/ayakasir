package com.ayakasir.app.feature.purchasing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ayakasir.app.core.util.CurrencyFormatter
import com.ayakasir.app.core.util.DateTimeUtil
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoodsReceivingFormScreen(
    receivingId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: PurchasingViewModel = hiltViewModel()
) {
    val form by viewModel.receivingForm.collectAsStateWithLifecycle()
    val vendors by viewModel.vendors.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val currentItem by viewModel.currentItemInput.collectAsStateWithLifecycle()

    LaunchedEffect(receivingId) {
        if (receivingId != null) {
            viewModel.loadReceiving(receivingId)
        } else {
            viewModel.resetReceivingForm()
        }
    }

    LaunchedEffect(form.isSaved) {
        if (form.isSaved) onNavigateBack()
    }

    val isEditing = receivingId != null

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = form.date)

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.onReceivingDateChange(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (isEditing) "Edit Penerimaan Barang" else "Penerimaan Barang Baru") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Vendor dropdown with add new option
            var vendorExpanded by remember { mutableStateOf(false) }
            val selectedVendor = vendors.find { it.id == form.vendorId }
            ExposedDropdownMenuBox(
                expanded = vendorExpanded,
                onExpandedChange = { vendorExpanded = it }) {
                OutlinedTextField(
                    value = selectedVendor?.name ?: "Pilih Vendor",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Vendor") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(vendorExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    isError = form.error != null && form.vendorId == null
                )
                ExposedDropdownMenu(
                    expanded = vendorExpanded,
                    onDismissRequest = { vendorExpanded = false }) {
                    vendors.forEach { vendor ->
                        DropdownMenuItem(
                            text = { Text(vendor.name) },
                            onClick = {
                                viewModel.onReceivingVendorChange(vendor.id); vendorExpanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("+ Tambah Vendor Baru") },
                        onClick = { viewModel.showAddVendorForm(); vendorExpanded = false }
                    )
                }
            }

            // Inline add vendor form
            if (form.showAddVendor) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Vendor Baru", style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = form.newVendorName,
                            onValueChange = { viewModel.onNewVendorNameChange(it) },
                            label = { Text("Nama Vendor") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.hideAddVendorForm() }) {
                                Text("Batal")
                            }
                            Button(
                                onClick = { viewModel.createAndSelectVendor() },
                                enabled = form.newVendorName.isNotBlank()
                            ) {
                                Text("Simpan")
                            }
                        }
                    }
                }
            }

            // Tanggal
            OutlinedTextField(
                value = DateTimeUtil.formatDate(form.date),
                onValueChange = {},
                readOnly = true,
                label = { Text("Tanggal") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Pilih Tanggal")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Notes
            OutlinedTextField(
                value = form.notes,
                onValueChange = { viewModel.onReceivingNotesChange(it) },
                label = { Text("Catatan (opsional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            // Item input section
            Text("Tambah Item Barang", style = MaterialTheme.typography.titleSmall)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Kategori dropdown
                    var categoryExpanded by remember { mutableStateOf(false) }
                    val selectedCategory = categories.find { it.id == currentItem.categoryId }
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = it }) {
                        OutlinedTextField(
                            value = selectedCategory?.name ?: "Pilih Kategori",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Kategori") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded)
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        viewModel.onCurrentItemCategoryChange(category.id, category.name)
                                        categoryExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("+ Tambah Kategori Baru") },
                                onClick = { viewModel.showAddCategoryForm(); categoryExpanded = false }
                            )
                        }
                    }

                    // Inline add category form
                    if (currentItem.showAddCategory) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = currentItem.newCategoryName,
                                onValueChange = { viewModel.onNewCategoryNameChange(it) },
                                label = { Text("Nama Kategori Baru") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(
                                onClick = { viewModel.createAndSelectCategory() },
                                enabled = currentItem.newCategoryName.isNotBlank()
                            ) { Text("OK") }
                            TextButton(onClick = { viewModel.hideAddCategoryForm() }) { Text("Batal") }
                        }
                    }

                    // Nama Barang dropdown (filtered by category)
                    var productExpanded by remember { mutableStateOf(false) }
                    val selectedProduct = products.find { it.id == currentItem.productId }
                    val filteredProducts = if (currentItem.categoryId.isNotBlank()) {
                        products.filter { it.categoryId == currentItem.categoryId }
                    } else {
                        products
                    }
                    ExposedDropdownMenuBox(
                        expanded = productExpanded,
                        onExpandedChange = { productExpanded = it }) {
                        OutlinedTextField(
                            value = selectedProduct?.name ?: "Pilih Nama Barang",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Nama Barang") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    productExpanded
                                )
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = productExpanded,
                            onDismissRequest = { productExpanded = false }) {
                            filteredProducts.forEach { product ->
                                DropdownMenuItem(
                                    text = { Text(product.name) },
                                    onClick = {
                                        viewModel.onCurrentItemProductChange(product)
                                        productExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("+ Tambah Barang Baru") },
                                onClick = { viewModel.showAddProductForm(); productExpanded = false }
                            )
                        }
                    }

                    // Inline add product form
                    if (currentItem.showAddProduct) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = currentItem.newProductName,
                                onValueChange = { viewModel.onNewProductNameChange(it) },
                                label = { Text("Nama Barang Baru") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(
                                onClick = { viewModel.createAndSelectProduct() },
                                enabled = currentItem.newProductName.isNotBlank()
                            ) { Text("OK") }
                            TextButton(onClick = { viewModel.hideAddProductForm() }) { Text("Batal") }
                        }
                    }

                    // Variant dropdown (if product has variants)
                    if (selectedProduct != null && selectedProduct.variants.isNotEmpty()) {
                        var variantExpanded by remember { mutableStateOf(false) }
                        val selectedVariant =
                            selectedProduct.variants.find { it.id == currentItem.variantId }
                        ExposedDropdownMenuBox(
                            expanded = variantExpanded,
                            onExpandedChange = { variantExpanded = it }) {
                            OutlinedTextField(
                                value = selectedVariant?.name ?: "Pilih Varian",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Varian") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        variantExpanded
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = variantExpanded,
                                onDismissRequest = { variantExpanded = false }) {
                                selectedProduct.variants.forEach { variant ->
                                    DropdownMenuItem(
                                        text = { Text(variant.name) },
                                        onClick = {
                                            viewModel.onCurrentItemVariantChange(variant.id)
                                            variantExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Jumlah and Satuan on same row
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = currentItem.qty,
                            onValueChange = { viewModel.onCurrentItemQtyChange(it) },
                            label = { Text("Jumlah") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        var unitExpanded by remember { mutableStateOf(false) }
                        val unitOptions = listOf("pcs", "ml", "g", "kg", "L")
                        ExposedDropdownMenuBox(
                            expanded = unitExpanded,
                            onExpandedChange = { if (!currentItem.unitLocked) unitExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = currentItem.unit,
                                onValueChange = { if (!currentItem.unitLocked) viewModel.onCurrentItemUnitChange(it) },
                                label = { Text("Satuan") },
                                readOnly = currentItem.unitLocked,
                                trailingIcon = {
                                    if (!currentItem.unitLocked) {
                                        ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                singleLine = true
                            )
                            if (!currentItem.unitLocked) {
                                ExposedDropdownMenu(
                                    expanded = unitExpanded,
                                    onDismissRequest = { unitExpanded = false }) {
                                    unitOptions.forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit) },
                                            onClick = {
                                                viewModel.onCurrentItemUnitChange(unit)
                                                unitExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Harga Total
                    OutlinedTextField(
                        value = currentItem.totalPrice,
                        onValueChange = { viewModel.onCurrentItemTotalPriceChange(it) },
                        label = { Text("Harga Total (Rp)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Tambahkan button
                    OutlinedButton(
                        onClick = { viewModel.addReceivingItem() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(" Tambahkan")
                    }
                }
            }

            // Added items list
            if (form.items.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    "Daftar Item (${form.items.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                form.items.forEachIndexed { index, item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.productName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${item.categoryName} · ${item.qty} ${item.unit} · ${
                                        CurrencyFormatter.format(
                                            item.costPerUnit
                                        )
                                    }",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.removeReceivingItem(index) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Hapus",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            if (form.error != null) {
                Text(
                    form.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.saveReceiving() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !form.isLoading && form.items.isNotEmpty()
            ) {
                Text(if (form.isLoading) "Menyimpan..." else "Simpan")
            }
        }
    }
}
