# Code Rules

## Repository Pattern (MUST follow)
Write flow:
1. Save locally with PENDING.
2. Try immediate server push.
3. If failed → enqueue to SyncQueue.

```kotlin
@Singleton
class ExampleRepository @Inject constructor(
    private val dao: ExampleDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncScheduler: SyncScheduler
) {
    // Read: Flow from DAO
    fun getAll(): Flow<List<Domain>> = dao.getAll().map { list -> list.map { it.toDomain() } }

    // Write: Insert → try immediate API → if fail → queue.
    suspend fun create(name: String): Domain {
        val id = UuidGenerator.generate()

        val entity = ExampleEntity(
            id = id,
            name = name,
            syncStatus = SyncStatus.PENDING,
            updatedAt = System.currentTimeMillis()
        )

        // 1. write locally
        dao.insert(entity)

        try {
            // 2. try immediate push
            api.insert(entity.toDto())

            // 3. success → mark synced
            dao.update(entity.copy(syncStatus = SyncStatus.SYNCED))
        } catch (e: Exception) {

            // 4. failed → enqueue retry
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    tableName = "table_name",
                    recordId = id,
                    operation = "INSERT",
                    payload = entity.toJson()
                )
            )

            syncScheduler.requestImmediateSync()
        }

        return entity.toDomain()
    }

    // Update: Set synced=PENDING, updatedAt=now
    suspend fun update(id: String, name: String) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(name = name, synced = PENDING, updatedAt = System.currentTimeMillis()))
        syncQueueDao.enqueue(SyncQueueEntity(tableName = "...", recordId = id, operation = "UPDATE", payload = "..."))
        syncScheduler.requestImmediateSync()
    }

    // Delete
    suspend fun delete(id: String) {
        dao.deleteById(id)
        syncQueueDao.enqueue(SyncQueueEntity(tableName = "...", recordId = id, operation = "DELETE", payload = "..."))
        syncScheduler.requestImmediateSync()
    }
}
```

## ViewModel Pattern (MUST follow)
```kotlin
@HiltViewModel
class ExampleViewModel @Inject constructor(
    private val repository: ExampleRepository
) : ViewModel() {
    data class UiState(val items: List<Item> = emptyList(), val isLoading: Boolean = false, val error: String? = null)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Expose Flow as StateFlow
    val items: StateFlow<List<Item>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun doAction() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.create("name")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
```

## Screen Pattern (MUST follow)
```kotlin
@Composable
fun ExampleScreen(viewModel: ExampleViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    // UI composition
}
```

## Entity Rules (MUST follow)
```kotlin
@Entity(tableName = "example")
data class ExampleEntity(
    @PrimaryKey val id: String,              // UUID v4
    val name: String,
    val syncStatus: SyncStatus = SyncStatus.PENDING, // REQUIRED
    val updatedAt: Long = System.currentTimeMillis() // REQUIRED
)
```

