# Session Learnings

## Current State (2026-02-16)
- Project version: 1.0.0
- DB version: 10 (migration 9→10: added restaurant_id to users table)
- Sync model: `syncStatus: String` (SyncStatus enum) — replaces old `synced: Boolean`
- Repository pattern: Network-first (try push → fallback queue)
- Auth flow: Landing (Masuk/Daftar) → Login (PIN) or Registration → POS

## Architecture Insights
- Supabase is source of truth, Room is local cache
- Every write triggers immediate push attempt via SyncManager.pushToSupabase()
- On push failure → enqueue to SyncQueue + requestImmediateSync via WorkManager
- SessionManager holds user + restaurantId (multi-tenancy implemented)
- Multi-tenancy: Restaurant entity stores business info, linked to owner user via email/phone

## SyncStatus Enum
- Location: `core/domain/model/SyncStatus.kt`
- Values: PENDING, SYNCING, SYNCED, FAILED, CONFLICT
- Stored as String in Room (`@ColumnInfo(name = "sync_status")`)
- DTOs use `@SerialName("sync_status") val syncStatus: String`

## Network-First Write Pattern
```
1. dao.insert(entity.copy(syncStatus = PENDING))
2. try { syncManager.pushToSupabase(table, op, id) }
3. catch { syncQueueDao.enqueue(...); syncScheduler.requestImmediateSync() }
```

## Room Migration Pattern
- MIGRATION_7_8: Full table rebuild (rename→create→copy→drop) to replace `synced:Boolean` with `sync_status:String`
- Defined in AyaKasirDatabase.Companion
- Registered in DatabaseModule.provideDatabase()
- **LESSON:** `ALTER TABLE ADD COLUMN` alone is insufficient when removing columns — SQLite has no DROP COLUMN before API 34. Must use the rebuild pattern: RENAME old → CREATE new → INSERT...SELECT → DROP old
- **LESSON:** `fallbackToDestructiveMigration()` only triggers when NO migration path exists. If a migration IS found but produces wrong schema, Room still crashes
- **LESSON:** Room validates every column exactly — extra columns in DB (not in Entity) cause `IllegalStateException`. Also validates DEFAULT values must match

## Known Issues from Memory
1. Background agents may not persist files to disk
2. Large JSON (98K+ chars) crashes Read tool → use Node.js extraction
3. `dependencyResolutionManagement` typo in settings.gradle.kts

## Quick Wins for Future Tasks
- To add new entity: Create Entity → DAO → Repository → ViewModel → Screen → add to Screen.kt + NavHost
- To modify sync: Check ConflictResolver strategy first
- To debug offline: Check SyncQueueEntity table + SyncWorker logs
- To add printer template: Modify EscPosReceiptBuilder (32 chars/line)

## Common Task Patterns
1. **Add feature:** Entity → DAO → Repo → VM → Screen → Navigation
2. **Fix sync:** Check SyncWorker logs, verify pushToSupabase + queue fallback
3. **Debug cash:** Check CashBalanceDataStore + CashWithdrawalRepository
4. **Printer issue:** BluetoothPrinterManager (check MAC address persistence)

## Registration Flow (Added 2026-02-15)
- **First Run:** App checks `UserRepository.hasAnyUsers()` → if false, shows RegistrationScreen
- **Registration Fields:**
  1. Nama (user name)
  2. E-mail
  3. No. Telefon (phone)
  4. Nama Usaha (business name → creates Restaurant entity)
  5. Password (min 6 chars, stored for Supabase auth)
  6. PIN (6-digit, for device unlock)
- **Registration Process:**
  1. Create Restaurant entity with business name, owner email/phone
  2. Create User entity with owner role, email, phone, hashed PIN
  3. Navigate to Login screen (user must login with PIN after registration)
- **Files Modified:**
  - UserEntity: Added `email`, `phone` fields
  - User domain: Added `email`, `phone` fields
  - Created: RestaurantEntity, RestaurantDao, RestaurantDto, RestaurantRepository
  - Created: RegistrationViewModel, RegistrationScreen
  - Navigation: Added Screen.Registration, composable route
  - MainActivity: Check hasAnyUsers() to determine authStartDestination
  - MainScaffold: Accept authStartDestination parameter
  - AyaKasirDatabase: Version 8→9 migration (add email/phone, create restaurants table)
  - AuthViewModel: Removed seedDefaultOwner() — no more default owner
- **Migration 8→9:** Rebuild users table with email/phone (NULL default), create restaurants table
- **Key Change:** No default owner seeded anymore — first user must register via form

## Supabase Schema Alignment (Updated 2026-02-16)
- **Schema Version:** Aligned with App DB v10
- **MCP Configuration:** `.vscode/mcp.json` points to Supabase project `tlkykpcznaieulbwkapc`
- **Schema Files:**
  - `supabase/schema.sql`: Full schema for fresh Supabase setup
  - `supabase/migration_v8_to_v9.sql`: Migration script for existing databases
- **Key Schema Changes:**
  1. **sync_status field:** All tables migrated from `synced BOOLEAN` → `sync_status TEXT` (values: PENDING, SYNCING, SYNCED, FAILED, CONFLICT)
  2. **restaurants table:** New table with fields: id, name, owner_email, owner_phone, is_active, sync_status, updated_at, created_at
  3. **users table:** Added `email TEXT` and `phone TEXT` fields (nullable)
- **DTO Mappers Updated:**
  - UserEntity ↔ UserDto: Now includes email and phone fields
  - RestaurantEntity ↔ RestaurantDto: New mappers added to DtoEntityMappers.kt
- **Migration Strategy:**
  - For fresh setup: Run `supabase/schema.sql` directly
  - For existing DB: Run `supabase/migration_v8_to_v9.sql` to migrate safely
  - Migration preserves all data: `synced=true` → `sync_status='SYNCED'`, `synced=false` → `sync_status='PENDING'`
- **RLS Policies:** Permissive "allow all" policies enabled for development (change for production)
- **Next Steps After Migration:**
  1. Run migration script in Supabase SQL Editor
  2. Verify all tables have `sync_status` column
  3. Verify restaurants table exists
  4. Test app registration flow → should create restaurant + owner user in Supabase

