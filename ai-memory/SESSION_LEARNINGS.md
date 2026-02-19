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

## 2026-02-18 — Product Components Not Saving (Missing restaurantId)

### Problem
When creating a menu item with ingredients, the product was saved to `products` table but ingredients were NOT saved to `product_components` in Supabase. They existed locally in Room but never synced.

### Root Cause
`ProductComponentRepository` did not inject `SessionManager` and never set `restaurantId` on `ProductComponentEntity`. The entity defaulted to `restaurantId = ""`. When `pushToSupabase` tried to upsert with `restaurant_id = ""` for a `UUID` column in Supabase, it threw a Postgres type-cast exception. The error was caught silently, enqueued to SyncQueue, and the queue retry also failed for the same reason.

### Fix
- Injected `SessionManager` into `ProductComponentRepository`
- Added `private val restaurantId: String get() = sessionManager.currentRestaurantId ?: ""`
- Set `restaurantId = restaurantId` when constructing `ProductComponentEntity` in `addComponent()`

### Files changed
- `ProductComponentRepository.kt`: Added `SessionManager` injection + `restaurantId` property + set on entity creation

### LESSON
**Every repository that creates tenant-scoped entities MUST inject `SessionManager` and set `restaurantId`.** This was already documented in CODE_RULES.md but `ProductComponentRepository` was missed during the multi-tenancy rollout. When Supabase push silently fails, check if `restaurantId` is empty — a UUID column receiving `""` causes an immediate Postgres type-cast error.

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

## 2026-02-18 — Menu Screen Improvements

### 1. Keyboard Not Covering Fields (ProductFormScreen)
- **Problem:** When selecting a text field in the product form, the soft keyboard covered input fields.
- **Fix:** Added `imePadding()` modifier to the root `Column` in `ProductFormScreen.kt`.
- **Files changed:** `feature/product/ProductFormScreen.kt`

### 2. Removed Jenis Produk Radio Buttons
- **Problem:** "Jenis Produk" radio buttons (Menu Item / Bahan Baku) were unnecessary since the "Tambah Produk" screen defaults to MENU_ITEM.
- **Fix:** Removed the radio buttons section (lines 130-158) from `ProductFormScreen.kt`. Default `ProductType.MENU_ITEM` in `ProductFormState` remains unchanged.
- **Files changed:** `feature/product/ProductFormScreen.kt`
- **Note:** Removed unused imports: `RadioButton`, `Alignment`

### 3. Unit Conversion for Inventory (kg↔g, L↔mL)
- **Problem:** When purchasing rice in kg but recipe uses grams, stock decrement didn't convert units. 1 kg rice bought → inventory = 1. Recipe needs 200g → subtracted 200 from 1 (wrong).
- **Architecture Decision:** Normalize inventory to base units at storage time. Base units: g (mass), mL (volume), pcs (count).
  - Purchasing 1 kg → inventory stores 1000 g
  - Recipe needs 200 g → decrement 200 from inventory (both in g)
  - All integer math, no fractional loss.
- **New Files:**
  - `core/util/UnitConverter.kt` — normalizeToBase(), convert(), areCompatible(), formatForDisplay()
- **Schema Changes:**
  - `InventoryEntity`: Added `unit: String = "pcs"` column
  - `InventoryDto`: Added `unit: String = "pcs"` field
  - `DtoEntityMappers.kt`: Added `unit` to both Entity↔DTO mappers
  - Room migration 12→13: `ALTER TABLE inventory ADD COLUMN unit TEXT NOT NULL DEFAULT 'pcs'`
  - Supabase migration: Same `ALTER TABLE` applied via MCP
  - DB version: 12 → 13
- **Repository Changes:**
  - `PurchasingRepository`: On goods receiving, normalizes (qty, unit) to base unit via `UnitConverter.normalizeToBase()` before storing in inventory. New inventory records get base unit. Existing inventory converts incoming unit to inventory's stored unit.
  - `TransactionRepository`: When decrementing component stock, converts component's (requiredQty, unit) to inventory's base unit via `UnitConverter.convert()` before decrement.
- **Domain/UI Changes:**
  - `InventoryItem`: Added `unit` field + `displayQty`/`displayMinQty` computed properties using `UnitConverter.formatForDisplay()`
  - `InventoryScreen`: Shows formatted qty with unit (e.g., "1.5 kg" instead of "1500")
  - `InventoryRepository`: Passes `unit` from entity to domain model
- **Files changed:** `InventoryEntity.kt`, `InventoryDto.kt`, `DtoEntityMappers.kt`, `AyaKasirDatabase.kt`, `DatabaseModule.kt`, `PurchasingRepository.kt`, `TransactionRepository.kt`, `InventoryItem.kt`, `InventoryRepository.kt`, `InventoryScreen.kt`, `supabase/schema.sql`
- **LESSON:** When different parts of the system use different units for the same dimension, normalize to the smallest base unit at storage time to keep all math as integers.

