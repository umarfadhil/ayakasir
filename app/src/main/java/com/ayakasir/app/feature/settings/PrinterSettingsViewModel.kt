package com.ayakasir.app.feature.settings

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.data.repository.RestaurantRepository
import com.ayakasir.app.core.printer.BluetoothPrinterManager
import com.ayakasir.app.core.printer.EscPosReceiptBuilder
import com.ayakasir.app.core.printer.PrinterConnectionType
import com.ayakasir.app.core.printer.PrinterStatus
import com.ayakasir.app.core.printer.SavedPrinterConfig
import com.ayakasir.app.core.session.SessionManager
import com.ayakasir.app.core.util.DateTimeUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrinterSettingsViewModel @Inject constructor(
    private val printerManager: BluetoothPrinterManager,
    private val restaurantRepository: RestaurantRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    data class BluetoothDeviceUi(
        val name: String,
        val address: String
    )

    data class UiState(
        val connectionType: PrinterConnectionType = PrinterConnectionType.BLUETOOTH,
        val bluetoothDevices: List<BluetoothDeviceUi> = emptyList(),
        val wifiHost: String = "",
        val wifiPort: String = BluetoothPrinterManager.DEFAULT_WIFI_PORT.toString(),
        val status: PrinterStatus = PrinterStatus.Disconnected,
        val isWorking: Boolean = false,
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var bluetoothDeviceLookup: Map<String, BluetoothDevice> = emptyMap()

    init {
        applySavedConfig(printerManager.getSavedConfig())
        observePrinterStatus()
    }

    fun selectConnectionType(type: PrinterConnectionType) {
        _uiState.update { it.copy(connectionType = type) }
    }

    fun updateWifiHost(host: String) {
        _uiState.update { it.copy(wifiHost = host, message = null) }
    }

    fun updateWifiPort(port: String) {
        val filtered = port.filter { it.isDigit() }.take(5)
        _uiState.update { it.copy(wifiPort = filtered, message = null) }
    }

    fun loadPairedBluetoothDevices() {
        val paired = printerManager.getPairedDevices()
        bluetoothDeviceLookup = paired.associateBy { it.address }
        _uiState.update {
            it.copy(
                bluetoothDevices = paired.map { device ->
                    BluetoothDeviceUi(
                        name = device.name?.ifBlank { "Perangkat tanpa nama" } ?: "Perangkat tanpa nama",
                        address = device.address
                    )
                }
            )
        }
    }

    fun connectBluetooth(address: String) {
        val device = bluetoothDeviceLookup[address]
        if (device == null) {
            _uiState.update { it.copy(message = "Perangkat Bluetooth tidak ditemukan") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, message = null) }
            printerManager.connectBluetooth(device)
            applySavedConfig(printerManager.getSavedConfig())
            publishConnectionMessage()
        }
    }

    fun connectWifi() {
        val host = _uiState.value.wifiHost.trim()
        val port = _uiState.value.wifiPort.toIntOrNull()
        if (host.isBlank()) {
            _uiState.update { it.copy(message = "Isi alamat IP/host printer terlebih dahulu") }
            return
        }
        if (port == null || port !in 1..65535) {
            _uiState.update { it.copy(message = "Port printer harus 1-65535") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, message = null) }
            printerManager.connectWifi(host, port)
            applySavedConfig(printerManager.getSavedConfig())
            publishConnectionMessage()
        }
    }

    fun disconnectPrinter() {
        printerManager.disconnect()
        _uiState.update { it.copy(message = "Printer diputuskan") }
    }

    fun printTestReceipt() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, message = null) }
            val restaurantName = resolveRestaurantName()
            val now = System.currentTimeMillis()
            val sampleItems = listOf(
                EscPosReceiptBuilder.ReceiptItem(
                    name = "Nasi Goreng Spesial",
                    qty = 1,
                    unitPrice = 28000L,
                    subtotal = 28000L
                ),
                EscPosReceiptBuilder.ReceiptItem(
                    name = "Es Teh Manis",
                    qty = 2,
                    unitPrice = 8000L,
                    subtotal = 16000L
                )
            )
            val total = sampleItems.sumOf { it.subtotal }
            val data = EscPosReceiptBuilder().buildReceipt(
                storeName = restaurantName,
                cashierName = sessionManager.currentUser.value?.name.orEmpty(),
                transactionId = "TEST$now",
                dateTime = DateTimeUtil.formatDateTime(now),
                items = sampleItems,
                total = total,
                paymentMethod = "TEST"
            )

            val printed = printerManager.print(data)
            _uiState.update {
                it.copy(
                    isWorking = false,
                    message = if (printed) {
                        "Tes cetak berhasil"
                    } else {
                        (printerManager.status.value as? PrinterStatus.Error)?.message ?: "Gagal mencetak tes"
                    }
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun observePrinterStatus() {
        viewModelScope.launch {
            printerManager.status.collect { status ->
                _uiState.update {
                    it.copy(
                        status = status,
                        isWorking = status is PrinterStatus.Connecting || status is PrinterStatus.Printing
                    )
                }
            }
        }
    }

    private fun applySavedConfig(config: SavedPrinterConfig) {
        _uiState.update {
            it.copy(
                connectionType = config.connectionType ?: PrinterConnectionType.BLUETOOTH,
                wifiHost = config.wifiHost.orEmpty(),
                wifiPort = config.wifiPort.toString()
            )
        }
    }

    private fun publishConnectionMessage() {
        _uiState.update {
            val message = when (val status = printerManager.status.value) {
                is PrinterStatus.Connected -> "Terhubung ke ${status.deviceName}"
                is PrinterStatus.Error -> status.message
                else -> null
            }
            it.copy(isWorking = false, message = message)
        }
    }

    private suspend fun resolveRestaurantName(): String {
        val restaurantId = sessionManager.currentRestaurantId ?: return "AyaKa\$ir"
        val restaurant = restaurantRepository.getById(restaurantId) ?: return "AyaKa\$ir"
        return restaurant.name.ifBlank { "AyaKa\$ir" }
    }
}