## Fixes Applied (2026-02-15)

### Registration Flow Fix
- **Problem:** After registration, app auto-logged in and went straight to POS/dashboard
- **Fix:** Removed `sessionManager.login()` from RegistrationViewModel. Changed NavHost to navigate to `Screen.Login` after registration success instead of `Screen.Pos`
- **Result:** User must now login with PIN after registering

### SyncManager Missing "restaurants" Table
- **Problem:** SyncManager.pushUpsert() had no handler for "restaurants" table, so restaurant records never synced to Supabase. User records may also fail if the push exception was swallowed.
- **Fix:** Added `RestaurantDao` to SyncManager constructor. Added `"restaurants"` case in `pushUpsert()`. Added `markSynced()` query to RestaurantDao.
- **LESSON:** When adding a new entity/table, always add its handler to SyncManager.pushUpsert() — otherwise sync silently does nothing for that table.

### Logout Button in Settings
- **Problem:** No way to log out from the app
- **Fix:** Added "Keluar" button (red) at bottom of SettingsScreen. Wired `onLogout` callback through AyaKasirNavHost → MainScaffold. Logout calls `sessionManager.logout()` + navigates to Landing with full backstack clear.
- **Files changed:** SettingsScreen.kt, AyaKasirNavHost.kt, MainScaffold.kt

### Landing Page Added
- **Problem:** App went directly to Registration or Login based on hasAnyUsers() check — no choice for user
- **Fix:** Added `Screen.Landing` + `LandingScreen.kt` with "Masuk" and "Daftar" buttons. App always starts at Landing. Removed `UserRepository` dependency from `MainActivity`.
- **Files created:** LandingScreen.kt
- **Files changed:** Screen.kt, AyaKasirNavHost.kt, MainActivity.kt, MainScaffold.kt
- **Flow:** Landing → Login (PIN) or Registration → Login → POS

## Registration UX Improvements (2026-02-16)

### PIN Input Changed from Keypad to TextField
- **Problem:** Registration screen used NumericKeypad component for PIN input, which was bulky and not consistent with other form fields
- **Fix:** Replaced PinInputField + NumericKeypad with standard OutlinedTextField
- **Files changed:**
  - RegistrationScreen.kt: Removed NumericKeypad/PinInputField components, added OutlinedTextField with KeyboardType.NumberPassword
  - RegistrationViewModel.kt: Replaced `onPinDigitEntered()` and `onPinBackspace()` with single `onPinChanged()` method that validates digits-only and max 6 chars
- **Result:** Cleaner UI, consistent form experience

### Back Buttons Added to Auth Screens
- **Problem:** No way to navigate back from Login or Registration screens to Landing screen
- **Fix:** Added back button (ArrowBack icon) to top of both LoginScreen and RegistrationScreen
- **Files changed:**
  - LoginScreen.kt: Added `onNavigateBack` parameter, added IconButton with ArrowBack at top
  - RegistrationScreen.kt: Added `onNavigateBack` parameter, added IconButton with ArrowBack at top
  - AyaKasirNavHost.kt: Wired `onNavigateBack = { navController.popBackStack() }` for both screens
- **Result:** Users can navigate back using the back button, improving UX

### User Table Sync Fix (2026-02-16)
- **Issue:** Restaurant records created in Supabase during registration, but user records were NOT.
- **Root Cause:** Supabase `users` table had a `restaurant_id UUID NOT NULL` column (FK to restaurants) that the app code did not include in UserEntity, UserDto, or mappers. When pushToSupabase tried to upsert a user, Supabase rejected it due to missing required `restaurant_id`. The error was caught silently by the try-catch, and the record was enqueued to SyncQueue (which would also fail for the same reason). Additionally, Supabase was missing the `feature_access` column.
- **Fix Applied:**
  1. **Supabase:** Made `restaurant_id` nullable (ALTER COLUMN DROP NOT NULL), added `feature_access TEXT` column
  2. **UserEntity:** Added `restaurantId: String?` field with `@ColumnInfo(name = "restaurant_id")`
  3. **UserDto:** Added `restaurantId: String?` field with `@SerialName("restaurant_id")`
  4. **DtoEntityMappers:** Added `restaurantId` to both Entity→DTO and DTO→Entity mappers
  5. **User domain model:** Added `restaurantId: String?` field
  6. **UserRepository.registerOwner():** Added `restaurantId` parameter, passes it to UserEntity constructor
  7. **RegistrationViewModel:** Passes `restaurant.id` from created restaurant to `registerOwner()`
  8. **Room migration 9→10:** `ALTER TABLE users ADD COLUMN restaurant_id TEXT`
  9. **DatabaseModule:** Registered MIGRATION_9_10
  10. **schema.sql:** Added `restaurant_id UUID REFERENCES restaurants(id)` to users table
  11. **migration_v8_to_v9.sql:** Added `restaurant_id` and `feature_access` column additions
- **LESSON:** When Supabase schema has columns the app DTO doesn't include, upserts fail silently (caught by try-catch). Always verify Supabase table schema matches DTO fields exactly. Use Supabase MCP `list_tables` to check actual live schema vs code.
- **LESSON:** Schema drift between Supabase (manual edits) and app code (entity/dto) is a common source of silent sync failures.

## Code Patterns Learned (2026-02-16)

### TextField Input Validation Pattern
```kotlin
fun onPinChanged(pin: String) {
    // Only allow digits and max N characters
    if (pin.all { it.isDigit() } && pin.length <= 6) {
        _uiState.update { it.copy(pin = pin, error = null) }
    }
}
```

### Back Button Pattern for Compose Screens
```kotlin
// 1. Add parameter to composable
@Composable
fun MyScreen(onNavigateBack: () -> Unit) {
    Column {
        IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
        }
        // rest of UI
    }
}

// 2. Wire in NavHost
composable<Screen.MyScreen> {
    MyScreen(onNavigateBack = { navController.popBackStack() })
}
```