## DAO Rules (MUST follow)
```kotlin
@Dao
interface ExampleDao {
    @Query("SELECT * FROM example ORDER BY name")
    fun getAll(): Flow<List<ExampleEntity>>

    @Query("SELECT * FROM example WHERE id = :id")
    suspend fun getById(id: String): ExampleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExampleEntity)

    @Update
    suspend fun update(entity: ExampleEntity)

    @Query("DELETE FROM example WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

## Navigation Rules
- Add screen to [Screen.kt](app/src/main/java/com/ayakasir/app/core/navigation/Screen.kt):
  ```kotlin
  @Serializable data object NewScreen : Screen
  @Serializable data class EditScreen(val id: String) : Screen
  ```
- Register in AyaKasirNavHost.kt:
  ```kotlin
  composable<Screen.NewScreen> { NewScreen() }
  composable<Screen.EditScreen> { backStackEntry ->
      val args = backStackEntry.toRoute<Screen.EditScreen>()
      EditScreen(id = args.id)
  }
  ```

## Hilt DI Rules
- Application: `@HiltAndroidApp class AyaKasirApp : Application()`
- Activity: `@AndroidEntryPoint class MainActivity : ComponentActivity()`
- ViewModel: `@HiltViewModel class VM @Inject constructor(...) : ViewModel()`
- Repository: `@Singleton class Repo @Inject constructor(...)`
- Module: `@Module @InstallIn(SingletonComponent::class)`

## WorkManager Initialization Rule
- If `AyaKasirApp` (or any Application class) implements `androidx.work.Configuration.Provider`, remove only WorkManager's startup metadata from manifest:
  - Add `xmlns:tools="http://schemas.android.com/tools"` on `<manifest>`.
  - Under `<application>`, add `androidx.startup.InitializationProvider` with:
    - `<meta-data android:name="androidx.work.WorkManagerInitializer" tools:node="remove" />`
- Keep `InitializationProvider` itself (do not remove the whole provider), so other AndroidX Startup initializers still run.

## Release R8 Rule (Ktor)
- Keep these in `app/proguard-rules.pro` for release minify:
  - `-dontwarn java.lang.management.ManagementFactory`
  - `-dontwarn java.lang.management.RuntimeMXBean`
- Reason: Ktor debug detector references JVM management classes that are unavailable on Android; without these rules, `minifyReleaseWithR8` fails.

## Sync Queue Rules
- SyncQueueEntity fields: `tableName`, `recordId`, `operation` (INSERT/UPDATE/DELETE), `payload` (JSON)
- Enqueue only if immediate push fails OR when offline.
- Always call `syncScheduler.requestImmediateSync()` after enqueue

## Pull Sync Rules
- `SyncManager.pullAllFromSupabase(restaurantId)` pulls all 13 tenant-related tables from Supabase and upserts to Room (REPLACE strategy = server wins).
- Called in three places:
  1. **`AuthViewModel.loginWithEmail()`** — after `loginFull()` succeeds, before navigating to app (blocking). Ensures cross-device sync on login.
  2. **`AuthViewModel.authenticatePin()`** — after `loginPin()` succeeds, in a background coroutine (non-blocking). Catches changes made while app was closed.
  3. **`SyncManager.syncAll()`** — Phase 2 after push queue is processed. Runs during every WorkManager periodic sync (15 min).
- Pull is 3-phase to respect Room FK constraints:
  - Phase 1 (parallel): categories, vendors, users, cash_withdrawals, general_ledger (leaf/independent tables)
  - Phase 2 (parallel, after phase 1): products, goods_receiving, transactions, inventory
  - Phase 3 (parallel, after phase 2): variants, product_components, goods_receiving_items, transaction_items
- Per-record inserts are sequential within each table.
- `SyncManager` injects `SessionManager` to get `currentRestaurantId` for use in `syncAll()`.

## Push Sync Rules — Child Items
- NEVER use `json.encodeToJsonElement(list)` for bulk upsert to Supabase (supabase-kt cannot reliably handle JsonArray in `upsert()`).
- Always push child items (goods_receiving_items, transaction_items) **one by one** via `forEach { supabaseClient.from(...).upsert(json.encodeToJsonElement(singleDto)) }`.
- Push side-effects (e.g. inventory from goods_receiving) **independently** — not gated by the parent push success. Use separate try-catch blocks.

## Multi-Tenancy Rule
- All operations must use restaurant context from SessionManager.
- Every tenant-scoped entity MUST include `@ColumnInfo(name = "restaurant_id") val restaurantId: String = ""`.
- Tenant-scoped tables (12): categories, products, variants, inventory, product_components, vendors, goods_receiving, goods_receiving_items, transactions, transaction_items, cash_withdrawals, general_ledger.
- Every repository that reads lists or creates entities MUST inject `SessionManager` and use `private val restaurantId: String get() = sessionManager.currentRestaurantId ?: ""`.
- DAO "list all" queries MUST filter by `WHERE restaurant_id = :restaurantId`.
- Child tables (variants, product_components, goods_receiving_items, transaction_items, inventory) also store `restaurant_id` (denormalized). Their DAO queries keyed by parentId do NOT need extra restaurant filter — but list-all queries do.
- Supabase schema: all 12 tables have `restaurant_id UUID REFERENCES restaurants(id)` + index `idx_<table>_restaurant`.

## Auth Rule
- Email/password login = required on first login and after explicit logout from Pengaturan. Password hash stored in `users` table (`password_hash`, `password_salt`), validated locally via `PinHasher.verify()`. No Supabase Auth dependency.
- PIN unlock = used when app is closed/minimized (session persisted via AuthSessionDataStore).
- Login flow: `UserRepository.authenticateByEmail(email, password)` → fetches user from Supabase Postgres (source of truth), updates local cache, verifies password hash locally. Returns sealed class `AuthResult` (Success/NotFound/Inactive/WrongPassword/NoPassword).
- SessionManager.loginFull() = persists session to DataStore (email/password login).
- SessionManager.loginPin() = in-memory only (PIN unlock on app resume).
- SessionManager.logout() = clears DataStore (requires email/password on next launch).
- Registration: Both restaurants and users default `is_active = false`. Requires admin activation before login works.


## Conflict Resolution (ConflictResolver)
- Master data (Category, Product, Vendor, User): Last-Write-Wins (LWW)
- Transactions: Local-always-wins
- Inventory: Delta-based (add quantities, don't overwrite)
- Server is source of truth unless rule explicitly overrides.

## Utilities
- UUID: `UuidGenerator.generate()`
- Currency: `CurrencyFormatter.format(25000L)` → "Rp25.000"
- Date: `DateTimeUtil.todayRange()` → (start: Long, end: Long)
- Date (period): `DateTimeUtil.dayRange(epochMillis)`, `DateTimeUtil.monthRange()`, `DateTimeUtil.yearRange()`
- PIN: `PinHasher.hash(pin, salt)`, `PinHasher.verify(pin, salt, hash)`
- Unit: `UnitConverter.normalizeToBase(1, "kg")` → (1000, "g"), `UnitConverter.convert(200, "g", "g")` → 200, `UnitConverter.formatForDisplay(1500, "g")` → "1.5 kg"

## Unit Conversion Rules
- Inventory stores qty in **base units**: g (mass), mL (volume), pcs (count).
- `InventoryEntity` has `unit: String = "pcs"` column — stores the base unit.
- When receiving goods (PurchasingRepository): normalize incoming (qty, unit) to base unit via `UnitConverter.normalizeToBase()` before storing.
- When decrementing stock via components (TransactionRepository): convert component's (requiredQty, unit) to inventory's unit via `UnitConverter.convert()` before decrementing.
- Supported conversions: kg → g (×1000), L → mL (×1000). pcs stays pcs.
- `InventoryItem.displayQty` / `displayMinQty` use `UnitConverter.formatForDisplay()` for human-readable display (e.g. 1500 g → "1.5 kg").

## Sync Side-Effects Rule
- When a write produces side-effects (e.g. goods_receiving → inventory update), push those side-effects on BOTH success and failure paths.
- On success: iterate side-effect entries and push each individually (catch per entry).
- On failure: enqueue all side-effect entries + main entry to SyncQueue.

## Room @Relation Caution
- Room `@Relation` can return empty lists when querying immediately after insert (timing/caching issue with multiple relations).
- For sync/push operations in SyncManager, prefer direct queries (`@Query`) over `@Relation` classes.
- Reserve `@Relation` for UI-facing read flows (Flow-based queries) where timing is not critical.

## Realtime Sync Rules
- `RealtimeManager` (@Singleton) subscribes to Supabase Postgres Changes for all 12 tenant tables + `restaurants` table (13 listeners total) via a single channel.
- Tenant tables filtered by `restaurant_id`; `restaurants` table filtered by `id` (the restaurant's own record).
- The `restaurants` listener also updates `QrisSettingsDataStore` with QRIS fields on UPDATE — enables real-time QRIS sync across devices.
- Lifecycle: `connect(restaurantId)` called from `SessionManager.loginFull()` and `loginPin()`; `disconnect()` called from `SessionManager.logout()`.
- On INSERT/UPDATE: `action.decodeRecord<Dto>().toEntity()` → `dao.insert()` (REPLACE).
- On DELETE: `action.decodeOldRecord<Dto>()` → `dao.deleteById(id)`.
- All listeners set up BEFORE `channel.subscribe()` (supabase-kt requirement).
- Errors caught per-event with `runCatching` — never crash on decode failures.
- Requires `ktor-client-okhttp` (not `ktor-client-android`) for WebSocket support.
- **Filter API:** Use `filter(column = "col", operator = FilterOperator.EQ, value = val)` method. Do NOT use `filter = "col=eq.val"` (private setter).
- **Decode imports required:** `import io.github.jan.supabase.realtime.decodeRecord` and `import io.github.jan.supabase.realtime.decodeOldRecord` (top-level extension functions, not member functions).

## Pull-to-Refresh Pattern (MUST follow for data screens)
```kotlin
// ViewModel: inject SyncManager + SessionManager
private val _isRefreshing = MutableStateFlow(false)
val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