## 2026-02-18 — Inventory Unit Mismatch Fix

### Problem
Unit shown in Stok (Inventory screen) didn't match unit entered in Pembelian (Goods Receiving). E.g., receiving in "kg" but Stok showed "pcs".

### Root Cause
In `PurchasingRepository` (createReceiving + updateReceiving), when existing inventory has a `unit` incompatible with the incoming item's unit (e.g., existing="pcs", incoming="kg"):
- `areCompatible("kg", "pcs")` = false → fell through to `normalizedQty` path
- `inventoryDao.incrementStock()` only updates `current_qty` (SQL query), NOT the `unit` column
- Result: `unit` column stays "pcs" but qty is now in grams → display shows wrong unit

### Fix
Changed the incompatible-unit path from `incrementStock()` to `insert(existing.copy(...))` with REPLACE strategy, so both `currentQty` and `unit` get updated atomically:
```kotlin
if (UnitConverter.areCompatible(item.unit, existing.unit)) {
    inventoryDao.incrementStock(...)
} else {
    // Update unit to base unit so unit column matches
    inventoryDao.insert(existing.copy(
        currentQty = existing.currentQty + normalizedQty,
        unit = baseUnit,
        syncStatus = SyncStatus.PENDING.name,
        updatedAt = now
    ))
}
```

### Secondary Fix: Adjust Dialog Shows Unit
Added unit suffix to adjust dialog labels in InventoryScreen:
- "Stok" → "Stok (${item.unit})" (e.g., "Stok (g)")
- "Minimum" → "Min (${item.unit})" (e.g., "Min (g)")
Pre-fill value stays as raw base-unit integer — user now knows what unit they're entering.

### Files changed
- `PurchasingRepository.kt`: Fixed incompatible-unit branch in `createReceiving()` and `updateReceiving()` (new items section)
- `InventoryScreen.kt`: Added unit suffix to adjust dialog labels

### LESSON
`DAO @Query` methods that only update specific columns (e.g., `incrementStock` updates `current_qty`) do NOT update other columns. When multiple columns must change together (qty + unit), use `dao.insert(entity.copy(...))` with `OnConflictStrategy.REPLACE` — this is an atomic full-row replacement.

## 2026-02-18 — Variant & ProductComponent Sync Coverage

### Problem
5 tables flagged as potentially missing sync: `product_components`, `cash_withdrawals`, `transaction_items`, `transactions`, `variants`. Investigation showed:
- **transactions** — already fully synced (create + void)
- **transaction_items** — already synced implicitly via parent transaction INSERT in SyncManager
- **cash_withdrawals** — already fully synced (create)
- **variants** — CRITICAL GAP: created/updated/deleted locally but never synced to Supabase
- **product_components** — MINOR GAP: `deleteByProductId()` cascade was silent (no sync)

### Fixes Applied

#### 1. ProductRepository — Variant Sync
- **createProduct():** After syncing the product, now iterates variants and pushes each via `syncManager.pushToSupabase("variants", "INSERT", v.id)` with SyncQueue fallback.
- **updateProduct():** Collects old variant IDs via `variantDao.getByProductIdDirect()` BEFORE local delete. After syncing the product update, pushes DELETE for each old variant and INSERT for each new variant.
- **deleteProduct():** Collects variant IDs BEFORE local product delete (cascade). After syncing product delete, pushes DELETE for each variant.

#### 2. ProductComponentRepository — Cascade Delete Sync
- **deleteByProductId():** Now collects component IDs via `getByProductIdDirect()` BEFORE local delete. After deleting locally, pushes DELETE for each component to Supabase with SyncQueue fallback.

### Files changed
- `ProductRepository.kt`: Added variant sync in create/update/delete
- `ProductComponentRepository.kt`: Added cascade delete sync in `deleteByProductId()`

### LESSON
When a parent entity operation cascades to child entities (delete product → delete variants, delete product → delete components), always:
1. Collect child IDs BEFORE the local cascade delete
2. Sync parent first
3. Sync each child deletion independently (own try-catch + SyncQueue fallback)

## 2026-02-18 — Login "Akun tidak ditemukan" Fix

### Problem
User tried to login with an existing email (confirmed in Supabase dashboard, is_active=TRUE, password_hash present), but got "Akun tidak ditemukan. Silakan daftar terlebih dahulu."

### Root Cause
Two compounding issues:
1. **Case-sensitive email matching:** Supabase PostgREST `eq("email", email)` is case-sensitive. If user typed "Contact.KedaiRakyat@gmail.com" but DB stores "contact.kedairakyat@gmail.com", the query returns empty.
2. **Silent exception swallowing:** `catch (_: Exception)` in `authenticateByEmail()` hid ALL errors (network, deserialization, configuration) with no logging. If Supabase fetch failed for any reason, it silently fell back to local cache which was also empty on a fresh install → `NotFound`.
3. **Schema drift:** `supabase/schema.sql` was missing `password_hash` and `password_salt` columns (though live DB had them). This file is reference documentation — keeping it out of sync makes debugging harder.

