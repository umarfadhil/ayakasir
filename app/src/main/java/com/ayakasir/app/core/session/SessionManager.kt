package com.ayakasir.app.core.session

import com.ayakasir.app.core.data.local.datastore.AuthSessionDataStore
import com.ayakasir.app.core.domain.model.User
import com.ayakasir.app.core.domain.model.UserRole
import com.ayakasir.app.core.sync.RealtimeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val authSessionDataStore: AuthSessionDataStore,
    private val realtimeManager: RealtimeManager
) {
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _restaurantId = MutableStateFlow<String?>(null)
    val restaurantId: StateFlow<String?> = _restaurantId.asStateFlow()

    val isLoggedIn: Boolean get() = _currentUser.value != null
    val isOwner: Boolean get() = _currentUser.value?.role == UserRole.OWNER
    val currentRestaurantId: String? get() = _restaurantId.value

    /**
     * Full login (email/password). Persists session to DataStore so PIN unlock works on app restart.
     */
    suspend fun loginFull(user: User, restaurantId: String? = null) {
        _currentUser.value = user
        _restaurantId.value = restaurantId
        authSessionDataStore.saveSession(user.id, restaurantId)
        restaurantId?.let { realtimeManager.connect(it) }
    }

    /**
     * PIN unlock (app resume). Sets in-memory session only, already persisted.
     */
    fun loginPin(user: User, restaurantId: String? = null) {
        _currentUser.value = user
        _restaurantId.value = restaurantId
        restaurantId?.let { realtimeManager.connect(it) }
    }

    /**
     * Full logout (from Settings). Clears persisted session → requires email/password on next launch.
     */
    suspend fun logout() {
        realtimeManager.disconnect()
        _currentUser.value = null
        _restaurantId.value = null
        authSessionDataStore.clearSession()
    }

    /**
     * Check if there's a persisted session (user previously did full login and hasn't logged out).
     * Returns the persisted userId if exists, null otherwise.
     */
    suspend fun getPersistedUserId(): String? {
        val session = authSessionDataStore.session.first()
        return if (session.isFullLogin) session.userId else null
    }

    suspend fun getPersistedRestaurantId(): String? {
        val session = authSessionDataStore.session.first()
        return session.restaurantId
    }
}