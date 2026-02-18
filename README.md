# AyaKasir ERP

**Version:** v1.0.0

---

## Overview
AyaKasir is a full native Android ERP application designed for small restaurants. It features robust offline-first data handling, multi-user authentication, and a modern, adaptive UI. The system uses Supabase as the source of truth, with a local Room database for offline access and backup.

---

## Core Features
- **POS:** Transaction management
- **Inventory:** Stock & product component tracking
- **Purchasing:** Vendor & goods receiving
- **Products:** Menu/raw materials, categories, variants
- **Cash Management:** Balance tracking, withdrawals
- **Reporting:** Sales analytics dashboard
- **Settings:** Bluetooth printer, QRIS, user management

---

## Architecture
- **Pattern:** MVVM (ViewModel + StateFlow + UiState)
- **DI:** Hilt
- **Database:** Room (local cache, not source of truth)
- **Source of Truth:** Supabase Postgres
- **Sync:** Supabase Realtime (WebSocket) → Room upsert → UI auto-update; background push/pull every 15 min
- **UI:** Jetpack Compose + Material3
- **Navigation:** Type-safe Compose Navigation
- **Backend:** Supabase (PostgREST + Storage). No Supabase Auth dependency; password/PIN validated locally.

---

## Identity, Roles & Tenancy
- **Tenant:** Each restaurant is a tenant. Users linked via `restaurant_id`.
- **Owner/Admin:** First approved user is owner, full permissions.
- **Cashier:** Created by owner, login via Restaurant ID + PIN (no email/password).
- **Permissions:** Server-validated; local cache for performance only.

---

## File Map
See `ai-memory/FILE_MAP.md` for a detailed file-by-file map. Key entry points:
- `app/src/main/java/com/ayakasir/app/MainActivity.kt` — Main activity, schedules sync, sets theme
- `app/src/main/java/com/ayakasir/app/AyaKasirApp.kt` — Application class, Hilt entry
- `app/src/main/java/com/ayakasir/app/MainScaffold.kt` — Root composable with NavHost
- `app/src/main/AndroidManifest.xml` — Permissions, orientation

---

## Code & Architecture Rules
- **Repository Pattern:** Network-first, fallback to local queue (see `ai-memory/CODE_RULES.md`)
- **ViewModel Pattern:** StateFlow, error handling, loading state
- **Screen Pattern:** Compose, collectAsStateWithLifecycle
- **Entity Rules:** UUID v4, syncStatus, updatedAt

---

## Build & Tech Stack
- **Gradle:** Kotlin DSL, Version Catalog
- **Java:** 17
- **ProGuard:** Enabled for release
- **Room DB version:** 12

---

## Database & Sync
- **Supabase:** Source of truth, Postgres
- **Room:** Local cache, offline support
- **Sync:** Realtime (WebSocket), pull-to-refresh, background sync, conflict resolution (server wins)

---

## Learnings & Conventions
- **Session:** SessionManager holds user + restaurant context
- **SyncStatus:** Enum (PENDING, SYNCING, SYNCED, FAILED, CONFLICT)
- **Migration:** Room migration uses rebuild pattern for schema changes
- **Auth:** Password/PIN validated locally, not via Supabase Auth

---

## License
Proprietary. All rights reserved.

Powered by Petalytix
