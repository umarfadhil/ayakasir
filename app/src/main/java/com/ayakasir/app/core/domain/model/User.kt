package com.ayakasir.app.core.domain.model

data class User(
    val id: String,
    val name: String,
    val role: UserRole,
    val isActive: Boolean = true,
    val featureAccess: Set<UserFeature> = emptySet()
)
