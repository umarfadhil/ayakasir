package com.ayakasir.app.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ayakasir.app.core.data.local.converter.Converters
import com.ayakasir.app.core.data.local.dao.CashWithdrawalDao
import com.ayakasir.app.core.data.local.dao.CategoryDao
import com.ayakasir.app.core.data.local.dao.GoodsReceivingDao
import com.ayakasir.app.core.data.local.dao.InventoryDao
import com.ayakasir.app.core.data.local.dao.ProductComponentDao
import com.ayakasir.app.core.data.local.dao.ProductDao
import com.ayakasir.app.core.data.local.dao.RestaurantDao
import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.dao.TransactionDao
import com.ayakasir.app.core.data.local.dao.UserDao
import com.ayakasir.app.core.data.local.dao.VendorDao
import com.ayakasir.app.core.data.local.dao.VariantDao
import com.ayakasir.app.core.data.local.entity.CashWithdrawalEntity
import com.ayakasir.app.core.data.local.entity.CategoryEntity
import com.ayakasir.app.core.data.local.entity.GoodsReceivingEntity
import com.ayakasir.app.core.data.local.entity.GoodsReceivingItemEntity
import com.ayakasir.app.core.data.local.entity.InventoryEntity
import com.ayakasir.app.core.data.local.entity.ProductComponentEntity
import com.ayakasir.app.core.data.local.entity.ProductEntity
import com.ayakasir.app.core.data.local.entity.RestaurantEntity
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.data.local.entity.TransactionEntity
import com.ayakasir.app.core.data.local.entity.TransactionItemEntity
import com.ayakasir.app.core.data.local.entity.UserEntity
import com.ayakasir.app.core.data.local.entity.VendorEntity
import com.ayakasir.app.core.data.local.entity.VariantEntity

