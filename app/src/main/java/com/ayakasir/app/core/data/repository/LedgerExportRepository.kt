package com.ayakasir.app.core.data.repository

import android.content.Context
import android.net.Uri
import com.ayakasir.app.core.data.local.dao.GeneralLedgerDao
import com.ayakasir.app.core.data.local.dao.RestaurantDao
import com.ayakasir.app.core.data.local.relation.GeneralLedgerExportRow
import com.ayakasir.app.core.session.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generalLedgerDao: GeneralLedgerDao,
    private val restaurantDao: RestaurantDao,
    private val sessionManager: SessionManager
) {
    data class ExportResult(
        val rowCount: Int
    )

    companion object {
        private val filenameDateFormat = SimpleDateFormat("ddMMyyyy", Locale("id", "ID")).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }
    }

    private val restaurantId: String
        get() = sessionManager.currentRestaurantId ?: ""

    suspend fun buildDefaultFileName(): String {
        val currentRestaurantId = restaurantId
        if (currentRestaurantId.isBlank()) {
            return "ayakasir_restaurant_${filenameDateFormat.format(Date())}.csv"
        }

        val restaurantName = restaurantDao.getById(currentRestaurantId)?.name.orEmpty()
        val safeRestaurantName = sanitizeFileSegment(
            if (restaurantName.isBlank()) "restaurant" else restaurantName
        )
        val dateString = filenameDateFormat.format(Date())
        return "ayakasir_${safeRestaurantName}_${dateString}.csv"
    }

    suspend fun exportToCsv(uri: Uri): ExportResult {
        val currentRestaurantId = restaurantId
        require(currentRestaurantId.isNotBlank()) { "Restoran aktif tidak ditemukan" }

        val rows = generalLedgerDao.getExportRows(currentRestaurantId)
        val csvContent = buildCsvContent(rows)

        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: error("Gagal membuka lokasi file tujuan")
        outputStream.use { stream ->
            stream.write(csvContent.toByteArray(Charsets.UTF_8))
            stream.flush()
        }

        return ExportResult(rowCount = rows.size)
    }

    private fun buildCsvContent(rows: List<GeneralLedgerExportRow>): String {
        val builder = StringBuilder()
        builder.append('\uFEFF')
        builder.append("id,type,product_name,variant_name,amount,qty,description\n")

        rows.forEach { row ->
            builder.append(csvEscape(row.id)).append(',')
            builder.append(csvEscape(row.type)).append(',')
            builder.append(csvEscape(row.productName.orEmpty())).append(',')
            builder.append(csvEscape(row.variantName.orEmpty())).append(',')
            builder.append(row.amount).append(',')
            builder.append(row.qty?.toString().orEmpty()).append(',')
            builder.append(csvEscape(row.description)).append('\n')
        }

        return builder.toString()
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (
            escaped.contains(',') ||
            escaped.contains('"') ||
            escaped.contains('\n') ||
            escaped.contains('\r')
        ) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun sanitizeFileSegment(value: String): String {
        return value
            .trim()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("_+"), "_")
    }
}