fun refresh() {
    val restaurantId = sessionManager.currentRestaurantId ?: return
    viewModelScope.launch {
        _isRefreshing.value = true
        try { syncManager.pullAllFromSupabase(restaurantId) }
        finally { _isRefreshing.value = false }
    }
}

// Screen: wrap content in PullToRefreshBox
val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
PullToRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = { viewModel.refresh() },
    modifier = Modifier.fillMaxSize()
) { /* existing content */ }
```

## Dashboard Date Filter Rule
- Dashboard period options must include exactly: `Hari ini`, `Bulan ini`, `Tahun ini`, `Pilih tanggal`.
- `Pilih tanggal` uses a single-date picker, then maps to a full-day range via `DateTimeUtil.dayRange(selectedDateMillis)`.
- Keep selected period in ViewModel `StateFlow`, and derive metric flows with `flatMapLatest` based on the active date range.
- Dashboard sales summary/product summary empty states should use the active period label (not hardcoded "hari ini").

## Cascade Delete Sync Rule
When a parent operation cascades to child entities (e.g., delete product → variants/components deleted):
1. Collect child IDs BEFORE local cascade delete (via `dao.getByProductIdDirect()`)
2. Delete locally (parent cascade)
3. Sync parent first
4. Sync each child deletion independently (own try-catch + SyncQueue fallback)

Same applies to replace operations (e.g., update product replaces variants):
1. Collect old child IDs before delete
2. Replace locally
3. Sync parent
4. Push DELETE for old children, INSERT for new children

## Email Handling Rule
- Always normalize email: `.trim().lowercase()` before storing or querying.
- Supabase queries: use `ilike("email", normalizedEmail)` (NOT `eq`).
- Room queries: use `WHERE LOWER(email) = LOWER(:email)`.
- Never use `catch (_: Exception)` without logging in Supabase network calls — at minimum `Log.w(TAG, "...: ${e.message}")`.

## Supabase Credential Rule
- Credentials (`SUPABASE_URL`, `SUPABASE_ANON_KEY`) are stored in `local.properties` (gitignored).
- `app/build.gradle.kts` reads them via `localProperties.getProperty("SUPABASE_URL", "")`.
- NEVER hardcode credentials in committed files. The `build.gradle.kts` fallback is empty string `""`.
- To set up a new dev environment: add `SUPABASE_URL=https://...` and `SUPABASE_ANON_KEY=eyJ...` to `local.properties`.

