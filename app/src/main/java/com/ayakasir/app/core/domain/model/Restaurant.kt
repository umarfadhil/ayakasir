package com.ayakasir.app.core.domain.model

data class Restaurant(
    val id: String,
    val name: String,
    val ownerEmail: String,
    val ownerPhone: String,
    val isActive: Boolean = true
)
