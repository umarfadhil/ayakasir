package com.ayakasir.app.core.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthSessionDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val USER_ID_KEY = stringPreferencesKey("auth_user_id")
        private val RESTAURANT_ID_KEY = stringPreferencesKey("auth_restaurant_id")
        private val IS_FULL_LOGIN_KEY = booleanPreferencesKey("auth_is_full_login")
    }

    data class PersistedSession(
        val userId: String?,
        val restaurantId: String?,
        val isFullLogin: Boolean
    )

    val session: Flow<PersistedSession> = dataStore.data
        .map { prefs ->
            PersistedSession(
                userId = prefs[USER_ID_KEY],
                restaurantId = prefs[RESTAURANT_ID_KEY],
                isFullLogin = prefs[IS_FULL_LOGIN_KEY] ?: false
            )
        }
        .catch {
            emit(PersistedSession(null, null, false))
        }

    suspend fun saveSession(userId: String, restaurantId: String?) {
        dataStore.edit { prefs ->
            prefs[USER_ID_KEY] = userId
            restaurantId?.let { prefs[RESTAURANT_ID_KEY] = it }
            prefs[IS_FULL_LOGIN_KEY] = true
        }
    }

    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(USER_ID_KEY)
            prefs.remove(RESTAURANT_ID_KEY)
            prefs[IS_FULL_LOGIN_KEY] = false
        }
    }
}