### Fixes Applied
1. **UserRepository.authenticateByEmail():** Normalize email with `.trim().lowercase()` before querying. Changed Supabase filter from `eq("email", email)` to `ilike("email", normalizedEmail)` for case-insensitive match. Added `Log.w()` on exception instead of silent swallow.
2. **UserRepository.getUserByEmailRemote():** Same `ilike` + trim + logging fixes.
3. **UserRepository.registerOwner():** Store email as `email.trim().lowercase()` to ensure consistent casing in DB.
4. **UserDao.getByEmail():** Changed Room query from `WHERE email = :email` to `WHERE LOWER(email) = LOWER(:email)` for case-insensitive local lookup.
5. **supabase/schema.sql:** Added `password_hash TEXT` and `password_salt TEXT` columns to `users` CREATE TABLE definition.

### Files changed
- `UserRepository.kt`: Email normalization + ilike filter + logging
- `UserDao.kt`: Case-insensitive email query
- `supabase/schema.sql`: Added missing password columns

### LESSONS
- **Email matching must ALWAYS be case-insensitive.** Use `ilike` for Supabase PostgREST and `LOWER()` for Room SQL.
- **Never use `catch (_: Exception)` without logging** in network calls — it hides configuration errors, deserialization failures, and auth issues. At minimum use `Log.w()`.
- **Normalize email on storage** (`.trim().lowercase()`) to prevent future case mismatches.
- **Keep schema.sql in sync with live DB** — it's the reference doc for new deployments and debugging.

## 2026-02-18 — Keyboard Covers Fields Fix (Auth Screens)

### Problem
When tapping a text field in `EmailLoginScreen` or `RegistrationScreen`, the soft keyboard popped up and covered the input fields. Users couldn't see what they were typing.

### Root Cause
`enableEdgeToEdge()` is called in `MainActivity`, which causes the window to draw behind system bars (including the IME). When edge-to-edge is active, the manifest's `android:windowSoftInputMode="adjustResize"` is **ignored** on API 30+. The window no longer resizes when the keyboard appears. The scrollable Column had no awareness of the keyboard inset.

### Fix Applied
Added `imePadding()` modifier **before** `verticalScroll()` on the scrollable root `Column` in both screens:
```kotlin
modifier = Modifier
    .fillMaxSize()
    .imePadding()                          // shrinks viewport by keyboard height
    .verticalScroll(rememberScrollState()) // allows scrolling within the shrunk space
    .padding(horizontal = ..., vertical = ...)
```

### Why Modifier Order Matters
- `imePadding()` **before** `verticalScroll()` → reduces the available height by keyboard height → scroll area shrinks → content scrolls to show focused field.
- `imePadding()` **after** `verticalScroll()` → adds padding to the bottom of an already-infinite-height scroll container → does nothing useful.

### Files changed
- `feature/auth/EmailLoginScreen.kt`: Added `import androidx.compose.foundation.layout.imePadding` + `.imePadding()` before `.verticalScroll()`
- `feature/auth/RegistrationScreen.kt`: Same changes

### LESSON
**Keyboard avoidance in Compose with edge-to-edge:**
- `enableEdgeToEdge()` in Activity invalidates the manifest `windowSoftInputMode="adjustResize"`.
- Use `imePadding()` modifier placed **before** `verticalScroll()` in the modifier chain.
- This pattern also works for `LazyColumn` and other scrollable containers.
- Already applied in `ProductFormScreen.kt` (see Menu Screen Improvements above) — apply consistently on all scrollable form screens.

## 2026-02-18 — Supabase Connection Fix (Placeholder Credentials)

### Problem
1. Registration succeeded locally but never synced to Supabase (new records not appearing in dashboard).
2. Login with existing user ("contact.kedairakyat@gmail.com") failed with "Akun tidak ditemukan" despite user existing in Supabase with `is_active=TRUE` and valid password hash.

### Root Cause
`app/build.gradle.kts` had `buildConfigField("String", "SUPABASE_URL", "\"PLACE_HERE\"")` and same for `SUPABASE_ANON_KEY`. The app was built with literal `"PLACE_HERE"` as Supabase URL — every Supabase HTTP request failed immediately. All failures were caught silently by `catch (_: Exception)` blocks throughout the codebase.

This meant:
- **Registration:** `pushToSupabase()` failed → enqueued to SyncQueue → SyncQueue retry also failed → data never reached Supabase.
- **Login:** `authenticateByEmail()` Supabase fetch failed → fell back to local Room cache → fresh install had empty cache → `NotFound`.

