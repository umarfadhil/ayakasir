package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.dao.UserDao
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.data.local.entity.UserEntity
import com.ayakasir.app.core.data.remote.dto.UserDto
import com.ayakasir.app.core.data.remote.mapper.toEntity
import com.ayakasir.app.core.domain.model.SyncStatus
import com.ayakasir.app.core.domain.model.User
import com.ayakasir.app.core.domain.model.UserFeature
import com.ayakasir.app.core.domain.model.UserFeatureAccess
import com.ayakasir.app.core.domain.model.UserRole
import com.ayakasir.app.core.session.PinHasher
import com.ayakasir.app.core.sync.SyncManager
import com.ayakasir.app.core.sync.SyncScheduler
import com.ayakasir.app.core.util.UuidGenerator
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncScheduler: SyncScheduler,
    private val syncManager: SyncManager,
    private val supabaseClient: SupabaseClient
) {
    fun getAllUsers(): Flow<List<User>> = userDao.getAll().map { list ->
        list.map { it.toDomain() }
    }

    fun getActiveUsers(): Flow<List<User>> = userDao.getAllActive().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getUserById(id: String): User? = userDao.getById(id)?.toDomain()

    suspend fun authenticateByPinDirect(pin: String): User? {
        val users = userDao.getAllActiveDirect()
        return users.firstOrNull { user ->
            PinHasher.verify(pin, user.pinSalt, user.pinHash)
        }?.toDomain()
    }

    suspend fun authenticateByPinForUser(userId: String, pin: String): User? {
        val user = userDao.getById(userId) ?: return null
        if (!user.isActive) return null
        return if (PinHasher.verify(pin, user.pinSalt, user.pinHash)) user.toDomain() else null
    }

    suspend fun getUserByEmail(email: String): User? = userDao.getByEmail(email)?.toDomain()

    /**
     * Fetch user by email from Supabase (source of truth), update local cache, then return.
     * Falls back to local cache if network fails.
     */
    suspend fun getUserByEmailRemote(email: String): User? {
        try {
            val dtos = supabaseClient.from("users")
                .select { filter { eq("email", email) } }
                .decodeList<UserDto>()
            val dto = dtos.firstOrNull()
            if (dto != null) {
                val entity = dto.toEntity()
                userDao.insert(entity)
                return entity.toDomain()
            }
        } catch (_: Exception) {
            // Network failure — fall back to local cache
        }
        return userDao.getByEmail(email)?.toDomain()
    }

    suspend fun seedDefaultOwner() {
        val existing = userDao.getAllActiveDirect()
        if (existing.isEmpty()) {
            val salt = PinHasher.generateSalt()
            val hash = PinHasher.hash("000000", salt)
            val entity = UserEntity(
                id = UuidGenerator.generate(),
                name = "Pemilik",
                pinHash = hash,
                pinSalt = salt,
                role = UserRole.OWNER.name
            )
            userDao.insert(entity)
        }
    }

    suspend fun createUser(
        name: String,
        pin: String,
        role: UserRole,
        featureAccess: Set<UserFeature> = emptySet()
    ): User {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash(pin, salt)
        val accessValue = if (role == UserRole.CASHIER) {
            UserFeatureAccess.serialize(featureAccess)
        } else {
            null
        }
        val entity = UserEntity(
            id = UuidGenerator.generate(),
            name = name,
            pinHash = hash,
            pinSalt = salt,
            role = role.name,
            featureAccess = accessValue,
            syncStatus = SyncStatus.PENDING.name,
            updatedAt = System.currentTimeMillis()
        )
        userDao.insert(entity)

        try {
            syncManager.pushToSupabase("users", "INSERT", entity.id)
        } catch (e: Exception) {
            val featurePayload = accessValue?.let { ",\"feature_access\":\"$it\"" }.orEmpty()
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    tableName = "users",
                    recordId = entity.id,
                    operation = "INSERT",
                    payload = "{\"id\":\"${entity.id}\",\"name\":\"$name\",\"role\":\"${role.name}\"$featurePayload}"
                )
            )
            syncScheduler.requestImmediateSync()
        }
        return entity.toDomain()
    }

    suspend fun updateUser(
        userId: String,
        name: String,
        role: UserRole,
        featureAccess: Set<UserFeature>
    ): User? {
        val user = userDao.getById(userId) ?: return null
        val accessValue = if (role == UserRole.CASHIER) {
            UserFeatureAccess.serialize(featureAccess)
        } else {
            null
        }
        val updated = user.copy(
            name = name,
            role = role.name,
            featureAccess = accessValue,
            syncStatus = SyncStatus.PENDING.name,
            updatedAt = System.currentTimeMillis()
        )
        userDao.update(updated)

        try {
            syncManager.pushToSupabase("users", "UPDATE", userId)
        } catch (e: Exception) {
            val featurePayload = accessValue?.let { ",\"feature_access\":\"$it\"" }.orEmpty()
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    tableName = "users",
                    recordId = userId,
                    operation = "UPDATE",
                    payload = "{\"id\":\"$userId\",\"name\":\"$name\",\"role\":\"${role.name}\"$featurePayload}"
                )
            )
            syncScheduler.requestImmediateSync()
        }
        return updated.toDomain()
    }

    suspend fun changePin(userId: String, newPin: String) {
        val user = userDao.getById(userId) ?: return
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash(newPin, salt)
        userDao.update(user.copy(pinHash = hash, pinSalt = salt, syncStatus = SyncStatus.PENDING.name, updatedAt = System.currentTimeMillis()))

        try {
            syncManager.pushToSupabase("users", "UPDATE", userId)
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(tableName = "users", recordId = userId, operation = "UPDATE", payload = "{\"id\":\"$userId\"}")
            )
            syncScheduler.requestImmediateSync()
        }
    }

    suspend fun toggleUserActive(userId: String) {
        val user = userDao.getById(userId) ?: return
        userDao.update(user.copy(isActive = !user.isActive, syncStatus = SyncStatus.PENDING.name, updatedAt = System.currentTimeMillis()))

        try {
            syncManager.pushToSupabase("users", "UPDATE", userId)
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(tableName = "users", recordId = userId, operation = "UPDATE", payload = "{\"id\":\"$userId\"}")
            )
            syncScheduler.requestImmediateSync()
        }
    }

    suspend fun hasAnyUsers(): Boolean {
        return userDao.getAllActiveDirect().isNotEmpty()
    }

    /**
     * Authenticate by email + password. Fetches from Supabase first (source of truth),
     * then verifies password hash locally.
     * Returns User if credentials valid and is_active = true, null otherwise.
     */
    suspend fun authenticateByEmail(email: String, password: String): AuthResult {
        // Fetch latest user data from Supabase (source of truth)
        var fetchedEntity: UserEntity? = null
        try {
            val dtos = supabaseClient.from("users")
                .select { filter { eq("email", email) } }
                .decodeList<UserDto>()
            val dto = dtos.firstOrNull()
            if (dto != null) {
                fetchedEntity = dto.toEntity()
                userDao.insert(fetchedEntity)
            }
        } catch (_: Exception) {
            // Network failure — fall back to local cache
        }

        // Use Supabase data directly if available, otherwise fall back to local cache
        val entity = fetchedEntity ?: userDao.getByEmail(email) ?: return AuthResult.NotFound
        if (!entity.isActive) return AuthResult.Inactive

        val pwSalt = entity.passwordSalt
        val pwHash = entity.passwordHash
        if (pwSalt == null || pwHash == null) return AuthResult.NoPassword
        if (!PinHasher.verify(password, pwSalt, pwHash)) return AuthResult.WrongPassword

        return AuthResult.Success(entity.toDomain())
    }

    sealed class AuthResult {
        data class Success(val user: User) : AuthResult()
        data object NotFound : AuthResult()
        data object Inactive : AuthResult()
        data object WrongPassword : AuthResult()
        data object NoPassword : AuthResult()
    }

    suspend fun registerOwner(
        name: String,
        email: String,
        phone: String,
        pin: String,
        password: String,
        restaurantId: String
    ): User {
        val pinSaltVal = PinHasher.generateSalt()
        val pinHashVal = PinHasher.hash(pin, pinSaltVal)
        val pwSalt = PinHasher.generateSalt()
        val pwHash = PinHasher.hash(password, pwSalt)
        val entity = UserEntity(
            id = UuidGenerator.generate(),
            name = name,
            email = email,
            phone = phone,
            pinHash = pinHashVal,
            pinSalt = pinSaltVal,
            passwordHash = pwHash,
            passwordSalt = pwSalt,
            role = UserRole.OWNER.name,
            restaurantId = restaurantId,
            isActive = false,
            syncStatus = SyncStatus.PENDING.name,
            updatedAt = System.currentTimeMillis()
        )
        userDao.insert(entity)

        try {
            syncManager.pushToSupabase("users", "INSERT", entity.id)
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    tableName = "users",
                    recordId = entity.id,
                    operation = "INSERT",
                    payload = "{\"id\":\"${entity.id}\",\"name\":\"$name\",\"email\":\"$email\",\"phone\":\"$phone\",\"role\":\"${UserRole.OWNER.name}\"}"
                )
            )
            syncScheduler.requestImmediateSync()
        }

        return entity.toDomain()
    }

    private fun UserEntity.toDomain() = User(
        id = id,
        name = name,
        email = email,
        phone = phone,
        role = UserRole.valueOf(role),
        restaurantId = restaurantId,
        isActive = isActive,
        featureAccess = UserFeatureAccess.parse(featureAccess)
    )
}
