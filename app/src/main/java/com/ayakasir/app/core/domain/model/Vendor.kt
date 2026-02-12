package com.ayakasir.app.core.domain.model

data class Vendor(
    val id: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null
)
