package com.ayakasir.app.feature.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.data.repository.CashBalanceRepository
import com.ayakasir.app.core.data.repository.CashWithdrawalRepository
import com.ayakasir.app.core.data.repository.CategoryRepository
import com.ayakasir.app.core.data.repository.ProductRepository
import com.ayakasir.app.core.data.repository.TransactionRepository
import com.ayakasir.app.core.data.local.datastore.QrisSettingsDataStore
import com.ayakasir.app.core.domain.model.CartItem
import com.ayakasir.app.core.domain.model.CashBalance
import com.ayakasir.app.core.domain.model.Category
import com.ayakasir.app.core.domain.model.DiscountType
import com.ayakasir.app.core.domain.model.PaymentMethod
import com.ayakasir.app.core.domain.model.Product
import com.ayakasir.app.core.domain.model.Variant
import com.ayakasir.app.core.payment.PaymentGateway
import com.ayakasir.app.core.payment.PaymentResult
import com.ayakasir.app.core.session.SessionManager
import com.ayakasir.app.core.sync.SyncManager
import com.ayakasir.app.core.util.CurrencyFormatter
import com.ayakasir.app.core.util.UuidGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PosViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val transactionRepository: TransactionRepository,
    private val cashWithdrawalRepository: CashWithdrawalRepository,
    private val cashBalanceRepository: CashBalanceRepository,
    private val sessionManager: SessionManager,
    private val syncManager: SyncManager,
    private val paymentGateway: PaymentGateway,
    private val qrisSettingsDataStore: QrisSettingsDataStore
) : ViewModel() {

    data class PendingWithdrawal(val amount: Long, val reason: String)

    data class QrisPaymentState(
        val referenceId: String,
        val qrCodeData: String,
        val cartItems: List<CartItem>,
        val amount: Long,
        val merchantName: String?,
        val providerName: String,
        val expiresAt: Long?
    )

    data class PosUiState(
        val categories: List<Category> = emptyList(),
        val products: List<Product> = emptyList(),
        val cart: List<CartItem> = emptyList(),
        val selectedCategoryId: String? = null,
        val showVariantSelector: Product? = null,
        val showCheckout: Boolean = false,
        val checkoutSuccess: Boolean = false,
        val isProcessing: Boolean = false,
        val error: String? = null,
        val showCashWithdrawalDialog: Boolean = false,
        val showWithdrawalConfirmation: Boolean = false,
        val pendingWithdrawal: PendingWithdrawal? = null,
        val withdrawalSuccess: Boolean = false,
        val showBalanceDetail: Boolean = false,
        val qrisPayment: QrisPaymentState? = null
    ) {
        val cartTotal: Long get() = cart.sumOf { it.subtotal }
        val cartItemCount: Int get() = cart.sumOf { it.qty }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _uiState = MutableStateFlow(PosUiState())
    val uiState: StateFlow<PosUiState> = _uiState.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)

    val categories: StateFlow<List<Category>> = categoryRepository.getMenuCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<Product>> = _selectedCategoryId.flatMapLatest { catId ->
        if (catId == null) productRepository.getAllActiveMenuItems()
        else productRepository.getMenuItemsByCategory(catId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashBalance: StateFlow<CashBalance> = cashBalanceRepository
        .getCurrentBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CashBalance(0, 0, 0, 0))

    val isQrisConfigured: StateFlow<Boolean> = qrisSettingsDataStore.isConfigured
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun refresh() {
        val restaurantId = sessionManager.currentRestaurantId ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncManager.pullAllFromSupabase(restaurantId)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun addToCart(product: Product, variant: Variant? = null) {
        if (isCheckoutLocked()) return
        _uiState.update { state ->
            val variantId = variant?.id
            val unitPrice = product.price + (variant?.priceAdjustment ?: 0)
            val existing = state.cart.find { it.productId == product.id && it.variantId == variantId }
            val newCart = if (existing != null) {
                state.cart.map {
                    if (it.productId == product.id && it.variantId == variantId) {
                        it.copy(qty = it.qty + 1)
                    } else it
                }
            } else {
                state.cart + CartItem(
                    productId = product.id,
                    productName = product.name,
                    variantId = variantId,
                    variantName = variant?.name,
                    qty = 1,
                    unitPrice = unitPrice
                )
            }
            state.copy(cart = newCart, showVariantSelector = null)
        }
    }

    fun onProductClick(product: Product) {
        if (isCheckoutLocked()) return
        if (product.variants.isNotEmpty()) {
            _uiState.update { it.copy(showVariantSelector = product) }
        } else {
            addToCart(product)
        }
    }

    fun dismissVariantSelector() {
        _uiState.update { it.copy(showVariantSelector = null) }
    }

    fun updateCartItemQty(productId: String, variantId: String?, newQty: Int) {
        if (isCheckoutLocked()) return
        _uiState.update { state ->
            if (newQty <= 0) {
                state.copy(cart = state.cart.filter { !(it.productId == productId && it.variantId == variantId) })
            } else {
                state.copy(cart = state.cart.map {
                    if (it.productId == productId && it.variantId == variantId) {
                        it.copy(qty = newQty)
                    } else it
                })
            }
        }
    }

    fun removeFromCart(productId: String, variantId: String?) {
        if (isCheckoutLocked()) return
        _uiState.update { state ->
            state.copy(cart = state.cart.filter { !(it.productId == productId && it.variantId == variantId) })
        }
    }

    fun clearCart() {
        if (isCheckoutLocked()) return
        _uiState.update { it.copy(cart = emptyList()) }
    }

    fun updateCartItemDiscount(
        productId: String,
        variantId: String?,
        discountType: DiscountType,
        discountValue: Long
    ) {
        if (isCheckoutLocked()) return
        _uiState.update { state ->
            val newCart = state.cart.map {
                if (it.productId == productId && it.variantId == variantId) {
                    val normalizedValue = when (discountType) {
                        DiscountType.NONE -> 0L
                        DiscountType.AMOUNT -> discountValue.coerceAtLeast(0L).coerceAtMost(it.unitPrice)
                        DiscountType.PERCENT -> discountValue.coerceIn(0L, 100L)
                    }
                    it.copy(discountType = discountType, discountValue = normalizedValue)
                } else it
            }
            state.copy(cart = newCart)
        }
    }

    fun showCheckout() {
        _uiState.update { it.copy(showCheckout = true) }
    }

    fun dismissCheckout() {
        _uiState.update { it.copy(showCheckout = false, checkoutSuccess = false) }
    }

    fun checkout(paymentMethod: PaymentMethod) {
        val state = _uiState.value
        if (state.cart.isEmpty() || state.isProcessing || state.qrisPayment != null) return
        val userId = sessionManager.currentUser.value?.id ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                createTransaction(userId, state.cart, paymentMethod, state.cartTotal)
                _uiState.update {
                    it.copy(
                        cart = emptyList(),
                        isProcessing = false,
                        checkoutSuccess = true,
                        showCheckout = false,
                        qrisPayment = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = "Gagal memproses transaksi") }
            }
        }
    }

    fun startQrisPayment() {
        val state = _uiState.value
        if (state.cart.isEmpty() || state.isProcessing || state.qrisPayment != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            val referenceId = UuidGenerator.generate()
            when (val result = paymentGateway.initiatePayment(state.cartTotal, referenceId)) {
                is PaymentResult.Pending -> {
                    val qrData = result.qrCodeData?.trim().orEmpty()
                    if (qrData.isBlank()) {
                        _uiState.update {
                            it.copy(isProcessing = false, error = "QRIS belum dikonfigurasi")
                        }
                        return@launch
                    }
                    val merchant = qrisSettingsDataStore.merchantName.first().trim().ifBlank { null }
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            qrisPayment = QrisPaymentState(
                                referenceId = referenceId,
                                qrCodeData = qrData,
                                cartItems = state.cart,
                                amount = state.cartTotal,
                                merchantName = merchant,
                                providerName = paymentGateway.getProviderName(),
                                expiresAt = result.expiresAt
                            )
                        )
                    }
                }
                is PaymentResult.Success -> {
                    try {
                        val userId = sessionManager.currentUser.value?.id ?: return@launch
                        createTransaction(userId, state.cart, PaymentMethod.QRIS, state.cartTotal)
                        _uiState.update {
                            it.copy(
                                cart = emptyList(),
                                isProcessing = false,
                                checkoutSuccess = true,
                                showCheckout = false,
                                qrisPayment = null
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(isProcessing = false, error = "Gagal memproses transaksi") }
                    }
                }
                is PaymentResult.Failed -> {
                    _uiState.update { it.copy(isProcessing = false, error = result.message) }
                }
                PaymentResult.Cancelled -> {
                    _uiState.update { it.copy(isProcessing = false, error = "Pembayaran dibatalkan") }
                }
            }
        }
    }

    fun dismissQrisPayment() {
        _uiState.update { it.copy(qrisPayment = null) }
    }

    fun confirmQrisPayment() {
        val state = _uiState.value
        val payment = state.qrisPayment ?: return
        if (payment.cartItems.isEmpty() || state.isProcessing) return
        val userId = sessionManager.currentUser.value?.id ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                createTransaction(userId, payment.cartItems, PaymentMethod.QRIS, payment.amount)
                _uiState.update {
                    it.copy(
                        cart = emptyList(),
                        isProcessing = false,
                        checkoutSuccess = true,
                        showCheckout = false,
                        qrisPayment = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = "Gagal memproses transaksi") }
            }
        }
    }

    fun dismissSuccessMessage() {
        _uiState.update { it.copy(checkoutSuccess = false) }
    }

    // Cash withdrawal methods
    fun showCashWithdrawalDialog() {
        _uiState.update { it.copy(showCashWithdrawalDialog = true) }
    }

    fun dismissCashWithdrawalDialog() {
        _uiState.update { it.copy(showCashWithdrawalDialog = false) }
    }

    fun showWithdrawalConfirmation(amount: Long, reason: String) {
        viewModelScope.launch {
            // Check if withdrawal would cause negative balance
            val canWithdraw = cashBalanceRepository.canWithdraw(amount)

            if (canWithdraw) {
                _uiState.update {
                    it.copy(
                        pendingWithdrawal = PendingWithdrawal(amount, reason),
                        showCashWithdrawalDialog = false,
                        showWithdrawalConfirmation = true
                    )
                }
            } else {
                // Show error - insufficient balance
                _uiState.update {
                    it.copy(
                        showCashWithdrawalDialog = false,
                        error = "Saldo tidak mencukupi. Saldo saat ini: ${CurrencyFormatter.format(cashBalance.value.currentBalance)}"
                    )
                }
            }
        }
    }

    fun dismissWithdrawalConfirmation() {
        _uiState.update {
            it.copy(
                showWithdrawalConfirmation = false,
                pendingWithdrawal = null
            )
        }
    }

    fun confirmCashWithdrawal() {
        val pending = _uiState.value.pendingWithdrawal ?: return
        val userId = sessionManager.currentUser.value?.id ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                cashWithdrawalRepository.recordWithdrawal(
                    userId = userId,
                    amount = pending.amount,
                    reason = pending.reason
                )
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        showWithdrawalConfirmation = false,
                        pendingWithdrawal = null,
                        withdrawalSuccess = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = "Gagal mencatat penarikan tunai: ${e.message}"
                    )
                }
            }
        }
    }

    fun dismissWithdrawalSuccess() {
        _uiState.update { it.copy(withdrawalSuccess = false) }
    }

    // Cash balance methods
    fun showBalanceDetail() {
        _uiState.update { it.copy(showBalanceDetail = true) }
    }

    fun dismissBalanceDetail() {
        _uiState.update { it.copy(showBalanceDetail = false) }
    }

    private fun isCheckoutLocked(): Boolean {
        val state = _uiState.value
        return state.isProcessing || state.qrisPayment != null
    }

    private suspend fun createTransaction(
        userId: String,
        cartItems: List<CartItem>,
        paymentMethod: PaymentMethod,
        total: Long
    ) {
        transactionRepository.createTransaction(
            userId = userId,
            cartItems = cartItems,
            paymentMethod = paymentMethod,
            total = total
        )
    }
}
