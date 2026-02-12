package com.ayakasir.app.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ayakasir.app.core.data.local.converter.Converters
import com.ayakasir.app.core.data.local.dao.CashWithdrawalDao
import com.ayakasir.app.core.data.local.dao.CategoryDao
import com.ayakasir.app.core.data.local.dao.GoodsReceivingDao
import com.ayakasir.app.core.data.local.dao.InventoryDao
import com.ayakasir.app.core.data.local.dao.ProductComponentDao
import com.ayakasir.app.core.data.local.dao.ProductDao
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
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.data.local.entity.TransactionEntity
import com.ayakasir.app.core.data.local.entity.TransactionItemEntity
import com.ayakasir.app.core.data.local.entity.UserEntity
import com.ayakasir.app.core.data.local.entity.VendorEntity
import com.ayakasir.app.core.data.local.entity.VariantEntity

@Database(
    entities = [
        UserEntity::class,
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
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AyaKasirDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
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
}
