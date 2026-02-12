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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayakasir.app.core.domain.model.CategoryType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFormScreen(
    categoryId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: ProductManagementViewModel = hiltViewModel()
) {
    val form by viewModel.categoryForm.collectAsStateWithLifecycle()

    LaunchedEffect(categoryId) {
        viewModel.resetCategoryForm()
        categoryId?.let { viewModel.loadCategory(it) }
    }

    LaunchedEffect(form.isSaved) {
        if (form.isSaved) onSaved()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (categoryId != null) "Edit Kategori" else "Tambah Kategori") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { viewModel.onCategoryNameChange(it) },
                label = { Text("Nama Kategori") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = form.sortOrder,
                onValueChange = { viewModel.onCategorySortOrderChange(it) },
                label = { Text("Urutan") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Category type selection
            Text("Jenis Kategori", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = form.categoryType == CategoryType.MENU,
                        onClick = { viewModel.onCategoryTypeChange(CategoryType.MENU) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Kategori Menu")
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = form.categoryType == CategoryType.RAW_MATERIAL,
                        onClick = { viewModel.onCategoryTypeChange(CategoryType.RAW_MATERIAL) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Kategori Bahan Baku")
                }
            }

            if (form.error != null) {
                Text(form.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.saveCategory(categoryId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !form.isLoading
            ) {
                Text(if (form.isLoading) "Menyimpan..." else "Simpan")
            }
        }
    }
}
