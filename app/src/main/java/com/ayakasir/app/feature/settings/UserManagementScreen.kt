package com.ayakasir.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayakasir.app.core.domain.model.User
import com.ayakasir.app.core.domain.model.UserFeature
import com.ayakasir.app.core.domain.model.UserFeatureAccess
import com.ayakasir.app.core.domain.model.UserRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: UserManagementViewModel = hiltViewModel()
) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<User?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.CASHIER) }
    var selectedFeatures by remember { mutableStateOf(UserFeatureAccess.defaultCashierFeatures) }

    var editName by remember { mutableStateOf("") }
    var editPin by remember { mutableStateOf("") }
    var editRole by remember { mutableStateOf(UserRole.CASHIER) }
    var editFeatures by remember { mutableStateOf(emptySet<UserFeature>()) }

    val resetForm = {
        name = ""
        email = ""
        phone = ""
        password = ""
        pin = ""
        role = UserRole.CASHIER
        selectedFeatures = UserFeatureAccess.defaultCashierFeatures
        viewModel.clearError()
    }

    LaunchedEffect(showAddDialog) {
        if (showAddDialog) resetForm()
    }

    LaunchedEffect(uiState.saveSuccessCounter) {
        if (uiState.saveSuccessCounter > 0) {
            showAddDialog = false
            editingUser = null
            showDeleteConfirm = false
        }
    }

    LaunchedEffect(editingUser?.id) {
        val user = editingUser
        if (user != null) {
            editName = user.name
            editPin = ""
            editRole = user.role
            editFeatures = if (user.role == UserRole.CASHIER) {
                if (user.featureAccess.isNotEmpty()) user.featureAccess else UserFeatureAccess.defaultCashierFeatures
            } else {
                emptySet()
            }
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manajemen User") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah User")
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Daftar User",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (users.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Belum ada user",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(users, key = { it.id }) { user ->
                            UserCard(
                                user = user,
                                onClick = {
                                    showAddDialog = false
                                    editingUser = user
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddUserDialog(
            name = name,
            email = email,
            phone = phone,
            password = password,
            pin = pin,
            role = role,
            selectedFeatures = selectedFeatures,
            isSaving = uiState.isSaving,
            error = uiState.error,
            onNameChange = { name = it },
            onEmailChange = { email = it },
            onPhoneChange = { phone = it },
            onPasswordChange = { password = it },
            onPinChange = { input -> pin = input.filter { it.isDigit() }.take(6) },
            onRoleChange = { newRole ->
                role = newRole
                if (newRole == UserRole.CASHIER && selectedFeatures.isEmpty()) {
                    selectedFeatures = UserFeatureAccess.defaultCashierFeatures
                }
            },
            onToggleFeature = { feature ->
                selectedFeatures = if (selectedFeatures.contains(feature)) {
                    selectedFeatures - feature
                } else {
                    selectedFeatures + feature
                }
            },
            onDismiss = { showAddDialog = false },
            onSave = {
                viewModel.createUser(
                    name = name,
                    email = email,
                    phone = phone,
                    password = password,
                    pin = pin,
                    role = role,
                    featureAccess = selectedFeatures
                )
            }
        )
    }

    editingUser?.let { user ->
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Hapus User") },
                text = { Text("Hapus user \"${user.name}\"? Tindakan ini tidak dapat dibatalkan.") },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.deleteUser(user.id) },
                        enabled = !uiState.isSaving
                    ) {
                        Text("Hapus", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Batal")
                    }
                }
            )
        } else {
            EditUserDialog(
                name = editName,
                pin = editPin,
                role = editRole,
                selectedFeatures = editFeatures,
                isSaving = uiState.isSaving,
                error = uiState.error,
                onNameChange = { editName = it },
                onPinChange = { input -> editPin = input.filter { it.isDigit() }.take(6) },
                onRoleChange = { newRole ->
                    editRole = newRole
                    editFeatures = when (newRole) {
                        UserRole.OWNER -> emptySet()
                        UserRole.CASHIER -> if (editFeatures.isEmpty()) {
                            UserFeatureAccess.defaultCashierFeatures
                        } else {
                            editFeatures
                        }
                    }
                },
                onToggleFeature = { feature ->
                    editFeatures = if (editFeatures.contains(feature)) {
                        editFeatures - feature
                    } else {
                        editFeatures + feature
                    }
                },
                onDismiss = { editingUser = null },
                onDelete = { showDeleteConfirm = true },
                onSave = {
                    viewModel.updateUser(
                        userId = user.id,
                        name = editName,
                        role = editRole,
                        featureAccess = editFeatures,
                        newPin = editPin.takeIf { it.isNotBlank() }
                    )
                }
            )
        }
    }
}

