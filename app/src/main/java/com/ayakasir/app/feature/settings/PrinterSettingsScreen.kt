package com.ayakasir.app.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayakasir.app.core.printer.PrinterConnectionType
import com.ayakasir.app.core.printer.PrinterStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: PrinterSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var bluetoothPermissionGranted by remember { mutableStateOf(hasBluetoothPermissions(context)) }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.all { it } || hasBluetoothPermissions(context)
        bluetoothPermissionGranted = granted
        if (granted) {
            viewModel.loadPairedBluetoothDevices()
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(uiState.connectionType, bluetoothPermissionGranted) {
        if (uiState.connectionType == PrinterConnectionType.BLUETOOTH && bluetoothPermissionGranted) {
            viewModel.loadPairedBluetoothDevices()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan Printer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Pilih koneksi printer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.connectionType == PrinterConnectionType.BLUETOOTH,
                    onClick = { viewModel.selectConnectionType(PrinterConnectionType.BLUETOOTH) },
                    label = { Text("Bluetooth") }
                )
                FilterChip(
                    selected = uiState.connectionType == PrinterConnectionType.WIFI,
                    onClick = { viewModel.selectConnectionType(PrinterConnectionType.WIFI) },
                    label = { Text("WiFi") }
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = printerStatusText(uiState.status),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (uiState.connectionType == PrinterConnectionType.BLUETOOTH) {
                BluetoothSettingSection(
                    permissionGranted = bluetoothPermissionGranted,
                    devices = uiState.bluetoothDevices,
                    isWorking = uiState.isWorking,
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            bluetoothPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN
                                )
                            )
                        } else {
                            bluetoothPermissionGranted = true
                            viewModel.loadPairedBluetoothDevices()
                        }
                    },
                    onRefreshDevices = { viewModel.loadPairedBluetoothDevices() },
                    onOpenBluetoothPairing = {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    },
                    onConnectDevice = { address -> viewModel.connectBluetooth(address) }
                )
            } else {
                WifiSettingSection(
                    host = uiState.wifiHost,
                    port = uiState.wifiPort,
                    isWorking = uiState.isWorking,
                    onHostChanged = viewModel::updateWifiHost,
                    onPortChanged = viewModel::updateWifiPort,
                    onConnect = { viewModel.connectWifi() }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.disconnectPrinter() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Putuskan")
                }
                Button(
                    onClick = { viewModel.printTestReceipt() },
                    enabled = !uiState.isWorking,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cetak Tes")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Format Struk",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Header: Nama Restoran")
                    Text("Tanggal & jam transaksi")
                    Text("Nama item, qty, sub-total")
                    Text("GRAND TOTAL")
                    Text("Footer: Dicetak melalui apliakasi AyaKa\$ir")
                }
            }
        }
    }
}

@Composable
private fun BluetoothSettingSection(
    permissionGranted: Boolean,
    devices: List<PrinterSettingsViewModel.BluetoothDeviceUi>,
    isWorking: Boolean,
    onRequestPermission: () -> Unit,
    onRefreshDevices: () -> Unit,
    onOpenBluetoothPairing: () -> Unit,
    onConnectDevice: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!permissionGranted) {
                Text(
                    text = "Izin Bluetooth diperlukan untuk membaca perangkat yang sudah dipasangkan.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = onRequestPermission) {
                    Text("Berikan Izin Bluetooth")
                }
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenBluetoothPairing, modifier = Modifier.weight(1f)) {
                    Text("Pair via Sistem")
                }
                OutlinedButton(onClick = onRefreshDevices, modifier = Modifier.weight(1f)) {
                    Text("Muat Ulang")
                }
            }

            if (devices.isEmpty()) {
                Text(
                    text = "Belum ada perangkat Bluetooth terpasang.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                devices.forEach { device ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name, fontWeight = FontWeight.Medium)
                                Text(
                                    device.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onConnectDevice(device.address) },
                                enabled = !isWorking
                            ) {
                                Text("Hubungkan")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiSettingSection(
    host: String,
    port: String,
    isWorking: Boolean,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = onHostChanged,
                label = { Text("IP / Host Printer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port,
                onValueChange = onPortChanged,
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onConnect,
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Hubungkan WiFi")
            }
        }
    }
}

private fun printerStatusText(status: PrinterStatus): String = when (status) {
    PrinterStatus.Disconnected -> "Status: Tidak terhubung"
    is PrinterStatus.Connecting -> "Status: Menghubungkan ${status.target ?: "printer"}..."
    is PrinterStatus.Connected -> "Status: Terhubung (${status.connectionType.name}) ke ${status.deviceName}"
    PrinterStatus.Printing -> "Status: Sedang mencetak..."
    is PrinterStatus.Error -> "Status: ${status.message}"
}

private fun hasBluetoothPermissions(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val connectGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED
    val scanGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_SCAN
    ) == PackageManager.PERMISSION_GRANTED
    return connectGranted && scanGranted
}
