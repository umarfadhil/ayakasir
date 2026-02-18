package com.ayakasir.app.core.domain.model

data class User(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val role: UserRole,
    val restaurantId: String? = null,
    val isActive: Boolean = true,
    val featureAccess: Set<UserFeature> = emptySet()
)