## General Ledger Rule
- Cash balance (Saldo Kas) is derived from `SUM(amount) WHERE type IN ('INITIAL_BALANCE', 'SALE', 'WITHDRAWAL', 'ADJUSTMENT')` — only cash-affecting types.
- `LedgerType` enum: `INITIAL_BALANCE`, `SALE`, `SALE_QRIS`, `WITHDRAWAL`, `ADJUSTMENT`, `COGS`.
- **Cash-affecting types** (included in Saldo Kas): `INITIAL_BALANCE`, `SALE`, `WITHDRAWAL`, `ADJUSTMENT`.
- **Non-cash types** (recorded for bookkeeping, excluded from Saldo Kas): `SALE_QRIS`, `COGS`.
- Supabase `general_ledger.type` CHECK constraint MUST include all six ledger types above; otherwise `SALE_QRIS` / `COGS` pushes will fail and stay queued.
- For existing Supabase databases, run `supabase/migration_general_ledger_type_constraint.sql` once to align the remote constraint.
- Signed amounts: positive = inflow (INITIAL_BALANCE, SALE, SALE_QRIS, ADJUSTMENT credit), negative = outflow (WITHDRAWAL, COGS).
- Every financial event MUST create a ledger entry via `GeneralLedgerRepository.recordEntry()`:
  - **CASH sale** (TransactionRepository): `LedgerType.SALE`, amount = transaction total, referenceId = transaction ID
  - **QRIS sale** (TransactionRepository): `LedgerType.SALE_QRIS`, amount = transaction total, referenceId = transaction ID
  - **Withdrawal** (CashWithdrawalRepository): `LedgerType.WITHDRAWAL`, amount = -withdrawalAmount, referenceId = withdrawal ID
  - **Initial balance** (InitialBalanceViewModel): `LedgerType.INITIAL_BALANCE`, amount = balance amount
  - **Goods receiving** (PurchasingRepository): `LedgerType.COGS`, amount = -totalCost, referenceId = goods_receiving ID
  - **Adjustment** (future): `LedgerType.ADJUSTMENT`, amount = +/- delta