@Composable
private fun UserCard(
    user: User,
    onClick: () -> Unit
) {
    val accessLabel = when (user.role) {
        UserRole.OWNER -> "Akses: Semua fitur"
        UserRole.CASHIER -> {
            val effectiveFeatures = if (user.featureAccess.isNotEmpty()) {
                user.featureAccess
            } else {
                UserFeatureAccess.defaultCashierFeatures
            }
            "Akses: " + effectiveFeatures
                .sortedBy { it.ordinal }
                .joinToString(", ") { it.label }
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = roleLabel(user.role),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (user.isActive) "Aktif" else "Nonaktif",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (user.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = accessLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddUserDialog(
    name: String,
    email: String,
    phone: String,
    password: String,
    pin: String,
    role: UserRole,
    selectedFeatures: Set<UserFeature>,
    isSaving: Boolean,
    error: String?,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onRoleChange: (UserRole) -> Unit,
    onToggleFeature: (UserFeature) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val isNameValid = name.trim().isNotBlank()
    val isPinValid = pin.length == 6
    val isFeatureValid = role == UserRole.OWNER || selectedFeatures.isNotEmpty()
    val canSave = isNameValid && isPinValid && isFeatureValid && !isSaving

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Tambah User") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Nama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("Email (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    )
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    label = { Text("No. Telepon (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Phone
                    )
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = { Text("Untuk login dengan email di perangkat lain") }
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChange,
                    label = { Text("PIN (6 digit)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = { Text("Gunakan 6 digit angka") }
                )

                Text(text = "Role", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == UserRole.OWNER,
                        onClick = { onRoleChange(UserRole.OWNER) },
                        label = { Text("Owner") }
                    )
                    FilterChip(
                        selected = role == UserRole.CASHIER,
                        onClick = { onRoleChange(UserRole.CASHIER) },
                        label = { Text("Kasir") }
                    )
                }

                if (role == UserRole.CASHIER) {
                    Text(text = "Akses Fitur", style = MaterialTheme.typography.labelMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        UserFeature.values().forEach { feature ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleFeature(feature) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedFeatures.contains(feature),
                                    onCheckedChange = { onToggleFeature(feature) }
                                )
                                Column {
                                    Text(text = feature.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = feature.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (!isFeatureValid) {
                        Text(
                            text = "Pilih minimal 1 fitur untuk kasir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (!isNameValid) {
                    Text(
                        text = "Nama wajib diisi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (!isPinValid) {
                    Text(
                        text = "PIN harus 6 digit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) {
                Text(if (isSaving) "Menyimpan..." else "Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Batal")
            }
        }
    )
}

@Composable
private fun EditUserDialog(
    name: String,
    pin: String,
    role: UserRole,
    selectedFeatures: Set<UserFeature>,
    isSaving: Boolean,
    error: String?,
    onNameChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onRoleChange: (UserRole) -> Unit,
    onToggleFeature: (UserFeature) -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit
) {
    val isNameValid = name.trim().isNotBlank()
    val isPinValid = pin.isBlank() || pin.length == 6
    val isFeatureValid = role == UserRole.OWNER || selectedFeatures.isNotEmpty()
    val canSave = isNameValid && isPinValid && isFeatureValid && !isSaving

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Edit User") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Nama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChange,
                    label = { Text("PIN baru (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = { Text("Kosongkan jika tidak ingin mengubah PIN") }
                )

                Text(text = "Role", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == UserRole.OWNER,
                        onClick = { onRoleChange(UserRole.OWNER) },
                        label = { Text("Owner") }
                    )
                    FilterChip(
                        selected = role == UserRole.CASHIER,
                        onClick = { onRoleChange(UserRole.CASHIER) },
                        label = { Text("Kasir") }
                    )
                }

                if (role == UserRole.CASHIER) {
                    Text(text = "Akses Fitur", style = MaterialTheme.typography.labelMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        UserFeature.values().forEach { feature ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleFeature(feature) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedFeatures.contains(feature),
                                    onCheckedChange = { onToggleFeature(feature) }
                                )
                                Column {
                                    Text(text = feature.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = feature.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (!isFeatureValid) {
                        Text(
                            text = "Pilih minimal 1 fitur untuk kasir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (!isNameValid) {
                    Text(
                        text = "Nama wajib diisi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (!isPinValid) {
                    Text(
                        text = "PIN harus 6 digit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) {
                Text(if (isSaving) "Menyimpan..." else "Simpan")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete, enabled = !isSaving) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss, enabled = !isSaving) {
                    Text("Batal")
                }
            }
        }
    )
}

private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.OWNER -> "Owner"
    UserRole.CASHIER -> "Kasir"
}