### Fix Applied
1. **`app/build.gradle.kts`:** Changed from hardcoded placeholders to `localProperties.getProperty("SUPABASE_URL", "")` pattern. Reads credentials from `local.properties` at build time.
2. **`local.properties`:** Added actual Supabase URL and anon key (file is gitignored — credentials never committed).
3. **`RestaurantRepository.kt`:** Added `Log.w()` to catch block in `create()` for visibility.
4. **`UserRepository.kt`:** Added `Log.w()` to catch block in `registerOwner()`. Also fixed SyncQueue payload to use `normalizedEmail` instead of raw `email`.

### Files changed
- `app/build.gradle.kts`: `import java.util.Properties`, load `localProperties`, read `SUPABASE_URL`/`SUPABASE_ANON_KEY` from it
- `local.properties`: Added `SUPABASE_URL` and `SUPABASE_ANON_KEY` entries
- `RestaurantRepository.kt`: Added `Log.w` + `import android.util.Log`
- `UserRepository.kt`: Added `Log.w` to `registerOwner` catch, fixed payload email

### LESSONS
- **NEVER use placeholder credentials in committed `build.gradle.kts`** — use `local.properties` (gitignored) + `Properties().load()` pattern.
- **Placeholder credentials cause total silent failure** — every Supabase operation fails but is caught by try-catch. The app appears to "work" locally but nothing syncs.
- **To set up a new dev environment:** Add `SUPABASE_URL=https://xxx.supabase.co` and `SUPABASE_ANON_KEY=eyJ...` to `local.properties`.
- **Always `Log.w()` in sync catch blocks** — silent failures are the #1 cause of debugging difficulty in this project.

## 2026-02-18 — Product Components Accumulation & Variants Not Saving

### Problem 1: product_components accumulated on every save
When editing a menu item multiple times, each save added new component records to Supabase without cleaning up old ones. The `deleteByProductId` method pushed DELETE by individual component IDs read from Room. If those IDs didn't exist in Supabase (e.g., from the previous restaurantId="" bug), the DELETE was a no-op, while the subsequent INSERT succeeded — causing accumulation.

### Problem 2: Variants intermittently not saved to Supabase
Variants added in the product form were saved to Room but not reliably synced to Supabase. Had to try multiple times to get them stored.

### Root Cause (both issues): Read-after-write timing + stale ID matching
1. **Components:** `deleteByProductId` deleted from Room by `productId`, then pushed individual DELETEs to Supabase by `component.id`. If Supabase had different IDs (or records that were never synced due to earlier bugs), the bulk cleanup never happened.
2. **Variants:** `ProductRepository.createProduct/updateProduct` called `syncManager.pushToSupabase("variants", "INSERT", v.id)` which calls `pushUpsert` → `variantDao.getById(recordId) ?: return`. After `variantDao.insertAll(variants)`, the Room read-back could return null due to write-commit timing, causing the push to silently skip.

### Fixes Applied

#### SyncManager — New direct-push methods
- `deleteVariantsByProductId(productId)` — bulk DELETE from Supabase `WHERE product_id = :productId`
- `pushVariantsDirect(variants: List<VariantEntity>)` — pushes from in-memory list, no Room read
- `deleteComponentsByProductId(productId)` — bulk DELETE from Supabase `WHERE parent_product_id = :productId`
- `pushComponentDirect(entity: ProductComponentEntity)` — pushes from in-memory entity, no Room read

#### ProductRepository
- `createProduct()`: Changed variant sync from `pushToSupabase("variants", "INSERT", v.id)` (Room read-back) to `pushVariantsDirect(variants)` (in-memory)
- `updateProduct()`: Changed old variant cleanup from individual ID DELETEs to `deleteVariantsByProductId(productId)` (bulk). Changed new variant push to `pushVariantsDirect(variants)` (in-memory)

#### ProductComponentRepository
- `addComponent()`: Changed from `pushToSupabase("product_components", "INSERT", entity.id)` to `pushComponentDirect(entity)` (in-memory)
- `deleteByProductId()`: Changed from individual ID DELETEs to `deleteComponentsByProductId(productId)` (bulk by parent_product_id)
- Added `Log.w` to catch blocks, added `companion object { TAG }`

### Files changed
- `SyncManager.kt`: Added `VariantEntity` + `ProductComponentEntity` imports, added 4 new direct-push/delete methods
- `ProductRepository.kt`: Changed variant sync in `createProduct` + `updateProduct` to use direct methods
- `ProductComponentRepository.kt`: Changed component push + delete to use direct methods, added logging

### LESSONS
- **For child entity sync, always use bulk DELETE by parent FK** (e.g., `WHERE parent_product_id = :id`) instead of deleting by individual child IDs. This ensures cleanup even when Room and Supabase are out of sync.
- **For batch-inserted child entities, always push from in-memory list** — never read back from Room after `insertAll()`. This is the same read-after-write pattern learned from goods_receiving_items. Applies to: variants, product_components, goods_receiving_items, transaction_items.
- **Pattern for replace-children operations** (delete old + insert new): (1) delete locally, (2) bulk DELETE from Supabase by parent FK, (3) insert locally, (4) push new entities from memory.

