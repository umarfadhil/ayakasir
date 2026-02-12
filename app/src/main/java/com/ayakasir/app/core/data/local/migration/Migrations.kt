package com.ayakasir.app.core.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add product_type column with default value
        database.execSQL("ALTER TABLE products ADD COLUMN product_type TEXT NOT NULL DEFAULT 'MENU_ITEM'")

        // Add category_type column with default value
        database.execSQL("ALTER TABLE categories ADD COLUMN category_type TEXT NOT NULL DEFAULT 'MENU'")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE users ADD COLUMN feature_access TEXT")
    }
}
