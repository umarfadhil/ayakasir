package com.ayakasir.app.core.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SavedPrinterConfig(
    val connectionType: PrinterConnectionType? = null,
    val bluetoothAddress: String? = null,
    val bluetoothName: String? = null,
    val wifiHost: String? = null,
    val wifiPort: Int = BluetoothPrinterManager.DEFAULT_WIFI_PORT
)

@Singleton
class BluetoothPrinterManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val DEFAULT_WIFI_PORT = 9100
        private const val SOCKET_TIMEOUT_MS = 5000
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val PREFS_NAME = "printer_prefs"
        private const val KEY_CONNECTION_TYPE = "connection_type"
        private const val KEY_MAC = "mac_address"
        private const val KEY_NAME = "device_name"
        private const val KEY_WIFI_HOST = "wifi_host"
        private const val KEY_WIFI_PORT = "wifi_port"
    }

    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Disconnected)
    val status: StateFlow<PrinterStatus> = _status.asStateFlow()

    private var bluetoothSocket: BluetoothSocket? = null
    private var wifiSocket: Socket? = null
    private var outputStream: OutputStream? = null
    private val printerMutex = Mutex()
    private var connectedDeviceName: String = ""
    private var activeConnectionType: PrinterConnectionType? = null

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var savedConnectionType: PrinterConnectionType?
        get() = prefs.getString(KEY_CONNECTION_TYPE, null)
            ?.let { raw -> runCatching { PrinterConnectionType.valueOf(raw) }.getOrNull() }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_CONNECTION_TYPE) else putString(KEY_CONNECTION_TYPE, value.name)
            }.apply()
        }

    private var savedAddress: String?
        get() = prefs.getString(KEY_MAC, null)
        set(value) = prefs.edit().putString(KEY_MAC, value).apply()

    private var savedName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    private var savedWifiHost: String?
        get() = prefs.getString(KEY_WIFI_HOST, null)
        set(value) = prefs.edit().putString(KEY_WIFI_HOST, value).apply()

    private var savedWifiPort: Int
        get() = prefs.getInt(KEY_WIFI_PORT, DEFAULT_WIFI_PORT)
        set(value) = prefs.edit().putInt(KEY_WIFI_PORT, value).apply()

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return emptyList()
        return try {
            adapter.bondedDevices
                ?.toList()
                ?.sortedBy { it.name ?: it.address }
                .orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun getSavedConfig(): SavedPrinterConfig = SavedPrinterConfig(
        connectionType = savedConnectionType,
        bluetoothAddress = savedAddress,
        bluetoothName = savedName,
        wifiHost = savedWifiHost,
        wifiPort = savedWifiPort
    )

    fun clearSavedConfig() {
        savedConnectionType = null
        savedAddress = null
        savedName = null
        savedWifiHost = null
        savedWifiPort = DEFAULT_WIFI_PORT
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice) {
        connectBluetooth(device)
    }

    @SuppressLint("MissingPermission")
    suspend fun connectBluetooth(device: BluetoothDevice) = printerMutex.withLock {
        connectBluetoothInternal(device)
    }

    suspend fun connectWifi(host: String, port: Int = DEFAULT_WIFI_PORT) = printerMutex.withLock {
        connectWifiInternal(host, port)
    }

    suspend fun print(data: ByteArray): Boolean = printerMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (outputStream == null) reconnectIfSavedInternal()
                val os = outputStream
                    ?: throw IllegalStateException("Printer tidak terhubung")
                _status.value = PrinterStatus.Printing
                os.write(data)
                os.flush()
                val connectionType = activeConnectionType ?: getSavedConfig().connectionType ?: PrinterConnectionType.BLUETOOTH
                _status.value = PrinterStatus.Connected(connectedDeviceName.ifBlank { "Printer" }, connectionType)
                true
            } catch (e: Exception) {
                _status.value = PrinterStatus.Error("Gagal cetak: ${e.message}")
                disconnectInternal()
                false
            }
        }
    }

    fun disconnect() {
        disconnectInternal()
        _status.value = PrinterStatus.Disconnected
    }

    @SuppressLint("MissingPermission")
    suspend fun reconnectIfSaved() = printerMutex.withLock {
        reconnectIfSavedInternal()
    }

    fun isConnected(): Boolean = _status.value is PrinterStatus.Connected

    private fun disconnectInternal() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            wifiSocket?.close()
        } catch (_: Exception) { }
        outputStream = null
        bluetoothSocket = null
        wifiSocket = null
        connectedDeviceName = ""
        activeConnectionType = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun reconnectIfSavedInternal() {
        when (savedConnectionType) {
            PrinterConnectionType.BLUETOOTH -> {
                val address = savedAddress ?: return
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = bluetoothManager?.adapter ?: return
                val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
                connectBluetoothInternal(device)
            }
            PrinterConnectionType.WIFI -> {
                val host = savedWifiHost ?: return
                connectWifiInternal(host, savedWifiPort)
            }
            null -> Unit
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectBluetoothInternal(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        _status.value = PrinterStatus.Connecting(device.name ?: device.address)
        try {
            disconnectInternal()
            val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            newSocket.connect()
            bluetoothSocket = newSocket
            wifiSocket = null
            outputStream = newSocket.outputStream
            connectedDeviceName = device.name ?: device.address
            activeConnectionType = PrinterConnectionType.BLUETOOTH
            savedConnectionType = PrinterConnectionType.BLUETOOTH
            savedAddress = device.address
            savedName = connectedDeviceName
            _status.value = PrinterStatus.Connected(connectedDeviceName, PrinterConnectionType.BLUETOOTH)
        } catch (e: Exception) {
            _status.value = PrinterStatus.Error("Gagal terhubung Bluetooth: ${e.message}")
            disconnectInternal()
        }
    }

    private suspend fun connectWifiInternal(host: String, port: Int) = withContext(Dispatchers.IO) {
        val normalizedHost = host.trim()
        if (normalizedHost.isBlank()) {
            _status.value = PrinterStatus.Error("Alamat IP/host printer tidak valid")
            return@withContext
        }

        val normalizedPort = port.coerceIn(1, 65535)
        _status.value = PrinterStatus.Connecting("$normalizedHost:$normalizedPort")
        try {
            disconnectInternal()
            val tcpSocket = Socket()
            tcpSocket.connect(InetSocketAddress(normalizedHost, normalizedPort), SOCKET_TIMEOUT_MS)
            tcpSocket.soTimeout = SOCKET_TIMEOUT_MS
            wifiSocket = tcpSocket
            bluetoothSocket = null
            outputStream = tcpSocket.getOutputStream()
            connectedDeviceName = "$normalizedHost:$normalizedPort"
            activeConnectionType = PrinterConnectionType.WIFI
            savedConnectionType = PrinterConnectionType.WIFI
            savedWifiHost = normalizedHost
            savedWifiPort = normalizedPort
            savedAddress = null
            savedName = null
            _status.value = PrinterStatus.Connected(connectedDeviceName, PrinterConnectionType.WIFI)
        } catch (e: Exception) {
            _status.value = PrinterStatus.Error("Gagal terhubung WiFi: ${e.message}")
            disconnectInternal()
        }
    }
}
