package com.ayakasir.app.core.printer

sealed interface PrinterStatus {
    data object Disconnected : PrinterStatus
    data object Connecting : PrinterStatus
    data class Connected(val deviceName: String) : PrinterStatus
    data class Error(val message: String) : PrinterStatus
    data object Printing : PrinterStatus
}