- COGS entries are managed on create/update/delete of goods receiving: `deleteByReferenceId()` removes old entry before recording new one on update; deletes on goods receiving deletion.
- `CashBalanceRepository` reads from `GeneralLedgerRepository` flows (no DataStore, no TransactionRepository, no CashWithdrawalRepository).
- `CashBalance` model includes `totalQrisSales` and `totalCogs` for informational display (shown in "Info Tambahan" section of CashBalanceDetailDialog).

## General Ledger CSV Export Rule
- Data source is `general_ledger` as the base table, enriched via reference joins for readability.
- Export rows MUST stay tenant-scoped with `WHERE general_ledger.restaurant_id = currentRestaurantId`.
- `SALE`, `SALE_QRIS`, and `COGS` exports use one row per item by joining `transaction_items` or `goods_receiving_items` through `reference_id`.
- CSV columns order must be: `id,type,product_name,variant_name,amount,qty,description`.
- Default file name format: `ayakasir_<restaurant_name>_<ddmmyyyy>.csv`.

## Feature-Gated Screen Content Rule
- For screens that vary content by user role/features (e.g., SettingsScreen), create a minimal ViewModel that reads `sessionManager.currentUser` and exposes:
  - `showFullSettings: StateFlow<Boolean>` — true if OWNER or has SETTINGS feature access. Controls whether settings cards are shown at all (vs logout-only).
  - `isOwner: StateFlow<Boolean>` — true if OWNER role. Controls owner-only cards within settings.
- Screen collects both values via `collectAsStateWithLifecycle()`:
  - `if (showFullSettings)` → show settings cards (Printer + owner-only cards)
  - `if (isOwner)` → show owner-only cards: Saldo Awal Kas, Manajemen User, Manajemen Kategori, Manajemen Vendor, Manajemen Barang, Pengaturan QRIS, Unduh Data
  - Cashier with SETTINGS access sees: Pengaturan Printer + Keluar only.
- The logout button MUST always be visible regardless of feature access (every role needs to log out).
- For screens with an existing ViewModel that already injects `SessionManager`, expose `val isOwner: Boolean get() = sessionManager.isOwner` (no StateFlow needed) and use `if (isOwner)` in Composable for owner-only UI elements.
- **Pembelian screen:** Penerimaan Barang edit/delete buttons are owner-only. Cashiers can add new records (FAB visible) but cannot edit or delete existing ones.

## Menu List Screen Pattern
- `ProductListScreen` renders only `ProductType.MENU_ITEM`.
- Render list grouped by menu categories (`menuCategories` order from repository). If a menu item's category is blank/missing, place it under `"Tanpa Kategori"`.
- Top-right app bar search action toggles a title search field and filters by case-insensitive `product.name` match.
- Clone action must duplicate the full menu definition: base product fields + variants + product components.
- Clone naming convention: `<original> (Copy)` then `<original> (Copy 2)`, `<original> (Copy 3)`, and so on to avoid collisions.

