package com.ayakasir.app.core.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CashBalanceDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val INITIAL_BALANCE_KEY = longPreferencesKey("initial_cash_balance")
    }

    val initialBalance: Flow<Long> = dataStore.data
        .map { preferences -> preferences[INITIAL_BALANCE_KEY] ?: 0L }
        .catch { emit(0L) }

    suspend fun setInitialBalance(amount: Long) {
        dataStore.edit { preferences ->
            preferences[INITIAL_BALANCE_KEY] = amount
        }
    }
}
