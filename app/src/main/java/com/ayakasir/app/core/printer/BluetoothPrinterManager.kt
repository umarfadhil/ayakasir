package com.ayakasir.app.core.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothPrinterManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val PREFS_NAME = "printer_prefs"
        private const val KEY_MAC = "mac_address"
        private const val KEY_NAME = "device_name"
    }

    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Disconnected)
    val status: StateFlow<PrinterStatus> = _status.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val printerMutex = Mutex()
    private var connectedDeviceName: String = ""

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var savedAddress: String?
        get() = prefs.getString(KEY_MAC, null)
        set(value) = prefs.edit().putString(KEY_MAC, value).apply()

    private var savedName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return emptyList()
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        _status.value = PrinterStatus.Connecting
        try {
            socket?.close()
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket!!.connect()
            outputStream = socket!!.outputStream
            connectedDeviceName = device.name ?: "Printer"
            savedAddress = device.address
            savedName = connectedDeviceName
            _status.value = PrinterStatus.Connected(connectedDeviceName)
        } catch (e: Exception) {
            _status.value = PrinterStatus.Error("Gagal terhubung: ${e.message}")
            socket?.close()
            socket = null
            outputStream = null
        }
    }

    suspend fun print(data: ByteArray): Boolean = printerMutex.withLock {
        return withContext(Dispatchers.IO) {
            try {
                val os = outputStream
                    ?: throw IllegalStateException("Printer tidak terhubung")
                _status.value = PrinterStatus.Printing
                os.write(data)
                os.flush()
                _status.value = PrinterStatus.Connected(connectedDeviceName)
                true
            } catch (e: Exception) {
                _status.value = PrinterStatus.Error("Gagal cetak: ${e.message}")
                disconnect()
                false
            }
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) { }
        outputStream = null
        socket = null
        _status.value = PrinterStatus.Disconnected
    }

    @SuppressLint("MissingPermission")
    suspend fun reconnectIfSaved() {
        val address = savedAddress ?: return
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return
        try {
            val device = adapter.getRemoteDevice(address)
            connect(device)
        } catch (_: Exception) { }
    }

    fun isConnected(): Boolean = _status.value is PrinterStatus.Connected
}
