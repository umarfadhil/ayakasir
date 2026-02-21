# Project Overview

## What is AyaKasir
- Platform: Full native Android ERP application for small restaurants.
- Authentication: Users register using email. Accounts require admin verification before activation.
- Data Architecture: Primary database is hosted on Supabase. A local on-device database is maintained for backup and offline access.
- UI/UX: Responsive interface that adapts to different device types and screen sizes (phone and tablet).

## Core Features
- **POS:** Point of sale with transaction management + post-payment print confirmation dialog (`Cetak struk?`) for CASH and QRIS transactions
- **Inventory:** Stock tracking with product components
- **Purchasing:** Vendor management & goods receiving
- **Products:** Menu/raw materials with categories & variants, plus menu management helpers (grouped list by category, title search, clone item)
- **Cash Management:** General ledger-based balance tracking, initial balance, withdrawals, adjustments. QRIS sales and COGS (goods receiving) recorded in ledger but excluded from Saldo Kas calculation.
- **Reporting:** Dashboard with sales analytics and period filters (`Hari ini`, `Bulan ini`, `Tahun ini`, `Pilih tanggal`)
- **Settings:** Printer (Bluetooth + WiFi TCP), QRIS, users, owner-only `Unduh Data` CSV export (general ledger with item references)

## Architecture Stack
- **Pattern:** MVVM (ViewModel + StateFlow + UiState)
- **DI:** Hilt (@Singleton, @HiltViewModel, @AndroidEntryPoint)
- **Database:** Room (local cache & offline resilience, not source of truth)
- **Source of Truth**: Supabase Postgres
- **Sync:** Supabase Realtime (Postgres Changes → Room upsert → auto UI update) + pull-to-refresh fallback + pull on login + push on write + background push+pull (WorkManager, every 15 min)
- **Storage:** Supabase Storage (bucket: `qris-images`) for QRIS image files
- **UI:** Jetpack Compose + Material3 adaptive layouts
- **Navigation:** Type-safe Compose Navigation 2.8+ (@Serializable sealed interface)
- **Backend:** Supabase (PostgREST + Storage). Password validated locally via hash (no Supabase Auth dependency).

## Key Tech Decisions
- IDs:
  - UUID v4.
  - Client may generate for offline creation.
  - Client must accept server-generated IDs.
- Currency: Long (Rupiah, no decimals)
- Auth:
  - Primary: Email/password login (password hash stored in `users` table, validated locally via PinHasher). Required on first login and after explicit logout.
  - Secondary: 6-digit PIN for fast device unlock (app close/minimize only, session persisted via DataStore).
  - Registration: Both `restaurants` and `users` tables default `is_active = false`. Requires admin activation.
  - No dependency on Supabase Auth module — password and PIN both validated locally against hashes in `users` table.
- Printer: 58mm ESC/POS via Bluetooth SPP and WiFi TCP (default port 9100)
- Unit Conversion:
  - Inventory stores quantities in base units: g (mass), mL (volume), pcs (count).
  - Purchasing normalizes input to base unit at storage time (1 kg → 1000 g).
  - Stock decrement converts component unit to inventory base unit before subtraction.
  - Supported: kg ↔ g (×1000), L ↔ mL (×1000).
  - `UnitConverter` utility handles normalization, conversion, and display formatting.
- Sync Metadata:
  - Every entity has `updatedAt: Long`.
  - Sync state uses enum:
    { PENDING, SYNCING, SYNCED, FAILED, CONFLICT }.
- Offline & Sync:
  - **Realtime:** Supabase Realtime (Postgres Changes) subscribes to all 12 tenant tables + `restaurants` table (13 listeners total) via WebSocket → upserts to Room → Room Flow auto-emits to UI. Connected on login, disconnected on logout. The `restaurants` listener also updates `QrisSettingsDataStore` on change.
  - **Pull-to-refresh:** All data screens (POS, Dashboard, Inventory, Products, Categories, Vendors, Goods Receiving) support swipe-to-refresh via Material3 PullToRefreshBox. Triggers `SyncManager.pullAllFromSupabase()`.
  - Every write attempts immediate server push.
  - On failure → enqueue to SyncQueue.
  - WorkManager handles retry & reconciliation (push + pull every 15 min).
  - On email/password login → immediate full pull (blocking) from Supabase for cross-device sync.
  - On PIN unlock → background full pull from Supabase (non-blocking, catches changes made while app was closed).
- Conflict Resolution:
  - Server is the source of truth by default.

## Build Config
- Gradle Kotlin DSL + Version Catalog (libs.versions.toml)
- Java 17 target
- ProGuard enabled for release
- R8 release rules include:
  - `-dontwarn java.lang.management.ManagementFactory`
  - `-dontwarn java.lang.management.RuntimeMXBean`
  (required for Ktor debug-detector references on Android release shrink)
- Debug variant: `.debug` suffix
- Room DB version: 15
- WorkManager uses on-demand initialization via `AyaKasirApp : Configuration.Provider`; manifest removes default `androidx.work.WorkManagerInitializer` metadata.
- Supabase credentials: loaded from `local.properties` (gitignored), NOT hardcoded in `build.gradle.kts`
  - Keys: `SUPABASE_URL`, `SUPABASE_ANON_KEY`
  - Accessed via `BuildConfig.SUPABASE_URL`, `BuildConfig.SUPABASE_ANON_KEY`

## Identity, Roles & Tenancy
- Tenant:
  - Each restaurant represents one tenant.
  - A tenant is created automatically when a new user registers.
  - Users are linked to restaurants via `restaurant_id` FK (nullable for seed users).
- Owner (Admin):
  - The first registered and approved account becomes the restaurant owner.
  - Owner has full permissions, including managing users and devices.
- Cashier:
  - Created by the owner via:
    Pengaturan → Manajemen User.
  - Cashiers do not self-register.
- Device Login (Cashier Mode):
  - Requires:
    - Restaurant ID (system generated, simple identifier).
    - 6-digit PIN set by the owner.
  - No email/password required.
- Permission Authority:
  - Roles and permissions are validated by the server.
  - Local cache is for performance, not authority.