## 2026-02-18 — General Ledger for Cash Management

### Goal
Replace device-local DataStore-based cash balance with a synced `general_ledger` table that tracks all cash events and syncs to Supabase.

### Architecture Decision
- **Ledger REPLACES old calculation:** Old: `CashBalanceDataStore.initialBalance + SUM(cash sales) - SUM(withdrawals)`. New: `SUM(general_ledger.amount)` where signed amounts encode direction.
- **LedgerType enum:** `INITIAL_BALANCE`, `SALE`, `WITHDRAWAL`, `ADJUSTMENT`
- **Signed amounts:** positive = inflow, negative = outflow. Balance = SUM(amount).
- **12th tenant-scoped table** with `restaurant_id`, follows standard network-first sync pattern.

### What was created
- `LedgerType.kt` — enum for ledger entry types
- `GeneralLedgerEntity.kt` — Room entity with indices on restaurant_id, type, date, reference_id, sync_status
- `GeneralLedgerDao.kt` — DAO with getBalance (SUM), getTotalByType, getByDateRange, getLatestInitialBalance
- `GeneralLedgerDto.kt` — Supabase DTO with kotlinx.serialization
- `GeneralLedgerRepository.kt` — network-first repo with `recordEntry()` for creating ledger entries

### What was modified
- `AyaKasirDatabase.kt` — version 13→14, MIGRATION_13_14, added entity + DAO
- `DatabaseModule.kt` — registered migration + DAO provider
- `SyncManager.kt` — general_ledger in Phase 1 pull + pushUpsert handler
- `RealtimeManager.kt` — general_ledger listener for INSERT/UPDATE/DELETE
- `CashBalanceRepository.kt` — complete rewrite: now uses GeneralLedgerRepository flows only (removed DataStore + TransactionRepo + CashWithdrawalRepo dependencies)
- `CashBalance.kt` — added `totalAdjustments` field
- `TransactionRepository.kt` — creates SALE ledger entry on CASH transactions
- `CashWithdrawalRepository.kt` — creates WITHDRAWAL ledger entry (negative amount)
- `InitialBalanceViewModel.kt` — uses GeneralLedgerRepository instead of CashBalanceDataStore
- `DtoEntityMappers.kt` — added GeneralLedger Entity↔DTO mappers
- `supabase/schema.sql` — added general_ledger table + RLS + policy
- Supabase live migration applied + added to realtime publication

### LESSONS
- **Ledger pattern is simpler than multi-source combine:** Instead of `combine(dataStore, transactionFlow, withdrawalFlow)`, a single `SUM(amount)` query replaces 3 data sources.
- **Signed amounts eliminate separate tracking:** No need for separate "total sales" and "total withdrawals" columns — positive/negative amounts in one table encode both direction and magnitude.
- **general_ledger is Phase 1 in pull sync** — it has no FK dependencies on other tenant tables (only references restaurant_id from restaurants, which is pulled as part of user session setup).
- **Side-effect ledger entries (SALE, WITHDRAWAL) must be created in the originating repository** — TransactionRepository creates SALE entries, CashWithdrawalRepository creates WITHDRAWAL entries. This keeps the ledger logic close to the business event.

## 2026-02-18 — CashBalance Constructor Missing Argument

### Problem
Build error: `No value passed for parameter 'currentBalance'` at `PosViewModel.kt:100`.

### Root Cause
When `totalAdjustments` was added to `CashBalance` data class, `currentBalance` (no default value) became the 5th parameter. `PosViewModel` still used `CashBalance(0, 0, 0, 0)` (4 args) — positional args mapped to `initialBalance`, `totalCashSales`, `totalWithdrawals`, `totalAdjustments`, leaving `currentBalance` unset.

### Fix
Changed `CashBalance(0, 0, 0, 0)` → `CashBalance(0, 0, 0, 0, 0)` in `PosViewModel.kt`.

### LESSON
When adding fields to a data class used as a default/empty value elsewhere, search all constructor call sites (`ClassName(`) and update them. Fields without default values break positional calls silently at compile time.

## 2026-02-18 — Inventory COGS & Nilai Stok

### Goal
Stok Barang screen shows weighted-average COGS (HPP) and item value per item, plus total inventory value in the header.

### Architecture
- **COGS source:** `goods_receiving_items.cost_per_unit` (Rp per purchasing unit) + `qty` + `unit`
- **Formula:** weighted average COGS per base unit = `SUM(qty × costPerUnit) / SUM(normalizedQty)` where `normalizedQty = UnitConverter.normalizeToBase(qty, unit).first`
- **Item value:** `avgCogs (Rp/base unit) × currentQty (base unit)` = total Rp
- **Total value:** `SUM(itemValue)` across all inventory items

