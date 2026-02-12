package com.ayakasir.app.feature.purchasing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.data.repository.CategoryRepository
import com.ayakasir.app.core.data.repository.ProductRepository
import com.ayakasir.app.core.data.repository.PurchasingRepository
import com.ayakasir.app.core.data.repository.VendorRepository
import com.ayakasir.app.core.domain.model.Category
import com.ayakasir.app.core.domain.model.CategoryType
import com.ayakasir.app.core.domain.model.GoodsReceiving
import com.ayakasir.app.core.domain.model.GoodsReceivingItem
import com.ayakasir.app.core.domain.model.Product
import com.ayakasir.app.core.domain.model.ProductType
import com.ayakasir.app.core.domain.model.Vendor
import com.ayakasir.app.core.util.UuidGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PurchasingViewModel @Inject constructor(
    private val vendorRepository: VendorRepository,
    private val purchasingRepository: PurchasingRepository,
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    data class VendorFormState(
        val name: String = "",
        val phone: String = "",
        val address: String = "",
        val isLoading: Boolean = false,
        val isSaved: Boolean = false,
        val error: String? = null
    )

    data class ReceivingFormState(
        val receivingId: String? = null,
        val vendorId: String? = null,
        val notes: String = "",
        val items: List<AddedItem> = emptyList(),
        val isLoading: Boolean = false,
        val isSaved: Boolean = false,
        val error: String? = null,
        val showAddVendor: Boolean = false,
        val newVendorName: String = ""
    )

    data class AddedItem(
        val productId: String,
        val variantId: String,
        val productName: String,
        val categoryName: String,
        val qty: Int,
        val costPerUnit: Long,
        val unit: String
    )

    data class CurrentItemInput(
        val categoryId: String = "",
        val productId: String = "",
        val variantId: String = "",
        val productName: String = "",
        val categoryName: String = "",
        val qty: String = "1",
        val totalPrice: String = "0",
        val unit: String = "pcs",
        val showAddCategory: Boolean = false,
        val newCategoryName: String = "",
        val showAddProduct: Boolean = false,
        val newProductName: String = ""
    )

    val vendors: StateFlow<List<Vendor>> = vendorRepository.getAllVendors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val receivingList: StateFlow<List<GoodsReceiving>> = purchasingRepository.getAllReceiving()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<Product>> = productRepository.getAllActiveRawMaterials()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = categoryRepository.getRawMaterialCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _vendorForm = MutableStateFlow(VendorFormState())
    val vendorForm: StateFlow<VendorFormState> = _vendorForm.asStateFlow()

    private val _receivingForm = MutableStateFlow(ReceivingFormState())
    val receivingForm: StateFlow<ReceivingFormState> = _receivingForm.asStateFlow()

    private val _currentItemInput = MutableStateFlow(CurrentItemInput())
    val currentItemInput: StateFlow<CurrentItemInput> = _currentItemInput.asStateFlow()

    fun loadVendor(vendorId: String) {
        viewModelScope.launch {
            val vendor = vendorRepository.getVendorById(vendorId) ?: return@launch
            _vendorForm.update { it.copy(name = vendor.name, phone = vendor.phone ?: "", address = vendor.address ?: "") }
        }
    }

    fun onVendorNameChange(value: String) { _vendorForm.update { it.copy(name = value) } }
    fun onVendorPhoneChange(value: String) { _vendorForm.update { it.copy(phone = value) } }
    fun onVendorAddressChange(value: String) { _vendorForm.update { it.copy(address = value) } }

    fun saveVendor(existingId: String?) {
        val form = _vendorForm.value
        if (form.name.isBlank()) {
            _vendorForm.update { it.copy(error = "Nama vendor wajib diisi") }
            return
        }
        viewModelScope.launch {
            _vendorForm.update { it.copy(isLoading = true) }
            try {
                if (existingId != null) {
                    vendorRepository.updateVendor(existingId, form.name, form.phone.ifBlank { null }, form.address.ifBlank { null })
                } else {
                    vendorRepository.createVendor(form.name, form.phone.ifBlank { null }, form.address.ifBlank { null })
                }
                _vendorForm.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _vendorForm.update { it.copy(isLoading = false, error = "Gagal menyimpan vendor") }
            }
        }
    }

    fun deleteVendor(vendorId: String) {
        viewModelScope.launch { vendorRepository.deleteVendor(vendorId) }
    }

    fun onReceivingVendorChange(vendorId: String) { _receivingForm.update { it.copy(vendorId = vendorId) } }
    fun onReceivingNotesChange(value: String) { _receivingForm.update { it.copy(notes = value) } }

    fun showAddVendorForm() { _receivingForm.update { it.copy(showAddVendor = true) } }
    fun hideAddVendorForm() { _receivingForm.update { it.copy(showAddVendor = false, newVendorName = "") } }
    fun onNewVendorNameChange(value: String) { _receivingForm.update { it.copy(newVendorName = value) } }

    fun createAndSelectVendor() {
        val name = _receivingForm.value.newVendorName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val vendor = vendorRepository.createVendor(name, null, null)
                _receivingForm.update { it.copy(vendorId = vendor.id, showAddVendor = false, newVendorName = "") }
            } catch (e: Exception) {
                _receivingForm.update { it.copy(error = "Gagal menambah vendor") }
            }
        }
    }

    // Current item input methods
    fun onCurrentItemCategoryChange(categoryId: String, categoryName: String) {
        _currentItemInput.update { it.copy(categoryId = categoryId, categoryName = categoryName, productId = "", variantId = "", productName = "") }
    }

    fun onCurrentItemProductChange(product: Product) {
        _currentItemInput.update { it.copy(productId = product.id, productName = product.name, categoryId = product.categoryId, variantId = "") }
    }

    fun onCurrentItemVariantChange(variantId: String) {
        _currentItemInput.update { it.copy(variantId = variantId) }
    }

    fun onCurrentItemQtyChange(value: String) {
        _currentItemInput.update { it.copy(qty = value) }
    }

    fun onCurrentItemTotalPriceChange(value: String) {
        _currentItemInput.update { it.copy(totalPrice = value) }
    }

    fun onCurrentItemUnitChange(value: String) {
        _currentItemInput.update { it.copy(unit = value) }
    }

    // Inline category creation
    fun showAddCategoryForm() { _currentItemInput.update { it.copy(showAddCategory = true) } }
    fun hideAddCategoryForm() { _currentItemInput.update { it.copy(showAddCategory = false, newCategoryName = "") } }
    fun onNewCategoryNameChange(value: String) { _currentItemInput.update { it.copy(newCategoryName = value) } }

    fun createAndSelectCategory() {
        val name = _currentItemInput.value.newCategoryName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val category = categoryRepository.createCategory(name, 0, CategoryType.RAW_MATERIAL)
                _currentItemInput.update {
                    it.copy(categoryId = category.id, categoryName = category.name, showAddCategory = false, newCategoryName = "", productId = "", variantId = "", productName = "")
                }
            } catch (e: Exception) {
                _receivingForm.update { it.copy(error = "Gagal menambah kategori") }
            }
        }
    }

    // Inline product creation
    fun showAddProductForm() { _currentItemInput.update { it.copy(showAddProduct = true) } }
    fun hideAddProductForm() { _currentItemInput.update { it.copy(showAddProduct = false, newProductName = "") } }
    fun onNewProductNameChange(value: String) { _currentItemInput.update { it.copy(newProductName = value) } }

    fun createAndSelectProduct() {
        val name = _currentItemInput.value.newProductName.trim()
        val categoryId = _currentItemInput.value.categoryId
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val product = productRepository.createProduct(name, categoryId.ifBlank { null }, null, 0, null, emptyList(), ProductType.RAW_MATERIAL)
                _currentItemInput.update {
                    it.copy(productId = product.id, productName = product.name, showAddProduct = false, newProductName = "", variantId = "")
                }
            } catch (e: Exception) {
                _receivingForm.update { it.copy(error = "Gagal menambah produk") }
            }
        }
    }

    fun addReceivingItem() {
        val input = _currentItemInput.value
        if (input.productId.isBlank()) {
            _receivingForm.update { it.copy(error = "Pilih produk terlebih dahulu") }
            return
        }
        val qty = input.qty.toIntOrNull() ?: 1
        val totalPrice = input.totalPrice.toLongOrNull() ?: 0
        val costPerUnit = if (qty > 0) totalPrice / qty else 0

        val addedItem = AddedItem(
            productId = input.productId,
            variantId = input.variantId,
            productName = input.productName,
            categoryName = input.categoryName,
            qty = qty,
            costPerUnit = costPerUnit,
            unit = input.unit
        )
        _receivingForm.update { it.copy(items = it.items + addedItem, error = null) }
        _currentItemInput.value = CurrentItemInput()
    }

    fun removeReceivingItem(index: Int) {
        _receivingForm.update { it.copy(items = it.items.toMutableList().also { list -> list.removeAt(index) }) }
    }

    fun saveReceiving() {
        val form = _receivingForm.value
        if (form.vendorId == null) {
            _receivingForm.update { it.copy(error = "Pilih vendor") }
            return
        }
        if (form.items.isEmpty()) {
            _receivingForm.update { it.copy(error = "Tambahkan minimal 1 item") }
            return
        }
        val items = form.items.map { input ->
            GoodsReceivingItem(
                id = UuidGenerator.generate(),
                receivingId = "",
                productId = input.productId,
                variantId = input.variantId,
                qty = input.qty,
                costPerUnit = input.costPerUnit,
                unit = input.unit
            )
        }
        viewModelScope.launch {
            _receivingForm.update { it.copy(isLoading = true) }
            try {
                if (form.receivingId != null) {
                    // Update existing receiving
                    purchasingRepository.updateReceiving(form.receivingId, form.vendorId, form.notes.ifBlank { null }, items)
                } else {
                    // Create new receiving
                    purchasingRepository.createReceiving(form.vendorId, form.notes.ifBlank { null }, items)
                }
                _receivingForm.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _receivingForm.update { it.copy(isLoading = false, error = "Gagal menyimpan penerimaan") }
            }
        }
    }

    fun loadReceiving(receivingId: String) {
        viewModelScope.launch {
            try {
                val receiving = purchasingRepository.getReceivingById(receivingId) ?: return@launch

                // Convert items to AddedItem format
                val addedItems = receiving.items.map { item ->
                    // Find the product to get category info
                    val product = products.value.find { it.id == item.productId }
                    val category = product?.let { p -> categories.value.find { it.id == p.categoryId } }
                    AddedItem(
                        productId = item.productId,
                        variantId = item.variantId,
                        productName = item.productName ?: "Unknown",
                        categoryName = category?.name ?: "",
                        qty = item.qty,
                        costPerUnit = item.costPerUnit,
                        unit = item.unit
                    )
                }

                _receivingForm.update {
                    it.copy(
                        receivingId = receivingId,
                        vendorId = receiving.vendorId,
                        notes = receiving.notes ?: "",
                        items = addedItems
                    )
                }
            } catch (e: Exception) {
                _receivingForm.update { it.copy(error = "Gagal memuat data penerimaan") }
            }
        }
    }

    fun deleteReceiving(receivingId: String) {
        viewModelScope.launch {
            try {
                purchasingRepository.deleteReceiving(receivingId)
            } catch (e: Exception) {
                // Handle error silently or show a snackbar
            }
        }
    }

    fun resetVendorForm() { _vendorForm.value = VendorFormState() }
    fun resetReceivingForm() {
        _receivingForm.value = ReceivingFormState()
        _currentItemInput.value = CurrentItemInput()
    }
}