## AlertDialog Delete Confirmation Pattern
```kotlin
// In Screen: add separate state for delete confirmation
var showDeleteConfirm by remember { mutableStateOf(false) }

// In EditDialog: "Hapus" button in dismissButton slot
dismissButton = {
    Row {
        TextButton(onClick = onDelete, enabled = !isSaving) {
            Text("Hapus", color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onDismiss) { Text("Batal") }
    }
}

// In Screen: show confirmation dialog when onDelete is called
if (showDeleteConfirm) {
    AlertDialog(
        title = { Text("Hapus Item") },
        text = { Text("Yakin hapus? Tidak bisa dibatalkan.") },
        confirmButton = { TextButton(onClick = { viewModel.delete(id) }) { Text("Hapus") } },
        dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Batal") } }
    )
}
```

## Bounded-Height Scrollable List in AlertDialog
- DO NOT use `Column + verticalScroll` inside `AlertDialog.text` for long lists — causes nested scroll conflicts.
- Use `Box(Modifier.heightIn(max = Xdp)) { LazyColumn(...) }` for independently scrollable bounded list.
```kotlin
Box(modifier = Modifier.heightIn(max = 180.dp)) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(list) { item -> /* row */ }
    }
}
```

## Printer Connectivity + Receipt Rule
- Printer transport layer must support both:
  - `PrinterConnectionType.BLUETOOTH` (SPP UUID `00001101-0000-1000-8000-00805F9B34FB`)
  - `PrinterConnectionType.WIFI` (raw TCP, default port `9100`)
- Persist last successful printer target (`connection_type`, bluetooth address/name, wifi host/port) and make `print()` attempt `reconnectIfSaved()` when no active socket is connected.
- `PrinterSettingsScreen` must provide:
  - Bluetooth paired-device list + shortcut to system Bluetooth pairing page.
  - WiFi host/port input and connect action.
  - Test print action.
- Receipt format (`EscPosReceiptBuilder`) must include:
  - Header = restaurant name
  - Date and time line
  - Item rows with item name, qty, sub-total
  - `GRAND TOTAL` line
  - Footer exact text: `Dicetak melalui apliakasi AyaKa$ir`
- POS flow: after successful `createTransaction()` for every payment method (CASH and QRIS), show print confirmation dialog.
  - If user confirms print -> send receipt immediately to printer manager.
  - If user declines print -> clear pending receipt state and continue POS flow.

## DO NOT
- Hard-code IDs (use UuidGenerator)
- Hard-code Supabase credentials in build.gradle.kts (use local.properties)
- Forget `syncStatus`, `updatedAt`, and `restaurantId` on tenant-scoped entities
- Skip SyncQueue after writes
- Use decimal types for currency (Long only)
- Forget to filter DAO list queries by `restaurant_id` (data isolation breach!)
- Use Room `@Relation` in SyncManager push operations (use direct queries instead)
- Use `json.encodeToJsonElement(list)` for bulk Supabase upsert (push items one by one instead)
- Pull child tables in parallel with parent tables (respect Room FK constraints with phased pull)
- Cascade-delete child entities without syncing each deletion to Supabase
- Use case-sensitive `eq` for email queries (use `ilike` for Supabase, `LOWER()` for Room)
- Silently swallow exceptions without logging in network calls
- Forget to create a ledger entry when recording financial events (SALE, SALE_QRIS, WITHDRAWAL, INITIAL_BALANCE, COGS)
- Add new LedgerType values to the `getBalance()` SQL IN clause unless they should affect Saldo Kas
- Let `supabase/schema.sql` `general_ledger.type` CHECK drift from `LedgerType` enum values (causes remote sync rejection)
- Forget to set `restaurantId = sessionManager.currentRestaurantId` when creating new users via owner
- Use `Column + verticalScroll` inside AlertDialog for long lists (use `Box(heightIn) + LazyColumn` instead)
- Keep `androidx.work.WorkManagerInitializer` active when using `Application : Configuration.Provider` (fails release lint with `RemoveWorkManagerInitializer`)
