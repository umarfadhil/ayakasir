package com.ayakasir.app.core.printer

sealed interface PrinterStatus {
    data object Disconnected : PrinterStatus
    data class Connecting(val target: String? = null) : PrinterStatus
    data class Connected(
        val deviceName: String,
        val connectionType: PrinterConnectionType
    ) : PrinterStatus
    data class Error(val message: String) : PrinterStatus
    data object Printing : PrinterStatus
}
