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

## Sync Queue Rules
- SyncQueueEntity fields: `tableName`, `recordId`, `operation` (INSERT/UPDATE/DELETE), `payload` (JSON)
- Enqueue only if immediate push fails OR when offline.
- Always call `syncScheduler.requestImmediateSync()` after enqueue

## Pull Sync Rules
- `SyncManager.pullAllFromSupabase(restaurantId)` pulls all 12 tenant-related tables from Supabase and upserts to Room (REPLACE strategy = server wins).
- Called in two places:
  1. **`AuthViewModel.loginWithEmail()`** — after `loginFull()` succeeds, before navigating to app. Ensures cross-device sync on login.
  2. **`SyncManager.syncAll()`** — Phase 2 after push queue is processed. Runs during every WorkManager periodic sync (15 min).
- Pull is 3-phase to respect Room FK constraints:
  - Phase 1 (parallel): categories, vendors, users, cash_withdrawals (leaf/independent tables)
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
- Tenant-scoped tables: categories, products, variants, inventory, product_components, vendors, goods_receiving, goods_receiving_items, transactions, transaction_items, cash_withdrawals.
- Every repository that reads lists or creates entities MUST inject `SessionManager` and use `private val restaurantId: String get() = sessionManager.currentRestaurantId ?: ""`.
- DAO "list all" queries MUST filter by `WHERE restaurant_id = :restaurantId`.
- Child tables (variants, product_components, goods_receiving_items, transaction_items, inventory) also store `restaurant_id` (denormalized). Their DAO queries keyed by parentId do NOT need extra restaurant filter — but list-all queries do.
- Supabase schema: all 11 tables have `restaurant_id UUID REFERENCES restaurants(id)` + index `idx_<table>_restaurant`.

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
- PIN: `PinHasher.hash(pin, salt)`, `PinHasher.verify(pin, salt, hash)`

## Sync Side-Effects Rule
- When a write produces side-effects (e.g. goods_receiving → inventory update), push those side-effects on BOTH success and failure paths.
- On success: iterate side-effect entries and push each individually (catch per entry).
- On failure: enqueue all side-effect entries + main entry to SyncQueue.

## Room @Relation Caution
- Room `@Relation` can return empty lists when querying immediately after insert (timing/caching issue with multiple relations).
- For sync/push operations in SyncManager, prefer direct queries (`@Query`) over `@Relation` classes.
- Reserve `@Relation` for UI-facing read flows (Flow-based queries) where timing is not critical.

## Realtime Sync Rules
- `RealtimeManager` (@Singleton) subscribes to Supabase Postgres Changes for all 11 tenant tables via a single channel filtered by `restaurant_id`.
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

## DO NOT
- Hard-code IDs (use UuidGenerator)
- Forget `syncStatus`, `updatedAt`, and `restaurantId` on tenant-scoped entities
- Skip SyncQueue after writes
- Use decimal types for currency (Long only)
- Forget to filter DAO list queries by `restaurant_id` (data isolation breach!)
- Use Room `@Relation` in SyncManager push operations (use direct queries instead)
- Use `json.encodeToJsonElement(list)` for bulk Supabase upsert (push items one by one instead)
- Pull child tables in parallel with parent tables (respect Room FK constraints with phased pull)


