package com.ayakasir.app.core.printer

import com.ayakasir.app.core.util.CurrencyFormatter
import java.io.ByteArrayOutputStream

class EscPosReceiptBuilder {

    companion object {
        const val LINE_WIDTH = 32
        private val ESC = 0x1B.toByte()
        private val GS = 0x1D.toByte()
        private val LF = 0x0A.toByte()

        val INIT = byteArrayOf(ESC, 0x40)
        val ALIGN_CENTER = byteArrayOf(ESC, 0x61, 0x01)
        val ALIGN_LEFT = byteArrayOf(ESC, 0x61, 0x00)
        val BOLD_ON = byteArrayOf(ESC, 0x45, 0x01)
        val BOLD_OFF = byteArrayOf(ESC, 0x45, 0x00)
        val DOUBLE_HEIGHT = byteArrayOf(GS, 0x21, 0x01)
        val NORMAL_SIZE = byteArrayOf(GS, 0x21, 0x00)
        val CUT_PAPER = byteArrayOf(GS, 0x56, 0x00)
        val FEED_3_LINES = byteArrayOf(ESC, 0x64, 0x03)
    }

    private val buffer = ByteArrayOutputStream()

    fun init(): EscPosReceiptBuilder {
        buffer.write(INIT)
        return this
    }

    fun centerBold(text: String): EscPosReceiptBuilder {
        buffer.write(ALIGN_CENTER)
        buffer.write(BOLD_ON)
        buffer.write(text.toByteArray())
        buffer.write(byteArrayOf(LF))
        buffer.write(BOLD_OFF)
        buffer.write(ALIGN_LEFT)
        return this
    }

    fun line(left: String, right: String): EscPosReceiptBuilder {
        val space = LINE_WIDTH - left.length - right.length
        val padded = if (space > 0) {
            left + " ".repeat(space) + right
        } else {
            left.take(LINE_WIDTH - right.length - 1) + " " + right
        }
        buffer.write(padded.toByteArray())
        buffer.write(byteArrayOf(LF))
        return this
    }

    fun text(text: String): EscPosReceiptBuilder {
        buffer.write(text.toByteArray())
        buffer.write(byteArrayOf(LF))
        return this
    }

    fun separator(): EscPosReceiptBuilder {
        buffer.write("-".repeat(LINE_WIDTH).toByteArray())
        buffer.write(byteArrayOf(LF))
        return this
    }

    fun doubleSeparator(): EscPosReceiptBuilder {
        buffer.write("=".repeat(LINE_WIDTH).toByteArray())
        buffer.write(byteArrayOf(LF))
        return this
    }

    fun feedAndCut(): EscPosReceiptBuilder {
        buffer.write(FEED_3_LINES)
        buffer.write(CUT_PAPER)
        return this
    }

    fun build(): ByteArray = buffer.toByteArray()

    data class ReceiptItem(
        val name: String,
        val qty: Int,
        val unitPrice: Long,
        val subtotal: Long
    )

    fun buildReceipt(
        storeName: String,
        cashierName: String,
        transactionId: String,
        dateTime: String,
        items: List<ReceiptItem>,
        total: Long,
        paymentMethod: String,
        paidAmount: Long? = null
    ): ByteArray {
        return EscPosReceiptBuilder()
            .init()
            .apply {
                buffer.write(ALIGN_CENTER)
                buffer.write(DOUBLE_HEIGHT)
                text(storeName)
                buffer.write(NORMAL_SIZE)
                text("")
            }
            .separator()
            .line("Kasir:", cashierName)
            .line("Tanggal:", dateTime)
            .line("No:", transactionId.takeLast(8).uppercase())
            .separator()
            .apply {
                for (item in items) {
                    text(item.name)
                    line(
                        "  ${item.qty}x ${CurrencyFormatter.format(item.unitPrice)}",
                        CurrencyFormatter.format(item.subtotal)
                    )
                }
            }
            .doubleSeparator()
            .apply {
                buffer.write(BOLD_ON)
                line("TOTAL", CurrencyFormatter.format(total))
                buffer.write(BOLD_OFF)
            }
            .line("Bayar ($paymentMethod)", CurrencyFormatter.format(paidAmount ?: total))
            .apply {
                if (paymentMethod == "CASH" && paidAmount != null && paidAmount > total) {
                    line("Kembali", CurrencyFormatter.format(paidAmount - total))
                }
            }
            .separator()
            .apply {
                buffer.write(ALIGN_CENTER)
                text("Terima kasih!")
                text("Selamat menikmati")
            }
            .feedAndCut()
            .build()
    }
}