@Database(
    entities = [
        UserEntity::class,
        RestaurantEntity::class,
        CategoryEntity::class,
        ProductEntity::class,
        VariantEntity::class,
        ProductComponentEntity::class,
        VendorEntity::class,
        InventoryEntity::class,
        GoodsReceivingEntity::class,
        GoodsReceivingItemEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        SyncQueueEntity::class,
        CashWithdrawalEntity::class
    ],
    version = 12,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AyaKasirDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun restaurantDao(): RestaurantDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun variantDao(): VariantDao
    abstract fun productComponentDao(): ProductComponentDao
    abstract fun vendorDao(): VendorDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun goodsReceivingDao(): GoodsReceivingDao
    abstract fun transactionDao(): TransactionDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun cashWithdrawalDao(): CashWithdrawalDao

    companion object {
        /**
         * Migration 7→8: Replace synced:Boolean with sync_status:String (SyncStatus enum).
         * Adds sync_status column, migrates data, drops old synced column via table rebuild.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Helper: rebuild table to drop `synced` column and add `sync_status`
                fun rebuild(
                    table: String,
                    createSql: String,
                    columns: String
                ) {
                    db.execSQL("ALTER TABLE `$table` RENAME TO `${table}_old`")
                    db.execSQL(createSql)
                    db.execSQL(
                        "INSERT INTO `$table` ($columns, sync_status) " +
                        "SELECT $columns, CASE WHEN synced = 1 THEN 'SYNCED' ELSE 'PENDING' END " +
                        "FROM `${table}_old`"
                    )
                    db.execSQL("DROP TABLE `${table}_old`")
                }

                // 1. users
                rebuild(
                    "users",
                    "CREATE TABLE IF NOT EXISTS `users` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `pin_hash` TEXT NOT NULL, `pin_salt` TEXT NOT NULL, `role` TEXT NOT NULL, `feature_access` TEXT, `is_active` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                    "id, name, pin_hash, pin_salt, role, feature_access, is_active, updated_at, created_at"
                )

                // 2. categories
                rebuild(
                    "categories",
                    "CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, `category_type` TEXT NOT NULL, `sync_status` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                    "id, name, sort_order, category_type, updated_at"
                )

                // 3. products
                rebuild(
                    "products",
                    "CREATE TABLE IF NOT EXISTS `products` (`id` TEXT NOT NULL, `category_id` TEXT, `name` TEXT NOT NULL, `description` TEXT, `price` INTEGER NOT NULL, `image_path` TEXT, `is_active` INTEGER NOT NULL, `product_type` TEXT NOT NULL, `sync_status` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)",
                    "id, category_id, name, description, price, image_path, is_active, product_type, updated_at"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_category_id` ON `products` (`category_id`)")

                // 4. variants
                rebuild(
                    "variants",
                    "CREATE TABLE IF NOT EXISTS `variants` (`id` TEXT NOT NULL, `product_id` TEXT NOT NULL, `name` TEXT NOT NULL, `price_adjustment` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                    "id, product_id, name, price_adjustment, updated_at"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_variants_product_id` ON `variants` (`product_id`)")

                // 5. vendors
                rebuild(
                    "vendors",
                    "CREATE TABLE IF NOT EXISTS `vendors` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT, `address` TEXT, `sync_status` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                    "id, name, phone, address, updated_at"
                )

                // 6. inventory
                rebuild(
                    "inventory",
                    "CREATE TABLE IF NOT EXISTS `inventory` (`product_id` TEXT NOT NULL, `variant_id` TEXT NOT NULL, `current_qty` INTEGER NOT NULL, `min_qty` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`product_id`, `variant_id`))",
                    "product_id, variant_id, current_qty, min_qty, updated_at"
                )

                // 7. goods_receiving
                rebuild(
                    "goods_receiving",
                    "CREATE TABLE IF NOT EXISTS `goods_receiving` (`id` TEXT NOT NULL, `vendor_id` TEXT, `date` INTEGER NOT NULL, `notes` TEXT, `sync_status` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`vendor_id`) REFERENCES `vendors`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)",
                    "id, vendor_id, date, notes, updated_at"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goods_receiving_vendor_id` ON `goods_receiving` (`vendor_id`)")

                // 8. goods_receiving_items
                rebuild(
                    "goods_receiving_items",
                    "CREATE TABLE IF NOT EXISTS `goods_receiving_items` (`id` TEXT NOT NULL, `receiving_id` TEXT NOT NULL, `product_id` TEXT NOT NULL, `variant_id` TEXT NOT NULL, `qty` INTEGER NOT NULL, `cost_per_unit` INTEGER NOT NULL, `unit` TEXT NOT NULL, `sync_status` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`receiving_id`) REFERENCES `goods_receiving`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                    "id, receiving_id, product_id, variant_id, qty, cost_per_unit, unit, updated_at"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goods_receiving_items_receiving_id` ON `goods_receiving_items` (`receiving_id`)")

                // 9. transactions
                rebuild(
                    "transactions",
                    "CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `date` INTEGER NOT NULL, `total` INTEGER NOT NULL, `payment_method` TEXT NOT NULL, `status` TEXT NOT NULL, `sync_status` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                    "id, user_id, date, total, payment_method, status, updated_at"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_user_id` ON `transactions` (`user_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_sync_status` ON `transactions` (`sync_status`)")

                // 10. transaction_items
                rebuild(
                    "transaction_items",
                    "CREATE TABLE IF NOT EXISTS `transaction_items` (`id` TEXT NOT NULL, `transaction_id` TEXT NOT NULL, `product_id` TEXT NOT NULL, `variant_id` TEXT NOT NULL, `product_name` TEXT NOT NULL, `variant_name` TEXT, `qty` INTEGER NOT NULL, `unit_price` INTEGER NOT NULL, `subtotal` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                    "id, transaction_id, product_id, variant_id, product_name, variant_name, qty, unit_price, subtotal, updated_at"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_items_transaction_id` ON `transaction_items` (`transaction_id`)")

                // 11. product_components
                rebuild(
                    "product_components",
                    "CREATE TABLE IF NOT EXISTS `product_components` (`id` TEXT NOT NULL, `parent_product_id` TEXT NOT NULL, `component_product_id` TEXT NOT NULL, `component_variant_id` TEXT NOT NULL, `required_qty` INTEGER NOT NULL, `unit` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`parent_product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`component_product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                    "id, parent_product_id, component_product_id, component_variant_id, required_qty, unit, sort_order, updated_at"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_components_parent_product_id` ON `product_components` (`parent_product_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_components_component_product_id` ON `product_components` (`component_product_id`)")

                // 12. cash_withdrawals
                rebuild(
                    "cash_withdrawals",
                    "CREATE TABLE IF NOT EXISTS `cash_withdrawals` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `amount` INTEGER NOT NULL, `reason` TEXT NOT NULL, `date` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                    "id, user_id, amount, reason, date, updated_at"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_withdrawals_user_id` ON `cash_withdrawals` (`user_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_withdrawals_date` ON `cash_withdrawals` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_withdrawals_sync_status` ON `cash_withdrawals` (`sync_status`)")
            }
        }

        /**
         * Migration 8→9: Add email, phone fields to users, create restaurants table.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Rebuild users table to add email and phone
                db.execSQL("ALTER TABLE `users` RENAME TO `users_old`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `users` (" +
                    "`id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`email` TEXT, " +
                    "`phone` TEXT, " +
                    "`pin_hash` TEXT NOT NULL, " +
                    "`pin_salt` TEXT NOT NULL, " +
                    "`role` TEXT NOT NULL, " +
                    "`feature_access` TEXT, " +
                    "`is_active` INTEGER NOT NULL, " +
                    "`sync_status` TEXT NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO `users` (id, name, email, phone, pin_hash, pin_salt, role, feature_access, is_active, sync_status, updated_at, created_at) " +
                    "SELECT id, name, NULL, NULL, pin_hash, pin_salt, role, feature_access, is_active, sync_status, updated_at, created_at " +
                    "FROM `users_old`"
                )
                db.execSQL("DROP TABLE `users_old`")

                // 2. Create restaurants table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `restaurants` (" +
                    "`id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`owner_email` TEXT NOT NULL, " +
                    "`owner_phone` TEXT NOT NULL, " +
                    "`is_active` INTEGER NOT NULL, " +
                    "`sync_status` TEXT NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
                )
            }
        }

        /**
         * Migration 9→10: Add restaurant_id column to users table.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `users` ADD COLUMN `restaurant_id` TEXT")
            }
        }

        /**
         * Migration 10→11: Add password_hash and password_salt columns to users table.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `users` ADD COLUMN `password_hash` TEXT")
                db.execSQL("ALTER TABLE `users` ADD COLUMN `password_salt` TEXT")
            }
        }

        /**
         * Migration 11→12: Add restaurant_id column to all tenant-scoped tables.
         * Existing rows default to empty string; app will re-sync from Supabase to populate.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `restaurant_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `products` ADD COLUMN `restaurant_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `variants` ADD COLUMN `restaurant_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `inventory` ADD COLUMN `restaurant_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `product_components` ADD COLUMN `restaurant_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `vendors` ADD COLUMN `restaurant_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `goods_receiving` ADD COLUMN `restaurant_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `goods_receiving_items` ADD COLUMN `restaurant_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `restaurant_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `transaction_items` ADD COLUMN `restaurant_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `cash_withdrawals` ADD COLUMN `restaurant_id` TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
