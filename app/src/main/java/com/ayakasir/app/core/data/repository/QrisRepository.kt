package com.ayakasir.app.core.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ayakasir.app.core.data.local.datastore.QrisSettingsDataStore
import com.ayakasir.app.core.data.remote.dto.RestaurantDto
import com.ayakasir.app.core.session.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QrisRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseClient: SupabaseClient,
    private val qrisSettingsDataStore: QrisSettingsDataStore,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "QrisRepository"
        private const val BUCKET = "qris-images"
    }

    /**
     * Save QRIS settings to Supabase and DataStore.
     * If [imageUri] is a local content:// URI, uploads to Supabase Storage first.
     * If [imageUri] already starts with "http://" or "https://", uses it as-is.
     */
    suspend fun saveQrisSettings(imageUri: String, merchantName: String) {
        val restaurantId = sessionManager.currentRestaurantId
            ?: throw IllegalStateException("No active restaurant session")

        val finalUrl = when {
            imageUri.isBlank() -> ""
            imageUri.startsWith("https://") || imageUri.startsWith("http://") -> imageUri
            else -> uploadImage(imageUri, restaurantId)
        }

        supabaseClient.from("restaurants").update(
            buildJsonObject {
                if (finalUrl.isBlank()) put("qris_image_url", null as String?) else put("qris_image_url", finalUrl)
                put("qris_merchant_name", merchantName.ifBlank { null })
            }
        ) {
            filter {
                filter("id", FilterOperator.EQ, restaurantId)
            }
        }
        Log.d(TAG, "Saved QRIS settings for restaurant $restaurantId")

        qrisSettingsDataStore.saveSettings(finalUrl, merchantName)
    }

    /**
     * Fetch the restaurant's QRIS settings from Supabase and populate DataStore.
     * Called on login and periodic sync.
     */
    suspend fun pullQrisSettings(restaurantId: String) {
        try {
            val dto = supabaseClient.from("restaurants")
                .select { filter { filter("id", FilterOperator.EQ, restaurantId) } }
                .decodeSingle<RestaurantDto>()

            val url = normalizeRemoteImageUrl(dto.qrisImageUrl.orEmpty(), restaurantId)
            val merchant = dto.qrisMerchantName.orEmpty()
            qrisSettingsDataStore.saveSettings(url, merchant)
            Log.d(TAG, "Pulled QRIS settings for restaurant $restaurantId: url=${url.isNotEmpty()}")
        } catch (e: Exception) {
            Log.w(TAG, "pullQrisSettings failed: ${e.message}")
        }
    }

    private fun normalizeRemoteImageUrl(rawUrl: String, restaurantId: String): String {
        val normalized = rawUrl.trim()
        if (normalized.isBlank()) return ""
        if (normalized.startsWith("https://") || normalized.startsWith("http://")) {
            return normalized
        }

        // Defensive fallback for legacy/invalid values (e.g. content://) so other devices can still load QRIS.
        val fallback = supabaseClient.storage.from(BUCKET).publicUrl("$restaurantId/qris.jpg")
        Log.w(TAG, "Non-HTTP QRIS URL found. Falling back to bucket path for restaurant $restaurantId")
        return fallback
    }

    private suspend fun uploadImage(uriString: String, restaurantId: String): String {
        val uri = Uri.parse(uriString)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot read image from URI")

        val path = "$restaurantId/qris.jpg"
        supabaseClient.storage.from(BUCKET).upload(path, bytes) { upsert = true }
        val publicUrl = supabaseClient.storage.from(BUCKET).publicUrl(path)
        Log.d(TAG, "Uploaded QRIS image: $publicUrl")
        return publicUrl
    }
}