### Display Formatting
- avgCogs stored as per base unit (Rp/g, Rp/mL, Rp/pcs)
- Display converts to natural unit for readability:
  - "g" → `CurrencyFormatter.format(avgCogs × 1000) + "/kg"`
  - "mL" → `CurrencyFormatter.format(avgCogs × 1000) + "/L"`
  - "pcs" → `CurrencyFormatter.format(avgCogs) + "/pcs"`
- If `avgCogs == 0L` (no purchase data), show "-"

### Files Changed
- `GoodsReceivingDao.kt`: Added `getAllItems(restaurantId): Flow<List<GoodsReceivingItemEntity>>`
- `InventoryItem.kt`: Added `avgCogs: Long`, `itemValue: Long`, `displayAvgCogs`, `displayItemValue` (also imports CurrencyFormatter)
- `InventoryRepository.kt`: Injected `GoodsReceivingDao`, 5-flow combine, `buildCogsMap()` private function
- `InventoryViewModel.kt`: Added `totalInventoryValue: StateFlow<Long>`
- `InventoryScreen.kt`: Header row (title + total value top-right), card expands to show "HPP avg" and item value row when avgCogs > 0

### Pattern: 5-flow combine
```kotlin
combine(flow1, flow2, flow3, flow4, flow5) { a, b, c, d, e -> ... }
```
`kotlinx.coroutines.flow.combine` has typed overloads up to 5 flows.

### LESSON
- Domain model can import utility classes (CurrencyFormatter, UnitConverter) if display formatting logically belongs to the domain. Consistent with existing `displayQty` pattern using `UnitConverter`.
- When computing COGS from purchase history, always normalize to base unit before averaging — different purchase units (kg, g) for the same item must be made comparable first.

## 2026-02-18 — Transaction Does Not Update Stok Barang

### Problem
After completing a sale in POS, Stok Barang did not reflect the decremented stock. Any pull-to-refresh or background sync (WorkManager 15 min) reverted inventory back to pre-transaction values.

### Root Cause
`TransactionRepository.createTransaction()` called `inventoryDao.decrementStock()` (Room updated ✓), but the corresponding Supabase push for inventory was only enqueued inside the transaction `catch` block. On **success**, inventory changes were never pushed to Supabase. Any subsequent pull fetched stale Supabase values and overwrote local Room — undoing the decrement.

### Fix
Applied Sync Side-Effects Rule: push inventory changes independently after transaction push, unconditionally:
```kotlin
inventorySyncEntries.forEach { entry ->
    try {
        syncManager.pushToSupabase(entry.tableName, entry.operation, entry.recordId)
    } catch (e: Exception) {
        syncQueueDao.enqueue(entry)
        syncScheduler.requestImmediateSync()
    }
}
```

### Files Changed
- `TransactionRepository.kt`: Added independent inventory push loop after the transaction push try-catch

### LESSON
Inventory side-effects from transactions must follow the **Sync Side-Effects Rule** — push independently, never nested inside the parent entity push. Same rule applies to: goods_receiving → inventory, transaction → inventory. Violation causes Supabase inventory to remain stale; any pull will revert local Room to pre-transaction values.

## 2026-02-19 -- Manajemen User Screen Improvements

### Changes Made
1. **Missing fields in Tambah User form:** Added `email` (optional), `phone` (optional), `password` (optional) fields to `AddUserDialog` and wired through ViewModel to Repository.
2. **restaurant_id auto-assigned:** `UserRepository` now injects `SessionManager`. `createUser()` sets `restaurantId = sessionManager.currentRestaurantId` on new user entities.
3. **Scrollable Akses Fitur checkboxes:** Feature list wrapped in `Box(Modifier.heightIn(max = 180.dp)) { LazyColumn(...) }` in both Add and Edit dialogs.
4. **Delete user:** Added `deleteById()` to `UserDao`. `UserRepository.deleteUser()` does Room delete + Supabase DELETE push. `EditUserDialog` has "Hapus" button triggering a confirmation AlertDialog.
5. **Pull-to-refresh:** `UserManagementScreen` uses `PullToRefreshBox`. ViewModel exposes `isRefreshing` + `refresh()` via `SyncManager.pullAllFromSupabase()`.
6. **Settings conditional rendering:** New `SettingsViewModel.kt` exposes `showFullSettings: StateFlow<Boolean>`. Cashiers without `UserFeature.SETTINGS` only see the logout button.

### Key Patterns Confirmed
- **AlertDialog delete confirmation:** Separate `showDeleteConfirm: Boolean` state -> confirmation AlertDialog before calling ViewModel delete.
- **Bounded-height scrollable list inside AlertDialog:** `Box(Modifier.heightIn(max = Xdp)) { LazyColumn(...) }` -- safe approach, avoids nested scroll container issues.
- **Feature-gated screen content:** Minimal ViewModel reads `sessionManager.currentUser`, maps to `showFullSettings: Boolean`. Screen uses `if (showFullSettings)` to show/hide content blocks.
- **createUser restaurantId rule:** Always set `restaurantId = sessionManager.currentRestaurantId` on new user entities to ensure tenant isolation.

