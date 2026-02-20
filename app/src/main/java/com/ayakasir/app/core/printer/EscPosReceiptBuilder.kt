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

    private fun centerWrappedText(text: String): EscPosReceiptBuilder {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) {
            text("")
            return this
        }

        var current = ""
        words.forEach { word ->
            if (current.isBlank()) {
                current = word
                return@forEach
            }

            val candidate = "$current $word"
            if (candidate.length <= LINE_WIDTH) {
                current = candidate
            } else {
                centerTextLine(current)
                current = word
            }
        }

        if (current.isNotBlank()) {
            centerTextLine(current)
        }
        return this
    }

    private fun centerTextLine(text: String) {
        buffer.write(ALIGN_CENTER)
        buffer.write(text.take(LINE_WIDTH).toByteArray())
        buffer.write(byteArrayOf(LF))
        buffer.write(ALIGN_LEFT)
    }

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
                buffer.write(BOLD_ON)
                text(storeName.ifBlank { "AyaKa\$ir" })
                buffer.write(BOLD_OFF)
                buffer.write(NORMAL_SIZE)
            }
            .separator()
            .line("Tanggal & Jam", dateTime)
            .separator()
            .line("Item", "Sub-total")
            .apply {
                for (item in items) {
                    text(item.name.take(LINE_WIDTH))
                    line(
                        "  Qty ${item.qty}",
                        CurrencyFormatter.format(item.subtotal)
                    )
                }
            }
            .doubleSeparator()
            .apply {
                buffer.write(BOLD_ON)
                line("GRAND TOTAL", CurrencyFormatter.format(total))
                buffer.write(BOLD_OFF)
            }
            .apply {
                if (cashierName.isNotBlank()) line("Kasir", cashierName)
                if (transactionId.isNotBlank()) line("No", transactionId.takeLast(8).uppercase())
                if (paymentMethod.isNotBlank()) line("Metode", paymentMethod)
                if (paidAmount != null) line("Dibayar", CurrencyFormatter.format(paidAmount))
            }
            .separator()
            .centerWrappedText("Dicetak melalui apliakasi AyaKa\$ir")
            .feedAndCut()
            .build()
    }
}
