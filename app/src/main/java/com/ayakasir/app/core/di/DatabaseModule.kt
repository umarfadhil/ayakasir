package com.ayakasir.app.core.di

import android.content.Context
import androidx.room.Room
import com.ayakasir.app.core.data.local.AyaKasirDatabase
import com.ayakasir.app.core.data.local.migration.MIGRATION_4_5
import com.ayakasir.app.core.data.local.migration.MIGRATION_6_7
import com.ayakasir.app.core.data.local.dao.CashWithdrawalDao
import com.ayakasir.app.core.data.local.dao.CategoryDao
import com.ayakasir.app.core.data.local.dao.GeneralLedgerDao
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AyaKasirDatabase {
        return Room.databaseBuilder(
            context,
            AyaKasirDatabase::class.java,
            "ayakasir.db"
        )
            .addMigrations(MIGRATION_4_5, MIGRATION_6_7, AyaKasirDatabase.MIGRATION_7_8, AyaKasirDatabase.MIGRATION_8_9, AyaKasirDatabase.MIGRATION_9_10, AyaKasirDatabase.MIGRATION_10_11, AyaKasirDatabase.MIGRATION_11_12, AyaKasirDatabase.MIGRATION_12_13, AyaKasirDatabase.MIGRATION_13_14, AyaKasirDatabase.MIGRATION_14_15)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideUserDao(db: AyaKasirDatabase): UserDao = db.userDao()
    @Provides fun provideRestaurantDao(db: AyaKasirDatabase): RestaurantDao = db.restaurantDao()
    @Provides fun provideCategoryDao(db: AyaKasirDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideProductDao(db: AyaKasirDatabase): ProductDao = db.productDao()
    @Provides fun provideVariantDao(db: AyaKasirDatabase): VariantDao = db.variantDao()
    @Provides fun provideProductComponentDao(db: AyaKasirDatabase): ProductComponentDao = db.productComponentDao()
    @Provides fun provideVendorDao(db: AyaKasirDatabase): VendorDao = db.vendorDao()
    @Provides fun provideInventoryDao(db: AyaKasirDatabase): InventoryDao = db.inventoryDao()
    @Provides fun provideGoodsReceivingDao(db: AyaKasirDatabase): GoodsReceivingDao = db.goodsReceivingDao()
    @Provides fun provideTransactionDao(db: AyaKasirDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideSyncQueueDao(db: AyaKasirDatabase): SyncQueueDao = db.syncQueueDao()
    @Provides fun provideCashWithdrawalDao(db: AyaKasirDatabase): CashWithdrawalDao = db.cashWithdrawalDao()
    @Provides fun provideGeneralLedgerDao(db: AyaKasirDatabase): GeneralLedgerDao = db.generalLedgerDao()
}
