# File Map

## Entry Points
- [MainActivity.kt](app/src/main/java/com/ayakasir/app/MainActivity.kt) - Single activity, schedules sync, sets theme
- [AyaKasirApp.kt](app/src/main/java/com/ayakasir/app/AyaKasirApp.kt) - Application class, Hilt entry, provides WorkManager `Configuration.Provider`
- [MainScaffold.kt](app/src/main/java/com/ayakasir/app/MainScaffold.kt) - Root composable with NavHost
- [AndroidManifest.xml](app/src/main/AndroidManifest.xml) - Permissions: INTERNET, BLUETOOTH_CONNECT/SCAN, responsive orientation, removes `androidx.work.WorkManagerInitializer` metadata from `androidx.startup.InitializationProvider`

## Navigation
- [Screen.kt](app/src/main/java/com/ayakasir/app/core/navigation/Screen.kt) - @Serializable sealed interface (28 screens)
- [AyaKasirNavHost.kt](app/src/main/java/com/ayakasir/app/core/navigation/AyaKasirNavHost.kt) - NavHost setup

## Core Architecture (app/src/main/java/com/ayakasir/app/core/)

### Data Layer
- **local/entity/** - Room entities (16+ tables): User, Product, Category, Variant, Transaction, GeneralLedger, SyncQueue, etc.
- **local/dao/** - Room DAOs with Flow<List<T>> queries (includes GeneralLedgerDao)
- **local/relation/** - Room relations/projections: ProductWithVariants, TransactionWithItems, GoodsReceivingItemWithProduct, GeneralLedgerExportRow, etc.
- **local/converter/** - Room type converters
- **local/datastore/** - DataStore for cash balance, QRIS settings, auth session persistence
- **remote/dto/** - Supabase DTOs with kotlinx.serialization
- **remote/** - SupabaseClientProvider.kt
- **repository/** - NetworkBound repositories (server authority, Room cache), includes GeneralLedgerRepository (cash ledger entries), LedgerExportRepository (tenant-scoped general ledger CSV export), QrisRepository (Supabase Storage upload + restaurant QRIS metadata)

### Domain
- **domain/model/**
    - Restaurant.kt
    - User.kt
    - Device.kt
    - Membership.kt
    - UserRole.kt
    - LedgerType.kt (INITIAL_BALANCE, SALE, SALE_QRIS, WITHDRAWAL, ADJUSTMENT, COGS)
    - CashBalance.kt (ledger-based: cash-affecting types only for Saldo Kas + non-cash types for info display)

### Infrastructure
- **sync/** - Immediate push, retry queue, reconciliation, conflict handling, RealtimeManager.kt (Supabase Realtime → Room upsert)
- **session/** - SessionManager.kt (current user, role, restaurant context), PinHasher.kt
- **printer/** - BluetoothPrinterManager.kt (Bluetooth + WiFi TCP connection + persisted saved target), EscPosReceiptBuilder.kt (required sales receipt format), PrinterConnectionType.kt
- **payment/** - PaymentGateway.kt interface, PaymentResult.kt
- **util/** - CurrencyFormatter, DateTimeUtil, UuidGenerator, NetworkMonitor, UnitConverter
- **di/** - AppModule.kt, NetworkModule.kt, DataStoreModule.kt

### UI
- **ui/theme/** - Color.kt, Type.kt, Shape.kt, Theme.kt (Material3)
- **ui/component/** - Reusable: PinInputField, ConfirmDialog, EmptyStateView, LoadingOverlay, CategoryFormDialog, CashBalanceCard, etc.

## Features (app/src/main/java/com/ayakasir/app/feature/)
- **auth/**
    ├── LandingScreen.kt (Masuk/Daftar choice)
    ├── EmailLoginScreen.kt (email/password login via Supabase Auth)
    ├── LoginScreen.kt (PIN unlock for app resume)
    ├── RegistrationScreen.kt + RegistrationViewModel.kt
    └── AuthViewModel.kt (handles both email/password + PIN auth)
- **pos/** - PosScreen.kt, PosViewModel.kt (post-payment `Cetak Struk` confirmation and direct print dispatch)
- **dashboard/** - DashboardScreen.kt, DashboardViewModel.kt (sales dashboard with selectable period chips: hari ini, bulan ini, tahun ini, custom date picker)
- **inventory/** - InventoryScreen.kt, InventoryViewModel.kt
- **product/** - ProductListScreen (menu-only list, grouped by category, top-right search toggle, per-item clone/delete), ProductFormScreen, CategoryListScreen, CategoryFormScreen, ProductManagementViewModel
- **purchasing/** - VendorListScreen, VendorFormScreen, GoodsReceivingListScreen, GoodsReceivingFormScreen, PurchasingViewModel
- **settings/** - SettingsScreen + SettingsViewModel (conditional rendering: full settings for OWNER/SETTINGS-feature, logout-only for cashier without SETTINGS, owner-only `Unduh Data` CSV export action), PrinterSettingsScreen + PrinterSettingsViewModel (Bluetooth/WiFi connect + test print), QrisSettingsScreen, UserManagementScreen + UserManagementViewModel, InitialBalanceSettingScreen

## Build Files
- [build.gradle.kts](build.gradle.kts) - Root project config
- [app/build.gradle.kts](app/build.gradle.kts) - App module: plugins, dependencies, Supabase config
- [libs.versions.toml](gradle/libs.versions.toml) - Version catalog (not read, assumed present)
- [settings.gradle.kts](settings.gradle.kts) - Project structure

## Database
- **app/schemas/** - Room schema export location
- Room DB version: 15

## Supabase Schema
- [schema.sql](supabase/schema.sql) - Canonical remote schema for fresh setup; `general_ledger.type` CHECK includes `INITIAL_BALANCE`, `SALE`, `SALE_QRIS`, `WITHDRAWAL`, `ADJUSTMENT`, `COGS`
- [migration_general_ledger_type_constraint.sql](supabase/migration_general_ledger_type_constraint.sql) - One-time migration for existing projects to expand `general_ledger.type` CHECK constraint

## Supabase Storage
- Bucket: `qris-images` (public) — stores QRIS images at path `{restaurantId}/qris.jpg`
- URL stored in `restaurants.qris_image_url` and cached in `QrisSettingsDataStore`
