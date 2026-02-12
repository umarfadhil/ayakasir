package com.ayakasir.app.core.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QrisSettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val QRIS_IMAGE_URI_KEY = stringPreferencesKey("qris_image_uri")
        private val QRIS_MERCHANT_NAME_KEY = stringPreferencesKey("qris_merchant_name")
    }

    val qrisImageUri: Flow<String> = dataStore.data
        .map { preferences -> preferences[QRIS_IMAGE_URI_KEY] ?: "" }
        .catch { emit("") }

    val merchantName: Flow<String> = dataStore.data
        .map { preferences -> preferences[QRIS_MERCHANT_NAME_KEY] ?: "" }
        .catch { emit("") }

    val isConfigured: Flow<Boolean> = qrisImageUri.map { it.isNotBlank() }

    suspend fun saveSettings(imageUri: String, merchantName: String) {
        dataStore.edit { preferences ->
            preferences[QRIS_IMAGE_URI_KEY] = imageUri
            preferences[QRIS_MERCHANT_NAME_KEY] = merchantName
        }
    }
}
