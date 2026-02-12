package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.dao.UserDao
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.data.local.entity.UserEntity
import com.ayakasir.app.core.domain.model.User
import com.ayakasir.app.core.domain.model.UserFeature
import com.ayakasir.app.core.domain.model.UserFeatureAccess
import com.ayakasir.app.core.domain.model.UserRole
import com.ayakasir.app.core.session.PinHasher
import com.ayakasir.app.core.util.UuidGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val syncQueueDao: SyncQueueDao
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
            featureAccess = accessValue
        )
        userDao.insert(entity)
        val featurePayload = accessValue?.let { ",\"feature_access\":\"$it\"" }.orEmpty()
        syncQueueDao.enqueue(
            SyncQueueEntity(
                tableName = "users",
                recordId = entity.id,
                operation = "INSERT",
                payload = "{\"id\":\"${entity.id}\",\"name\":\"$name\",\"role\":\"${role.name}\"$featurePayload}"
            )
        )
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
            synced = false,
            updatedAt = System.currentTimeMillis()
        )
        userDao.update(updated)
        val featurePayload = accessValue?.let { ",\"feature_access\":\"$it\"" }.orEmpty()
        syncQueueDao.enqueue(
            SyncQueueEntity(
                tableName = "users",
                recordId = userId,
                operation = "UPDATE",
                payload = "{\"id\":\"$userId\",\"name\":\"$name\",\"role\":\"${role.name}\"$featurePayload}"
            )
        )
        return updated.toDomain()
    }

    suspend fun changePin(userId: String, newPin: String) {
        val user = userDao.getById(userId) ?: return
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash(newPin, salt)
        userDao.update(user.copy(pinHash = hash, pinSalt = salt, synced = false, updatedAt = System.currentTimeMillis()))
        syncQueueDao.enqueue(
            SyncQueueEntity(tableName = "users", recordId = userId, operation = "UPDATE", payload = "{\"id\":\"$userId\"}")
        )
    }

    suspend fun toggleUserActive(userId: String) {
        val user = userDao.getById(userId) ?: return
        userDao.update(user.copy(isActive = !user.isActive, synced = false, updatedAt = System.currentTimeMillis()))
        syncQueueDao.enqueue(
            SyncQueueEntity(tableName = "users", recordId = userId, operation = "UPDATE", payload = "{\"id\":\"$userId\"}")
        )
    }

    private fun UserEntity.toDomain() = User(
        id = id,
        name = name,
        role = UserRole.valueOf(role),
        isActive = isActive,
        featureAccess = UserFeatureAccess.parse(featureAccess)
    )
}
