# File Map

## Entry Points
- [MainActivity.kt](app/src/main/java/com/ayakasir/app/MainActivity.kt) - Single activity, schedules sync, sets theme
- [AyaKasirApp.kt](app/src/main/java/com/ayakasir/app/AyaKasirApp.kt) - Application class, Hilt entry
- [MainScaffold.kt](app/src/main/java/com/ayakasir/app/MainScaffold.kt) - Root composable with NavHost
- [AndroidManifest.xml](app/src/main/AndroidManifest.xml) - Permissions: INTERNET, BLUETOOTH_CONNECT/SCAN, responsive orientation

## Navigation
- [Screen.kt](app/src/main/java/com/ayakasir/app/core/navigation/Screen.kt) - @Serializable sealed interface (28 screens)
- [AyaKasirNavHost.kt](app/src/main/java/com/ayakasir/app/core/navigation/AyaKasirNavHost.kt) - NavHost setup

## Core Architecture (app/src/main/java/com/ayakasir/app/core/)

### Data Layer
- **local/entity/** - Room entities (16+ tables): User, Product, Category, Variant, Transaction, GeneralLedger, SyncQueue, etc.
- **local/dao/** - Room DAOs with Flow<List<T>> queries (includes GeneralLedgerDao)
- **local/relation/** - Room relations: ProductWithVariants, TransactionWithItems, etc.
- **local/converter/** - Room type converters
- **local/datastore/** - DataStore for cash balance, QRIS settings, auth session persistence
- **remote/dto/** - Supabase DTOs with kotlinx.serialization
- **remote/** - SupabaseClientProvider.kt
- **repository/** - NetworkBound repositories (server authority, Room cache), includes GeneralLedgerRepository (cash ledger entries), QrisRepository (Supabase Storage upload + restaurant QRIS metadata)

### Domain
- **domain/model/**
    - Restaurant.kt
    - User.kt
    - Device.kt
    - Membership.kt
    - UserRole.kt
    - LedgerType.kt (INITIAL_BALANCE, SALE, WITHDRAWAL, ADJUSTMENT)
    - CashBalance.kt (ledger-based: initialBalance + totalSales - totalWithdrawals + totalAdjustments)

### Infrastructure
- **sync/** - Immediate push, retry queue, reconciliation, conflict handling, RealtimeManager.kt (Supabase Realtime → Room upsert)
- **session/** - SessionManager.kt (current user, role, restaurant context), PinHasher.kt
- **printer/** - BluetoothPrinterManager.kt, EscPosReceiptBuilder.kt
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
- **pos/** - PosScreen.kt, PosViewModel.kt
- **dashboard/** - DashboardScreen.kt, DashboardViewModel.kt
- **inventory/** - InventoryScreen.kt, InventoryViewModel.kt
- **product/** - ProductListScreen, ProductFormScreen, CategoryListScreen, CategoryFormScreen, ProductManagementViewModel
- **purchasing/** - VendorListScreen, VendorFormScreen, GoodsReceivingListScreen, GoodsReceivingFormScreen, PurchasingViewModel
- **settings/** - SettingsScreen + SettingsViewModel (conditional rendering: full settings for OWNER/SETTINGS-feature, logout-only for cashier without SETTINGS), PrinterSettingsScreen, QrisSettingsScreen, UserManagementScreen + UserManagementViewModel, InitialBalanceSettingScreen

## Build Files
- [build.gradle.kts](build.gradle.kts) - Root project config
- [app/build.gradle.kts](app/build.gradle.kts) - App module: plugins, dependencies, Supabase config
- [libs.versions.toml](gradle/libs.versions.toml) - Version catalog (not read, assumed present)
- [settings.gradle.kts](settings.gradle.kts) - Project structure

## Database
- **app/schemas/** - Room schema export location
- Room DB version: 15

## Supabase Storage
- Bucket: `qris-images` (public) — stores QRIS images at path `{restaurantId}/qris.jpg`
- URL stored in `restaurants.qris_image_url` and cached in `QrisSettingsDataStore`