## 2026-02-19 — QRIS Cloud Sync (Cross-Device Settings)

### Goal
QRIS image and merchant name now persist to Supabase so any device that logs in loads the same QRIS configuration automatically.

### Architecture
- **Image storage:** Supabase Storage bucket `qris-images`, path `{restaurantId}/qris.jpg` (public, upsert on save)
- **Metadata storage:** `restaurants.qris_image_url` (TEXT) + `restaurants.qris_merchant_name` (TEXT)
- **Local cache:** `QrisSettingsDataStore` (unchanged) caches the Supabase URL for offline display
- **Pull on login:** `SyncManager.pullAllFromSupabase()` Phase 1 now fetches the restaurant record and populates DataStore with QRIS fields
- **DB migration:** v14 → v15, adds `qris_image_url` + `qris_merchant_name` to Room `restaurants` table

### Upload flow (QrisRepository.saveQrisSettings)
1. If imageUri is `content://` → upload bytes to Storage → get public HTTPS URL
2. If imageUri is `https://` already → use as-is (re-save or merchant name change only)
3. If imageUri is blank → save null/empty
4. UPDATE `restaurants` table in Supabase with new URL + merchant name via PostgREST
5. Save URL + merchant to DataStore (local cache)

### Pull flow (SyncManager.pullAllFromSupabase Phase 1)
- Fetch restaurant record by ID → insert to Room (`restaurantDao.insert`) → if `qris_image_url` non-empty, call `qrisSettingsDataStore.saveSettings()` → DataStore populated → `QrisPaymentGateway.isQrisConfigured` becomes true

### Files created
- `core/data/repository/QrisRepository.kt` — upload + save + pull logic

### Files changed
- `gradle/libs.versions.toml` — added `supabase-storage`
- `app/build.gradle.kts` — added `implementation(libs.supabase.storage)`
- `SupabaseClientProvider.kt` — installed `Storage` plugin
- `RestaurantEntity.kt` — added `qrisImageUrl: String?`, `qrisMerchantName: String?` fields
- `RestaurantDto.kt` — added matching `@SerialName` fields
- `DtoEntityMappers.kt` — updated Restaurant Entity↔DTO mappers
- `AyaKasirDatabase.kt` — version 15, MIGRATION_14_15
- `DatabaseModule.kt` — registered MIGRATION_14_15
- `SyncManager.kt` — injected `QrisSettingsDataStore`, added restaurant QRIS pull in Phase 1
- `QrisSettingsViewModel.kt` — injected `QrisRepository`, added `UiState(isUploading, error, savedSuccessfully)`
- `QrisSettingsScreen.kt` — loading spinner on save button, disabled inputs during upload, Snackbar for success/error
- `supabase/schema.sql` — added `qris_image_url` + `qris_merchant_name` to restaurants table definition + bucket setup comment

### Supabase setup required (one-time)
1. Run ALTER TABLE: `ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS qris_image_url TEXT; ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS qris_merchant_name TEXT;`
2. Create Storage bucket: Dashboard → Storage → New bucket → name: `qris-images`, Public: true
3. Add RLS policy on `storage.objects`: allow INSERT for authenticated / SELECT for public (anon) on bucket `qris-images`

### LESSONS
- **Settings stored only in DataStore are device-local.** Any setting that needs cross-device persistence must be backed by Supabase (either a table column or Storage).
- **Supabase Storage upload pattern:** `supabaseClient.storage.from("bucket").upload(path, bytes) { upsert = true }` → `publicUrl(path)` returns the CDN-accessible URL. Store this URL in PostgREST table for other devices to fetch.
- **Local content:// URIs are not portable.** Always upload to cloud storage and save the public HTTPS URL. Detect by checking `imageUri.startsWith("https://")` — if so, no upload needed (already a remote URL).
- **Restaurant record pull in SyncManager.pullAllFromSupabase Phase 1:** `restaurants` table is the tenant itself (not in the 12 tenant-scoped tables), but still needs to be pulled for cross-device settings like QRIS. Add it alongside categories/vendors/users in Phase 1 (no FK dependencies).

## 2026-02-19 — QRIS Upload Fix (Storage plugin missing from Hilt DI)

### Problem
QRIS image upload silently failed — image never stored in Supabase Storage. No error shown to user (caught by try-catch in ViewModel).

### Root Cause
`NetworkModule.provideSupabaseClient()` (the Hilt-provided `SupabaseClient`) only installed `Postgrest`, `Auth`, `Realtime` — **missing `Storage` plugin**. The separate `SupabaseClientProvider.kt` had `Storage` installed, but that class is NOT used by Hilt injection. `QrisRepository` injects `SupabaseClient` from Hilt, so `supabaseClient.storage` would throw at runtime.

### Fix
- Added `import io.github.jan.supabase.storage.Storage` to `NetworkModule.kt`
- Added `install(Storage)` to `provideSupabaseClient()` builder block

