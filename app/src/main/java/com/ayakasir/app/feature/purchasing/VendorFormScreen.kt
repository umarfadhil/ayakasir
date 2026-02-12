package com.ayakasir.app.feature.purchasing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorFormScreen(
    vendorId: String?,
    onNavigateBack: () -> Unit,
    viewModel: PurchasingViewModel = hiltViewModel()
) {
    val form by viewModel.vendorForm.collectAsStateWithLifecycle()

    LaunchedEffect(vendorId) {
        viewModel.resetVendorForm()
        vendorId?.let { viewModel.loadVendor(it) }
    }

    LaunchedEffect(form.isSaved) {
        if (form.isSaved) onNavigateBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (vendorId != null) "Edit Vendor" else "Tambah Vendor") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
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
                onValueChange = { viewModel.onVendorNameChange(it) },
                label = { Text("Nama Vendor") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = form.error != null && form.name.isBlank()
            )

            OutlinedTextField(
                value = form.phone,
                onValueChange = { viewModel.onVendorPhoneChange(it) },
                label = { Text("Telepon") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = form.address,
                onValueChange = { viewModel.onVendorAddressChange(it) },
                label = { Text("Alamat") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            if (form.error != null) {
                Text(form.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.saveVendor(vendorId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !form.isLoading
            ) {
                Text(if (form.isLoading) "Menyimpan..." else "Simpan")
            }
        }
    }
}
