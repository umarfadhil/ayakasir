package com.ayakasir.app.feature.product

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.data.repository.CategoryRepository
import com.ayakasir.app.core.data.repository.InventoryRepository
import com.ayakasir.app.core.data.repository.ProductComponentRepository
import com.ayakasir.app.core.data.repository.ProductRepository
import com.ayakasir.app.core.domain.model.Category
import com.ayakasir.app.core.domain.model.CategoryType
import com.ayakasir.app.core.domain.model.Product
import com.ayakasir.app.core.domain.model.ProductType
import com.ayakasir.app.core.session.SessionManager
import com.ayakasir.app.core.sync.SyncManager
import com.ayakasir.app.core.util.UuidGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductManagementViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val productComponentRepository: ProductComponentRepository,
    private val inventoryRepository: InventoryRepository,
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager
) : ViewModel() {
    companion object {
        private const val TAG = "ProductManagementVM"
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    data class ProductFormState(
        val name: String = "",
        val categoryId: String? = null,
        val description: String = "",
        val price: String = "",
        val productType: ProductType = ProductType.MENU_ITEM,
        val variants: List<VariantInput> = emptyList(),
        val components: List<ComponentInput> = emptyList(),
        val isLoading: Boolean = false,
        val isSaved: Boolean = false,
        val error: String? = null
    )

    data class VariantInput(
        val name: String = "",
        val priceAdjustment: String = "0"
    )

    data class ComponentInput(
        val id: String = UuidGenerator.generate(),
        val productId: String = "",
        val variantId: String = "",
        val qty: String = "",
        val unit: String = "pcs"
    )

    data class CategoryFormState(
        val name: String = "",
        val sortOrder: String = "0",
        val categoryType: CategoryType = CategoryType.MENU,
        val isLoading: Boolean = false,
        val isSaved: Boolean = false,
        val error: String? = null
    )

    val products: StateFlow<List<Product>> = productRepository.getAllActiveProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val menuCategories: StateFlow<List<Category>> = categoryRepository.getMenuCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawMaterialCategories: StateFlow<List<Category>> = categoryRepository.getRawMaterialCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Maps productId → base unit (e.g. "g", "mL", "pcs") from inventory. */
    val rawMaterialUnitMap: StateFlow<Map<String, String>> = inventoryRepository.getAllInventory()
        .map { items -> items.associate { it.productId to it.unit } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _productForm = MutableStateFlow(ProductFormState())
    val productForm: StateFlow<ProductFormState> = _productForm.asStateFlow()

    private val _categoryForm = MutableStateFlow(CategoryFormState())
    val categoryForm: StateFlow<CategoryFormState> = _categoryForm.asStateFlow()

    fun loadProduct(productId: String) {
        viewModelScope.launch {
            val product = productRepository.getProductById(productId) ?: return@launch

            // Fetch components for this product
            val componentList = productComponentRepository.getComponentsByProductId(productId).first()
            val components = componentList.map { comp ->
                ComponentInput(
                    id = comp.id,
                    productId = comp.componentProductId,
                    variantId = comp.componentVariantId,
                    qty = comp.requiredQty.toString(),
                    unit = comp.unit
                )
            }

            _productForm.update {
                it.copy(
                    name = product.name,
                    categoryId = product.categoryId,
                    description = product.description ?: "",
                    price = product.price.toString(),
                    productType = product.productType,
                    variants = product.variants.map { v ->
                        VariantInput(name = v.name, priceAdjustment = v.priceAdjustment.toString())
                    },
                    components = components
                )
            }
        }
    }

    fun loadCategory(categoryId: String) {
        viewModelScope.launch {
            val category = categoryRepository.getCategoryById(categoryId) ?: return@launch
            _categoryForm.update {
                it.copy(name = category.name, sortOrder = category.sortOrder.toString(), categoryType = category.categoryType)
            }
        }
    }

    fun onProductNameChange(value: String) { _productForm.update { it.copy(name = value) } }
    fun onProductCategoryChange(id: String?) { _productForm.update { it.copy(categoryId = id) } }
    fun onProductDescriptionChange(value: String) { _productForm.update { it.copy(description = value) } }
    fun onProductPriceChange(value: String) { _productForm.update { it.copy(price = value) } }
    fun onProductTypeChange(type: ProductType) { _productForm.update { it.copy(productType = type) } }

    fun addVariant() {
        _productForm.update { it.copy(variants = it.variants + VariantInput()) }
    }

    fun removeVariant(index: Int) {
        _productForm.update { it.copy(variants = it.variants.toMutableList().also { list -> list.removeAt(index) }) }
    }

    fun onVariantNameChange(index: Int, value: String) {
        _productForm.update {
            it.copy(variants = it.variants.toMutableList().also { list ->
                list[index] = list[index].copy(name = value)
            })
        }
    }

    fun onVariantPriceChange(index: Int, value: String) {
        _productForm.update {
            it.copy(variants = it.variants.toMutableList().also { list ->
                list[index] = list[index].copy(priceAdjustment = value)
            })
        }
    }

    fun addComponent() {
        _productForm.update { it.copy(components = it.components + ComponentInput()) }
    }

    fun removeComponent(index: Int) {
        _productForm.update {
            it.copy(components = it.components.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    fun updateComponent(index: Int, productId: String, variantId: String, qty: String, unit: String) {
        _productForm.update {
            val updated = it.components.toMutableList()
            updated[index] = updated[index].copy(
                productId = productId,
                variantId = variantId,
                qty = qty,
                unit = unit
            )
            it.copy(components = updated)
        }
    }

    /** Called when the user picks a different raw material product for a component row.
     *  Auto-fills the unit from inventory and resets variantId. */
    fun onComponentProductSelected(index: Int, productId: String) {
        val unit = rawMaterialUnitMap.value[productId] ?: "pcs"
        _productForm.update {
            val updated = it.components.toMutableList()
            updated[index] = updated[index].copy(
                productId = productId,
                variantId = "",
                unit = unit
            )
            it.copy(components = updated)
        }
    }

    fun saveProduct(existingId: String?) {
        val form = _productForm.value
        if (form.name.isBlank() || form.price.isBlank()) {
            _productForm.update { it.copy(error = "Nama dan harga wajib diisi") }
            return
        }
        val price = form.price.toLongOrNull() ?: run {
            _productForm.update { it.copy(error = "Harga tidak valid") }
            return
        }
        val variants = form.variants.filter { it.name.isNotBlank() }.map {
            it.name to (it.priceAdjustment.toLongOrNull() ?: 0L)
        }

        viewModelScope.launch {
            _productForm.update { it.copy(isLoading = true) }
            try {
                val productId = if (existingId != null) {
                    productRepository.updateProduct(existingId, form.name, form.categoryId, form.description.ifBlank { null }, price, null, variants, form.productType)
                    existingId
                } else {
                    val product = productRepository.createProduct(form.name, form.categoryId, form.description.ifBlank { null }, price, null, variants, form.productType)
                    product.id
                }

                // Save components
                productComponentRepository.deleteByProductId(productId)
                form.components.filter { it.productId.isNotBlank() }.forEach { comp ->
                    productComponentRepository.addComponent(
                        parentProductId = productId,
                        componentProductId = comp.productId,
                        componentVariantId = comp.variantId,
                        requiredQty = comp.qty.toIntOrNull() ?: 0,
                        unit = comp.unit
                    )
                }

                _productForm.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _productForm.update { it.copy(isLoading = false, error = "Gagal menyimpan produk") }
            }
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            productRepository.deleteProduct(productId)
        }
    }

    fun cloneProduct(productId: String) {
        viewModelScope.launch {
            try {
                val source = productRepository.getProductById(productId) ?: return@launch
                val sourceComponents = productComponentRepository.getComponentsByProductId(productId).first()

                val copiedProduct = productRepository.createProduct(
                    name = generateCopyName(source.name),
                    categoryId = source.categoryId.ifBlank { null },
                    description = source.description,
                    price = source.price,
                    imagePath = source.imagePath,
                    variantNames = source.variants.map { it.name to it.priceAdjustment },
                    productType = source.productType
                )

                sourceComponents.forEach { component ->
                    productComponentRepository.addComponent(
                        parentProductId = copiedProduct.id,
                        componentProductId = component.componentProductId,
                        componentVariantId = component.componentVariantId,
                        requiredQty = component.requiredQty,
                        unit = component.unit
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clone product: ${e.message}")
            }
        }
    }

    fun onCategoryNameChange(value: String) { _categoryForm.update { it.copy(name = value) } }
    fun onCategorySortOrderChange(value: String) { _categoryForm.update { it.copy(sortOrder = value) } }
    fun onCategoryTypeChange(type: CategoryType) { _categoryForm.update { it.copy(categoryType = type) } }

    fun saveCategory(existingId: String?) {
        val form = _categoryForm.value
        if (form.name.isBlank()) {
            _categoryForm.update { it.copy(error = "Nama kategori wajib diisi") }
            return
        }
        val sortOrder = form.sortOrder.toIntOrNull() ?: 0

        viewModelScope.launch {
            _categoryForm.update { it.copy(isLoading = true) }
            try {
                if (existingId != null) {
                    categoryRepository.updateCategory(existingId, form.name, sortOrder, form.categoryType)
                } else {
                    categoryRepository.createCategory(form.name, sortOrder, form.categoryType)
                }
                _categoryForm.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _categoryForm.update { it.copy(isLoading = false, error = "Gagal menyimpan kategori") }
            }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(categoryId)
        }
    }

    fun resetProductForm() { _productForm.value = ProductFormState() }
    fun resetCategoryForm() { _categoryForm.value = CategoryFormState() }

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

    private fun generateCopyName(originalName: String): String {
        val normalizedExistingNames = products.value
            .map { it.name.trim().lowercase() }
            .toSet()

        val baseName = "$originalName (Copy)"
        if (baseName.trim().lowercase() !in normalizedExistingNames) return baseName

        var index = 2
        while (true) {
            val candidate = "$originalName (Copy $index)"
            if (candidate.trim().lowercase() !in normalizedExistingNames) return candidate
            index++
        }
    }
}