### LESSON
- **All Supabase plugins must be installed in the Hilt-provided client (`NetworkModule`), not in `SupabaseClientProvider`.** The `SupabaseClientProvider` is a legacy wrapper; Hilt injects `SupabaseClient` directly from `NetworkModule`. When adding new Supabase features (Storage, Auth, Realtime), always verify the plugin is installed in `NetworkModule.provideSupabaseClient()`.

## 2026-02-19 — QRIS Storage RLS Policy Fix (anon vs authenticated)

### Problem
`Gagal menyimpan: new row violates row-level security policy` when uploading QRIS image to Supabase Storage.

### Root Cause
Storage bucket RLS INSERT policy was set to `TO authenticated`, but this app uses the **anon key** (no Supabase Auth module for login). All requests arrive as `anon` role, not `authenticated`. The INSERT was rejected by RLS.

### Fix (Supabase SQL Editor, no app code change)
- Changed Storage `INSERT` policy on `storage.objects` from `TO authenticated` → `TO anon` with `WITH CHECK (bucket_id = 'qris-images')`
- Added `UPDATE` policy for `TO anon` (needed because `upload { upsert = true }` performs UPDATE on re-upload)
- Ensured `SELECT` policy also targets `TO anon`

### LESSON
- **This app does NOT use Supabase Auth.** All Supabase requests use the anon key. Therefore ALL RLS policies (tables AND storage) must target the `anon` role, not `authenticated`. When writing RLS policies for this project, always use `TO anon` or `TO public`.

## 2026-02-19 — Settings: Owner-Only Cards for Cashier Role

### Change
When a Cashier has SETTINGS feature access, they now only see **Pengaturan Printer** + **Keluar**. Six owner-only cards are hidden: Saldo Awal Kas, Manajemen User, Manajemen Kategori, Manajemen Vendor, Manajemen Barang, Pengaturan QRIS.

### Implementation
- `SettingsViewModel`: Added `isOwner: StateFlow<Boolean>` — maps `sessionManager.currentUser` to `user.role == UserRole.OWNER`.
- `SettingsScreen`: Collects `isOwner`, wraps the 6 owner-only cards inside `if (isOwner) { ... }`.
- Printer card stays inside `if (showFullSettings)` but outside `if (isOwner)` — visible to any user with settings access.

### Files changed
- `feature/settings/SettingsViewModel.kt`: Added `isOwner` StateFlow
- `feature/settings/SettingsScreen.kt`: Collect `isOwner`, wrap 6 cards in `if (isOwner)`

### Visibility matrix
| Card | OWNER | CASHIER+SETTINGS | CASHIER (no SETTINGS) |
|------|-------|-------------------|-----------------------|
| Printer | Yes | Yes | No |
| Saldo Awal Kas | Yes | No | No |
| Manajemen User | Yes | No | No |
| Manajemen Kategori | Yes | No | No |
| Manajemen Vendor | Yes | No | No |
| Manajemen Barang | Yes | No | No |
| Pengaturan QRIS | Yes | No | No |
| Keluar | Yes | Yes | Yes |

## 2026-02-19 — Pembelian: Owner-Only Edit/Delete on Goods Receiving

### Change
Edit and Delete buttons (and FAB "Tambah") on Penerimaan Barang cards are now hidden for Cashier role. Only Owner can create, edit, or delete goods receiving records.

### Implementation
- `PurchasingViewModel`: Added `val isOwner: Boolean get() = sessionManager.isOwner` (SessionManager already injected).
- `GoodsReceivingListScreen`: Read `viewModel.isOwner`, wrapped Edit/Delete `IconButton`s and `FloatingActionButton` in `if (isOwner)`.

### Files changed
- `feature/purchasing/PurchasingViewModel.kt`: Added `isOwner` property
- `feature/purchasing/GoodsReceivingListScreen.kt`: Conditional rendering of Edit/Delete/FAB based on `isOwner`

### Pattern
For role-gated UI in screens that share a ViewModel with SessionManager already injected, expose `val isOwner: Boolean get() = sessionManager.isOwner` from ViewModel and use `if (isOwner)` in Composable. No StateFlow needed for a synchronous property read.

## 2026-02-19 — Pembelian: Cashier Can Add but Not Edit/Delete

### Change
Reversed FAB restriction from previous change. Cashiers can now add new Penerimaan Barang records (FAB always visible), but still cannot edit or delete existing records (Edit/Delete buttons remain owner-only).

### Files changed
- `feature/purchasing/GoodsReceivingListScreen.kt`: Removed `if (isOwner)` wrapper around FAB. Edit/Delete `IconButton`s remain inside `if (isOwner)`.

### Updated Visibility Matrix (Penerimaan Barang)
| Action | OWNER | CASHIER |
|--------|-------|---------|
| Add (FAB) | Yes | Yes |
| Edit | Yes | No |
| Delete | Yes | No |
| View/Expand | Yes | Yes |