## UI Responsiveness Fix (2026-02-16)

### Login Screen Vertical Overflow on Small Screens
- **Problem:** On smaller screens, the PIN pad was not fully visible and required scrolling down, creating poor UX
- **Root Cause:** Layout used `Column(fillMaxSize).verticalScroll()` wrapping another `Column(fillMaxSize, Center)`. The inner Column's `fillMaxSize()` tried to fill infinite scroll container height, causing content to overflow viewport.
- **Fix Applied:**
  1. Removed `verticalScroll(rememberScrollState())` from outer Column
  2. Removed unused imports: `rememberScrollState`, `verticalScroll`
  3. Added `weight(1f)` to inner Column to make it fill remaining space after back button
  4. Kept `Arrangement.Center` on inner Column to vertically center PIN pad content
- **Files changed:** LoginScreen.kt
- **Result:** Content now fits screen without scrolling. Back button stays at top, PIN pad and title are centered in remaining space.
- **LESSON:** When centering content in Compose, avoid nesting `fillMaxSize()` inside scrollable containers. Use `weight(1f)` to distribute space instead of `fillMaxSize()` for flexible layouts. Remove scroll when content can fit with proper space distribution.

### Compile Error: "Cannot access weight: it is internal"
- **Problem:** Build failed with error `Cannot access 'val RowColumnParentData?.weight: Float': it is internal in file` after adding `import androidx.compose.foundation.layout.weight`
- **Root Cause:** `weight` is a `Modifier` extension function, NOT a standalone type that should be imported. The import tries to access internal implementation details.
- **Fix Applied:** Removed `import androidx.compose.foundation.layout.weight` - no import needed
- **Files changed:** LoginScreen.kt
- **Result:** `Modifier.weight(1f)` works without explicit import because it's already available as part of Compose foundation layout
- **LESSON:** Compose modifier functions like `weight()`, `padding()`, `fillMaxSize()` are extension functions on `Modifier` and should NOT be imported separately. Only import layout components (Column, Row, Box) and container types, not modifier functions.

### Login Screen Pin Pad Clipped on Small Screens (2026-02-16)
- **Problem:** On screens smaller than 10.1 inch, the PIN keypad was not fully visible — bottom rows were clipped with no way to scroll
- **Root Cause:** Inner Column used `fillMaxSize()` + `weight(1f)` + `Arrangement.Center` but no scroll. When content height exceeded the weighted space, bottom content was clipped.
- **Fix Applied:**
  1. Changed `fillMaxSize()` to `fillMaxWidth()` on inner Column (avoids infinite height in scroll container)
  2. Added `.verticalScroll(rememberScrollState())` to inner Column
  3. Kept `weight(1f)` to constrain scroll area height, and `Arrangement.Center` so content is centered when it fits
  4. Added imports: `rememberScrollState`, `verticalScroll`, `fillMaxWidth`
- **Files changed:** LoginScreen.kt
- **Result:** On large screens content is centered as before. On small screens, content scrolls so the full keypad is accessible.
- **LESSON:** `weight(1f)` + `verticalScroll` is a valid combination — weight constrains the max height, scroll allows overflow. Use `fillMaxWidth()` (not `fillMaxSize()`) with this pattern to avoid infinite constraints.

## Auth Flow Refactor (2026-02-16)

### Email/Password Login Required (First Login & After Logout)
- **Change:** Users must now authenticate with email/password (Supabase Auth) on first launch or after explicit logout. PIN is only for app resume (close/minimize).
- **Auth Flow:**
  - First launch / after logout: Landing → EmailLogin (Supabase Auth) → POS
  - App resume (close/minimize): PIN unlock → POS
  - Registration: Landing → Registration → Success message → Landing
  - Logout from Pengaturan: clears DataStore → Landing
- **Files created:** `EmailLoginScreen.kt`, `AuthSessionDataStore.kt`
- **Files modified:** `AuthViewModel.kt`, `SessionManager.kt`, `MainActivity.kt`, `MainScaffold.kt`, `AyaKasirNavHost.kt`, `Screen.kt`, `UserDao.kt`, `UserRepository.kt`, `NetworkModule.kt`, `RegistrationViewModel.kt`, `RegistrationScreen.kt`, `RestaurantRepository.kt`, `app/build.gradle.kts`
- **Key patterns:**
  - `SessionManager.loginFull()` persists to DataStore, `loginPin()` is in-memory only, `logout()` clears DataStore
  - `AuthSessionDataStore` uses DataStore Preferences with keys: auth_user_id, auth_restaurant_id, auth_is_full_login
  - `MainActivity` checks `getPersistedUserId()` to route to PIN vs Landing on startup

### Registration Defaults is_active=FALSE
- **Change:** `RestaurantRepository.create()` and `UserRepository.registerOwner()` now set `isActive = false`
- **Registration also calls `supabaseClient.auth.signUpWith(Email)` for Supabase Auth account creation**
- **RegistrationScreen shows success message with "Kembali ke Beranda" button instead of auto-navigating**

## Supabase Email Confirmation Fix (2026-02-17)

### Issue: "Email belum dikonfirmasi" error after admin approval
- **Problem:** User registered → admin set is_active=TRUE → login failed with "Email belum dikonfirmasi"
- **Root Cause:** Supabase Auth's `signUpWith(Email)` sends confirmation email by default. `signInWith(Email)` rejects unconfirmed emails even if local is_active=true.
- **Solution Applied:**
  1. **Registration:** Wrapped `signUpWith(Email)` in try-catch - registration succeeds locally even if Supabase email confirmation pending
  2. **Login:** Check is_active first, then validate password via Supabase. If "email not confirmed" error, show clear message instructing user to disable email confirmation in Supabase Dashboard.
  3. **Recommended Supabase Config:** Disable email confirmations: Dashboard → Authentication → Email Auth → "Confirm email" = OFF
- **Files modified:** `RegistrationViewModel.kt`, `AuthViewModel.kt`
- **Key insight:** Admin approval (is_active flag) and Supabase email confirmation are redundant gates. Since admin approval is the intended primary gate, email confirmation should be disabled in Supabase settings.
- **Error message improved:** Now instructs user how to fix Supabase config if email confirmation is still enabled

