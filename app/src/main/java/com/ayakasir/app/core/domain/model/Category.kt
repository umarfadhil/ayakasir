package com.ayakasir.app.core.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Category(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val categoryType: CategoryType = CategoryType.MENU
)
