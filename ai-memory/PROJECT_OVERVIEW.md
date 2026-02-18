# Project Overview

## What is AyaKasir
- Platform: Full native Android ERP application for small restaurants.
- Authentication: Users register using email. Accounts require admin verification before activation.
- Data Architecture: Primary database is hosted on Supabase. A local on-device database is maintained for backup and offline access.
- UI/UX: Responsive interface that adapts to different device types and screen sizes (phone and tablet).

## Core Features
- **POS:** Point of sale with transaction management
- **Inventory:** Stock tracking with product components
- **Purchasing:** Vendor management & goods receiving
- **Products:** Menu/raw materials with categories & variants
- **Cash Management:** Balance tracking & withdrawals
- **Reporting:** Dashboard with sales analytics
- **Settings:** Printer (Bluetooth), QRIS placeholder, users

## Architecture Stack
- **Pattern:** MVVM (ViewModel + StateFlow + UiState)
- **DI:** Hilt (@Singleton, @HiltViewModel, @AndroidEntryPoint)
- **Database:** Room (local cache & offline resilience, not source of truth)
- **Source of Truth**: Supabase Postgres
- **Sync:** Supabase Realtime (Postgres Changes → Room upsert → auto UI update) + pull-to-refresh fallback + pull on login + push on write + background push+pull (WorkManager, every 15 min)
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
- Printer: 58mm ESC/POS Bluetooth
- Sync Metadata:
  - Every entity has `updatedAt: Long`.
  - Sync state uses enum:
    { PENDING, SYNCING, SYNCED, FAILED, CONFLICT }.
- Offline & Sync:
  - **Realtime:** Supabase Realtime (Postgres Changes) subscribes to all 11 tenant tables via WebSocket → upserts to Room → Room Flow auto-emits to UI. Connected on login, disconnected on logout.
  - **Pull-to-refresh:** All data screens (POS, Dashboard, Inventory, Products, Categories, Vendors, Goods Receiving) support swipe-to-refresh via Material3 PullToRefreshBox. Triggers `SyncManager.pullAllFromSupabase()`.
  - Every write attempts immediate server push.
  - On failure → enqueue to SyncQueue.
  - WorkManager handles retry & reconciliation (push + pull every 15 min).
  - On email/password login → immediate full pull from Supabase for cross-device sync.
- Conflict Resolution:
  - Server is the source of truth by default.

## Build Config
- Gradle Kotlin DSL + Version Catalog (libs.versions.toml)
- Java 17 target
- ProGuard enabled for release
- Debug variant: `.debug` suffix
- Room DB version: 12

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