## Login is_active Stale Cache Fix (2026-02-17)

### Issue: "Akun belum diaktivasi" despite is_active=TRUE in Supabase
- **Problem:** User registered (is_active=false locally) → admin set is_active=TRUE in Supabase → login still failed because `AuthViewModel.loginWithEmail()` called `getUserByEmail()` which reads from local Room cache (still false).
- **Root Cause:** `getUserByEmail()` queries Room only. Supabase (source of truth) had is_active=true but local cache was never updated after admin changed it remotely.
- **Solution Applied:**
  1. **UserRepository:** Added `getUserByEmailRemote(email)` — fetches user from Supabase via PostgREST `select { filter { eq("email", email) } }`, updates local Room cache with `insert(OnConflictStrategy.REPLACE)`, falls back to local cache on network failure.
  2. **UserRepository:** Added `SupabaseClient` as constructor dependency (auto-resolved by Hilt since it's already in DI graph).
  3. **AuthViewModel:** Changed `loginWithEmail()` to call `getUserByEmailRemote()` instead of `getUserByEmail()`.
- **Files modified:** `UserRepository.kt`, `AuthViewModel.kt`
- **LESSON:** For login/auth flows, always fetch from Supabase (source of truth) rather than local Room cache, because admin may have changed is_active remotely. Local-only reads are fine for in-app display but not for access-control gate checks.

## Remove Supabase Auth — Local Password Hash (2026-02-17)

### Issue: "Email atau password salah" despite correct credentials
- **Problem:** After registration + admin set is_active=TRUE + disabled Supabase email confirmation + disabled "Allow new users to sign up" → login failed with "Email atau password salah".
- **Root Cause:** During registration, `signUpWith(Email)` was wrapped in try-catch and silently failed (Supabase Auth "Allow new users to sign up" was disabled). The user was never created in Supabase Auth's `auth.users` table. So `signInWith(Email)` during login failed with "invalid login credentials" because the auth account didn't exist.
- **Architectural Decision:** Removed Supabase Auth dependency entirely. Password hash is now stored in the `users` Postgres table (like PIN hash), validated locally via `PinHasher.verify()`.
- **Solution Applied:**
  1. **UserEntity:** Added `password_hash TEXT` and `password_salt TEXT` columns (nullable).
  2. **UserDto:** Added matching `@SerialName` fields.
  3. **DtoEntityMappers:** Added `passwordHash`/`passwordSalt` to both Entity→DTO and DTO→Entity.
  4. **Room migration 10→11:** `ALTER TABLE users ADD COLUMN password_hash TEXT; ALTER TABLE users ADD COLUMN password_salt TEXT`.
  5. **Supabase migration:** Same ALTER TABLE applied via MCP.
  6. **DatabaseModule:** Registered `MIGRATION_10_11`. DB version bumped to 11.
  7. **UserRepository.registerOwner():** Now accepts `password` param, hashes it with `PinHasher.hash()`, stores `passwordHash`/`passwordSalt` in entity.
  8. **UserRepository.authenticateByEmail():** New method — fetches user from Supabase Postgres, updates local cache, verifies password hash locally. Returns sealed class `AuthResult` (Success/NotFound/Inactive/WrongPassword/NoPassword).
  9. **AuthViewModel:** Replaced Supabase Auth `signInWith(Email)` with `userRepository.authenticateByEmail()`. Removed `SupabaseClient` dependency.
  10. **RegistrationViewModel:** Removed Supabase Auth `signUpWith(Email)` call and `SupabaseClient` dependency. Passes `password` to `registerOwner()`.
- **Files modified:** `UserEntity.kt`, `UserDto.kt`, `DtoEntityMappers.kt`, `AyaKasirDatabase.kt`, `DatabaseModule.kt`, `UserRepository.kt`, `AuthViewModel.kt`, `RegistrationViewModel.kt`
- **LESSON:** Supabase Auth and custom admin-approval flows conflict. Supabase Auth has its own user table (`auth.users`), signup settings, and email confirmation — all of which can silently block login even when the app's `users` table is correctly configured. For apps that use their own approval gate (is_active flag), storing password hash locally (same pattern as PIN) is simpler and more reliable than depending on Supabase Auth.
- **LESSON:** `PinHasher` is a generic hash utility — it works for any secret (PIN, password), not just PINs. Reuse it for password hashing.

## SettingsScreen Scrollable (2026-02-17)

### Settings list not scrollable on small screens
- **Problem:** On smaller screens, settings cards below the fold were not reachable.
- **Fix:** Added `.verticalScroll(rememberScrollState())` to the outer `Column` in `SettingsScreen.kt`. Added imports `rememberScrollState`, `verticalScroll`.
- **Files changed:** `SettingsScreen.kt`
- **Pattern:** Static settings lists should always use `verticalScroll` on the root `Column` rather than `LazyColumn` — no dynamic data, no reuse/recycle needed.

## Login Inactive Bug — Use Supabase Data Directly (2026-02-17)

### Issue: "Akun belum diaktivasi" despite is_active=TRUE in both Supabase users and restaurants tables
- **Problem:** User changed `is_active` to TRUE in Supabase dashboard + toggled Auth settings, but login still returned `AuthResult.Inactive`.
- **Root Cause:** `authenticateByEmail()` fetched from Supabase and inserted into Room, but then **re-read from Room** for the isActive check. The `catch (_: Exception)` block swallowed ALL exceptions (deserialization, Room insert errors, etc.), not just network failures. If any step after the Supabase fetch failed silently, the stale local cache (isActive=false from registration) was used.
- **Fix Applied:** Capture the Supabase DTO-mapped entity in a local variable (`fetchedEntity`) and use it directly for auth checks: `val entity = fetchedEntity ?: userDao.getByEmail(email)`. Room insert still happens for cache, but auth logic no longer depends on it.
- **Files modified:** `UserRepository.kt`
- **LESSON:** When fetching from a remote source of truth for access-control checks, use the fetched data directly — don't re-read from local cache after insert. The insert-then-read pattern is fragile when catch-all exception handlers mask insert failures.
- **LESSON:** `catch (_: Exception)` is dangerous for Supabase PostgREST calls — it hides deserialization errors, not just network failures. Consider catching specific exceptions or at least logging the error.

## Multi-Tenancy: restaurant_id on All Tables (2026-02-17)

### Change: Added `restaurant_id` column to all 11 tenant-scoped tables
- **Goal:** Data isolation — each restaurant's data is completely separate. Users of restaurant A cannot see restaurant B's data.
- **Tables affected:** `categories`, `products`, `variants`, `inventory`, `product_components`, `vendors`, `goods_receiving`, `goods_receiving_items`, `transactions`, `transaction_items`, `cash_withdrawals`
- **DB version:** 11 → 12 (MIGRATION_11_12)
- **Approach:** Denormalized — `restaurant_id` stored on ALL tables (including child tables like `variants`, `transaction_items`). This avoids complex JOINs for filtering and is standard multi-tenancy practice.

### What was changed:
1. **Entities (11):** Added `@ColumnInfo(name = "restaurant_id") val restaurantId: String = ""`
2. **DTOs (11):** Added `@SerialName("restaurant_id") val restaurantId: String = ""`
3. **DtoEntityMappers:** All `toDto()` and `toEntity()` now pass `restaurantId` (nullable → `?: ""` for DTOs)
4. **DAOs (7):** All "list all" queries now require `restaurantId: String` parameter with `WHERE restaurant_id = :restaurantId` filter:
   - CategoryDao: `getAll()`, `getAllDirect()`, `getAllMenuCategories()`, `getAllRawMaterialCategories()`
   - ProductDao: all `getAllActive*`, `getAll`, `getActiveWith*` queries
   - VendorDao: `getAll()`, `getAllDirect()`
   - GoodsReceivingDao: `getAllWithItems()`
   - TransactionDao: all date-range and aggregate queries
   - CashWithdrawalDao: all `getAll*`, `getByDateRange*`, `getTotalByDateRange*`
   - InventoryDao: `getAll()`, `getRawMaterialInventory()`, `getLowStock()`, `getLowStockCount()`
5. **Repositories (7):** Injected `SessionManager`, added `private val restaurantId: String get() = sessionManager.currentRestaurantId ?: ""`, threaded through all DAO calls and entity construction:
   - CategoryRepository, ProductRepository, VendorRepository, InventoryRepository, CashWithdrawalRepository, TransactionRepository, PurchasingRepository
6. **AyaKasirDatabase:** Version 12, MIGRATION_11_12 adds `restaurant_id TEXT NOT NULL DEFAULT ''` to all 11 tables
7. **DatabaseModule:** MIGRATION_11_12 registered
8. **supabase/schema.sql:** `restaurant_id UUID REFERENCES restaurants(id)` added to all 11 tables, with indices `idx_<table>_restaurant`

### Key Design Decisions:
- Child tables (variants, product_components, goods_receiving_items, transaction_items, inventory) get `restaurant_id` denormalized even though they could derive it from parent — avoids JOIN complexity
- DAO queries for child tables (VariantDao, ProductComponentDao, etc.) do NOT need restaurantId filter since they always query by parentId (which is already restaurant-scoped)
- `restaurantId` defaults to `""` for migration safety — existing rows get empty string, and re-sync from Supabase populates correctly
- `sessionManager.currentRestaurantId ?: ""` in repositories is safe since restaurant doesn't change mid-session

### Files modified:
- 11 entity files, 11 DTO files, DtoEntityMappers.kt
- CategoryDao, ProductDao, VariantDao (no query changes, only entity field), InventoryDao, VendorDao, GoodsReceivingDao, TransactionDao, CashWithdrawalDao, ProductComponentDao (no query changes)
- CategoryRepository, ProductRepository, VendorRepository, InventoryRepository, CashWithdrawalRepository, TransactionRepository, PurchasingRepository
- AyaKasirDatabase.kt (version 12 + MIGRATION_11_12), DatabaseModule.kt
- supabase/schema.sql (version comment updated to v12)

## Cross-Device Sync on Login (2026-02-18)

### Issue: Data not synced when logging in on a new device
- **Problem:** Records saved on Device A were in Supabase, but logging in on Device B showed empty Room DB. No data appeared.
- **Root Cause:** `SyncManager.syncAll()` was push-only (processed the SyncQueue). There was no pull mechanism. On a new device, Room is empty and `syncAll()` never fetched from Supabase.
- **Fix Applied:**
  1. **`SyncManager.kt`:** Added `pullAllFromSupabase(restaurantId: String)` — fetches all 12 tenant-related tables (categories, products, variants, vendors, inventory, product_components, goods_receiving, goods_receiving_items, transactions, transaction_items, cash_withdrawals, users) in parallel via `coroutineScope { async { } }` and upserts each to Room using `dao.insert()` (REPLACE strategy).
  2. **`SyncManager.kt`:** Integrated pull as Phase 2 of `syncAll()` — after pushing queued items, calls `pullAllFromSupabase(sessionManager.currentRestaurantId)` when session is active. This means every periodic sync (15 min) also reconciles cross-device changes.
  3. **`SyncManager.kt`:** Added `SessionManager` to constructor (for `currentRestaurantId` in `syncAll()`). Added all DTO imports + `toEntity` mapper import + coroutines imports.
  4. **`AuthViewModel.kt`:** Added `SyncManager` to constructor. After `sessionManager.loginFull()` succeeds, calls `syncManager.pullAllFromSupabase(restaurantId)` synchronously (before marking `isAuthenticated = true`). This ensures data is available as soon as user lands on POS/dashboard.
- **Files modified:** `SyncManager.kt`, `AuthViewModel.kt`
- **Key Design:** Pull is table-parallel (all tables fetch simultaneously), per-record sequential (loop insert). Room's `OnConflictStrategy.REPLACE` handles merge — server wins by replacing local rows.
- **LESSON:** SyncManager should own both push AND pull directions. Push = outbound queue processing. Pull = inbound reconciliation from Supabase. Both must be implemented for true cross-device sync.
- **LESSON:** Periodic sync (WorkManager every 15 min) now also pulls — passive reconciliation for changes made on other devices while app is in background.

## Scrollable Screen Pattern (2026-02-18)

### Settings sub-screens made scrollable
- **Screens fixed:** CategoryListScreen (Daftar Kategori), CategoryFormScreen (Tambah/Edit Kategori), InitialBalanceSettingScreen (Saldo Awal Kas)
- **Pattern for static form screens** (CategoryFormScreen, InitialBalanceSettingScreen):
  - Change `fillMaxSize()` → `fillMaxWidth()` on the content Column
  - Add `.verticalScroll(rememberScrollState())` to the content Column
  - Add imports: `rememberScrollState`, `verticalScroll`
- **Pattern for dynamic list screens** (CategoryListScreen):
  - Screen already uses `LazyColumn` (inherently scrollable)
  - Add `Modifier.fillMaxSize()` to the `LazyColumn` so it properly fills and scrolls within the parent Column
- **Rule:** Static form screens → `Column + verticalScroll`. Dynamic list screens → `LazyColumn + fillMaxSize()`.
- **Files changed:** `feature/product/CategoryFormScreen.kt`, `feature/product/CategoryListScreen.kt`, `feature/settings/InitialBalanceSettingScreen.kt`

## Pembelian: Tanggal Field on GoodsReceiving (2026-02-18)

### Change: User-selectable receive date for Penerimaan Barang
- **Problem:** `date` field on `GoodsReceivingEntity`/`GoodsReceivingDto`/`GoodsReceiving` domain model existed but was hardcoded to `System.currentTimeMillis()` in `PurchasingRepository.createReceiving()`. No UI exposed it.
- **Fix Applied:**
  1. **`ReceivingFormState`:** Added `date: Long = System.currentTimeMillis()` field.
  2. **`PurchasingViewModel`:** Added `onReceivingDateChange(value: Long)`. Updated `loadReceiving()` to populate `date = receiving.date`. Updated `saveReceiving()` to pass `form.date` to repo calls.
  3. **`PurchasingRepository`:** Added `date: Long` param to `createReceiving()` and `updateReceiving()`. Both now store the user-supplied date (no longer forces `now`).
  4. **`GoodsReceivingFormScreen`:** Added `rememberDatePickerState(initialSelectedDateMillis = form.date)`, `DatePickerDialog` + `DatePicker` (M3), and an `OutlinedTextField` (readOnly) showing `DateTimeUtil.formatDate(form.date)` with calendar icon. Placed between vendor dropdown and notes field.
- **No entity/DTO/DB migration needed** — `date: Long` already existed in schema.
- **Key pattern:** M3 `DatePickerDialog` returns UTC midnight millis. `DateTimeUtil.formatDate()` in WIB (UTC+7) correctly shows the chosen calendar date since midnight UTC = 07:00 WIB = same calendar day.

## Pengaturan: Manajemen Vendor & Manajemen Barang (2026-02-18)

### Added to Settings
- **Manajemen Vendor:** Card in SettingsScreen that navigates to `Screen.VendorList`. Reuses existing VendorListScreen + VendorFormScreen (name, phone, address).
- **Manajemen Barang:** Card in SettingsScreen that navigates to `Screen.ProductList`. Reuses existing ProductListScreen + ProductFormScreen (products grouped by type: Menu Item / Bahan Baku).
- No new screens or entities created — pure navigation wiring.

### Files changed:
- `feature/purchasing/VendorListScreen.kt`: Added `onNavigateBack: () -> Unit` param + TopAppBar with back button. Changed padding to `horizontal = 24.dp`.
- `feature/product/ProductListScreen.kt`: Added `onNavigateBack: () -> Unit` param + TopAppBar with back button. Removed inline title Text (moved to TopAppBar). Changed padding to `horizontal = 24.dp`.
- `feature/settings/SettingsScreen.kt`: Added `onNavigateToVendors` and `onNavigateToProducts` callbacks. Added two new `Card` entries between Kategori and QRIS.
- `core/navigation/AyaKasirNavHost.kt`: Passed `onNavigateBack` to `VendorList` and `ProductList` composables. Added `onNavigateToVendors` and `onNavigateToProducts` to the `Settings` composable.
- `ai-memory/FILE_MAP.md`: Updated settings entry to list all 7 Settings entry points.

### Key pattern:
- Screen accessed from both NavRail (top-level tab) and Settings sub-navigation: same `Screen.ProductList` route, `onNavigateBack = { navController.popBackStack() }` works for both cases (from NavRail it pops to previous tab; from Settings it pops back to Settings).
- When reusing an existing screen from a new entry point, just add `onNavigateBack` to the composable and wire in NavHost — no new Screen needed.

## Pembelian Sync Fix: goods_receiving_items & inventory (2026-02-18)

### Bug 1: goods_receiving_items never synced to Supabase
- **Symptom:** Supabase `goods_receiving` had 1 row, `goods_receiving_items` had 0 rows. API logs confirmed: POST goods_receiving → 201, DELETE goods_receiving_items → 204, but NO POST for goods_receiving_items.
- **Root Cause:** `SyncManager.pushUpsert("goods_receiving")` used `goodsReceivingDao.getWithItemsById(recordId)` which returns `GoodsReceivingWithVendorAndItems` — a Room relation class with TWO `@Relation` annotations (vendor + items). The `@Relation` for items returned an empty list despite items existing in Room.
- **Why @Relation failed:** Likely a Room timing/caching issue when querying a relation immediately after inserting items in the same coroutine scope. The main `SELECT` ran but the relation sub-query for items returned empty.
- **Fix:** Added direct queries to `GoodsReceivingDao`:
  - `getById(id)` → returns `GoodsReceivingEntity?` (no relation)
  - `getItemsByReceivingId(receivingId)` → returns `List<GoodsReceivingItemEntity>` (plain query)
- Changed `SyncManager.pushUpsert("goods_receiving")` to use `getById()` + `getItemsByReceivingId()` instead of `getWithItemsById()`.
- **LESSON:** Room `@Relation` can silently return empty lists when multiple relations are used or when querying immediately after insert. For sync/push operations, prefer direct queries over `@Relation` for reliability.

### Bug 2: Inventory never synced on successful push
- **Symptom:** Supabase `inventory` had 0 rows. Inventory was updated in Room but never pushed.
- **Root Cause:** In `PurchasingRepository.createReceiving()`, inventory sync entries were only enqueued in the `catch` block (when goods_receiving push fails). On success, inventory was never pushed.
- **Fix:** After successful `pushToSupabase("goods_receiving", ...)`, iterate `inventorySyncEntries` and push each via `syncManager.pushToSupabase()`. If individual inventory push fails, enqueue that entry.
- Applied same pattern to `updateReceiving()` and `deleteReceiving()`.
- **LESSON:** When a write operation produces side-effects (like inventory changes from goods receiving), those side-effects must be synced on BOTH success and failure paths. Don't assume the main push handler covers them.

### Bug 3: Cross-device sync not working for Pembelian/Stok/Menu
- **Root Cause:** Not a pull issue — `SyncManager.pullAllFromSupabase()` already handles all tables including goods_receiving_items and inventory. The problem was that data was never IN Supabase (bugs 1 & 2 above), so there was nothing to pull.
- **Fix:** Bugs 1 & 2 fixes ensure data reaches Supabase. Cross-device sync will work automatically via existing pull mechanism.

### Files changed:
- `GoodsReceivingDao.kt`: Added `getById()` and `getItemsByReceivingId()` direct queries
- `SyncManager.kt`: Changed `goods_receiving` push to use direct queries instead of @Relation
- `PurchasingRepository.kt`: Added inventory push on success path for create/update/delete; changed `deleteReceiving` to use `getItemsByReceivingId` instead of `getWithItemsById`

## 2026-02-18 — Goods Receiving Items Still Not Syncing (Session 3 Fix)

### Problem
Even after fixing SyncManager to use direct queries (`getItemsByReceivingId`) instead of @Relation,
goods_receiving_items STILL not appearing in Supabase. API logs showed: POST goods_receiving → 201,
DELETE items → 204, **no POST for items** on BOTH new and retried (SyncWorker) receivings.
This proved items were never in Room — both immediate push and delayed queue retry both returned empty.

### Root Cause (final)
Suspected **Room write-after-read race condition** or silent `insertItems` failure.
Regardless of exact cause, the pattern is: SyncManager reads items from Room (via `getItemsByReceivingId`)
immediately after `insertItems` completes, and still gets empty list. Items may not be committed
to Room's WAL file fast enough for the next read on a different thread/connection.

### Definitive Fix: Direct Entity Push (bypass Room read entirely)
- Added `SyncManager.pushGoodsReceivingWithItems(entity, items)` — takes in-memory entities
  from caller, pushes them directly to Supabase WITHOUT any Room read round-trip
- `PurchasingRepository.createReceiving()`: now calls `pushGoodsReceivingWithItems(entity, itemEntities)`
  using the item list that's already in memory, eliminating the read-after-write problem
- `PurchasingRepository.updateReceiving()`:
  - Fixed @Relation bug for old items: changed from `getWithItemsById().items` to `getItemsByReceivingId()`
  - Uses `pushGoodsReceivingWithItems(entity, itemEntities)` for new items push

### Rule: Always Pass In-Memory Data for Immediate Push
When a repository creates entities in memory, push them to Supabase directly from that in-memory list.
Do NOT insert to Room and then immediately read back via DAO — timing can cause empty reads.
Pattern:
```kotlin
val entity = SomeEntity(...)
dao.insert(entity)
val items = inputList.map { SomeItemEntity(...) }
dao.insertItems(items)
try {
    syncManager.pushWithItems(entity, items)  // pass in-memory, not DAO read
} catch (e: Exception) { /* enqueue fallback */ }
```
### Files changed:
- `SyncManager.kt`: Added `pushGoodsReceivingWithItems(entity, items)` method; added imports for entity types
- `PurchasingRepository.kt`: `createReceiving` uses `pushGoodsReceivingWithItems`; `updateReceiving` fixes @Relation bug + uses `pushGoodsReceivingWithItems`

## 2026-02-18 — Bulk Upsert JsonArray Bug & Phased Pull (Session 4 Fix)

### Problem
goods_receiving_items and inventory STILL 0 rows in Supabase (3 goods_receiving headers existed).
API logs showed consistent pattern for every attempt (direct + SyncQueue retries):
1. POST goods_receiving → 201/200 ✓
2. DELETE goods_receiving_items → 204 ✓
3. **No POST for goods_receiving_items** ever reached Supabase

### Root Cause
`json.encodeToJsonElement(listOfDtos)` produces a `JsonArray`. The supabase-kt `upsert(jsonArray)` call fails client-side before the HTTP request is sent. Single-object `upsert(jsonObject)` works (all other tables sync fine). The error is swallowed by the catch block in `PurchasingRepository`, and inventory push was gated behind goods_receiving success — so both failed.

### Fix Applied
1. **SyncManager.pushGoodsReceivingWithItems**: Changed from batch `upsert(json.encodeToJsonElement(itemDtos))` to individual `forEach { upsert(json.encodeToJsonElement(singleDto)) }`
2. **SyncManager.pushUpsert "goods_receiving"**: Same fix for SyncQueue retry path
3. **SyncManager.pushUpsert "transactions"**: Same fix for transaction_items (same latent bug)
4. **PurchasingRepository create/update/delete**: Separated inventory push from goods_receiving push with independent try-catch blocks — inventory is no longer gated by goods_receiving success
5. **SyncManager.pullAllFromSupabase**: Restructured from all-parallel to 3-phase pull to respect Room FK constraints:
   - Phase 1: categories, vendors, users, cash_withdrawals (independent)
   - Phase 2: products, goods_receiving, transactions, inventory (depend on phase 1)
   - Phase 3: variants, product_components, goods_receiving_items, transaction_items (depend on phase 2)
6. **GoodsReceivingDao + TransactionDao**: Added `insertItem()` single-record method for phased pull

### LESSONS
- **NEVER use `json.encodeToJsonElement(list)` for Supabase bulk upsert** — supabase-kt cannot reliably handle JsonArray. Always push child items one by one.
- **Push side-effects independently** — inventory changes from goods_receiving must use their own try-catch, not be nested inside the goods_receiving push try block.
- **Pull must respect Room FK order** — parallel pull of parent+child tables causes FK violations when child inserts before parent. Use phased pull: parents first, children after.

### Files changed
- `SyncManager.kt`: Individual item push in `pushGoodsReceivingWithItems`, `pushUpsert("goods_receiving")`, `pushUpsert("transactions")`; 3-phase `pullAllFromSupabase`
- `PurchasingRepository.kt`: Independent try-catch for inventory push in `createReceiving`, `updateReceiving`, `deleteReceiving`
- `GoodsReceivingDao.kt`: Added `insertItem(GoodsReceivingItemEntity)`
- `TransactionDao.kt`: Added `insertItem(TransactionItemEntity)`

## 2026-02-18 — ProductListScreen: Hide Raw Materials

### Change: Raw material products no longer shown in ProductListScreen ("Daftar Produk")
- **Problem:** ProductListScreen showed both "Menu Item" and "Bahan Baku" sections. Raw materials are not sellable menu items; they should not appear in the product/menu management list.
- **Fix:** In `ProductListScreen.kt`:
  - Removed `rawMaterials` remember block
  - Removed the "Bahan Baku" section rendering (`if (rawMaterials.isNotEmpty())` block)
  - Changed empty state check from `if (products.isEmpty())` → `if (menuItems.isEmpty())` (so empty state shows correctly when only raw materials exist)
  - The `menuItems` filter (`productType == ProductType.MENU_ITEM`) was already present — just cleaned up the unused rawMaterials half
- **Files changed:** `feature/product/ProductListScreen.kt`
- **No ViewModel/Repo changes:** `getAllActiveProducts()` still returns all types (needed for component picker in ProductFormScreen). Filtering is done in the Screen.
- **POS screen:** Already filters correctly via DAO (`product_type = 'MENU_ITEM'` AND `c.category_type = 'MENU'`). No change needed there.

### LESSON
- When a screen already has an in-memory filter (`menuItems = products.filter { ... }`), removing a section is a pure UI change — no DAO/Repo changes needed.
- Keep `getAllActiveProducts()` in repository intact for reuse by other consumers (component picker, future screens).

## 2026-02-18 — Realtime Sync + Pull-to-Refresh

### Goal
Make all data screens (POS, Dashboard, Inventory, Products, Categories, Vendors, Goods Receiving) update in near-realtime via Supabase Realtime, with pull-to-refresh as fallback.

### Architecture
- **Realtime:** `RealtimeManager` (@Singleton) subscribes to Supabase Postgres Changes for all 11 tenant tables → decodes DTO → maps to Entity → upserts/deletes in Room → Room Flow auto-emits → UI updates.
- **Pull-to-refresh:** Each data screen wraps content in Material3 `PullToRefreshBox`. Swipe triggers `SyncManager.pullAllFromSupabase()` via ViewModel.
- **Room stays single data source for ViewModels** — Realtime and pull-to-refresh both write to Room, ViewModels read from Room Flows.

### Dependencies Changed
- Added `supabase-realtime` (`realtime-kt` via BOM) to `libs.versions.toml` + `app/build.gradle.kts`
- Replaced `ktor-client-android` with `ktor-client-okhttp` — required for WebSocket support (Realtime uses WebSockets; ktor-client-android doesn't support them)

### Files Created
- `core/sync/RealtimeManager.kt` — Supabase Realtime subscriber, one channel per restaurant, listeners for all 11 tenant tables

### Files Modified
- `gradle/libs.versions.toml` — added realtime + okhttp deps
- `app/build.gradle.kts` — wired new deps
- `core/di/NetworkModule.kt` — `install(Realtime)` in SupabaseClient builder
- `core/session/SessionManager.kt` — inject RealtimeManager, connect on login, disconnect on logout
- 5 ViewModels (Pos, Dashboard, Inventory, Purchasing, ProductManagement) — added `isRefreshing` StateFlow + `refresh()` method
- 7 Screens (PosScreen, DashboardScreen, InventoryScreen, VendorListScreen, GoodsReceivingListScreen, ProductListScreen, CategoryListScreen) — wrapped content in `PullToRefreshBox`

### LESSONS
- **ktor-client-android does NOT support WebSockets** — must use `ktor-client-okhttp` for Supabase Realtime.
- **supabase-kt v3 Realtime API:** Use `ch.postgresChangeFlow<PostgresAction>(schema) { table = "..."; filter(column = "col", operator = FilterOperator.EQ, value = val) }`. The `filter` property has a **private setter** — use the `filter()` method instead. Decode with `action.decodeRecord<T>()` / `action.decodeOldRecord<T>()` — these are **top-level extension functions** requiring explicit imports: `import io.github.jan.supabase.realtime.decodeRecord` and `import io.github.jan.supabase.realtime.decodeOldRecord`. Also import `import io.github.jan.supabase.postgrest.query.filter.FilterOperator`.
- **Set up all listeners BEFORE calling `channel.subscribe()`** — supabase-kt requires flows to be collected before subscription starts.
- **RealtimeManager must NOT inject SessionManager** (avoid circular dependency) — takes `restaurantId` as parameter from SessionManager instead.
- **Pull-to-refresh pattern:** ViewModel exposes `isRefreshing: StateFlow<Boolean>` + `refresh()`. Screen uses `PullToRefreshBox(isRefreshing, onRefresh)` wrapping existing content. Import `ExperimentalMaterial3Api`.
- **DAOs without delete methods** (TransactionDao, CashWithdrawalDao): Realtime listener only handles INSERT/UPDATE, skips DELETE events.
