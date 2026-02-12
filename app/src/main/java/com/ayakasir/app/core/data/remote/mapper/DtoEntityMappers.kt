package com.ayakasir.app.core.data.remote.mapper

import com.ayakasir.app.core.data.local.entity.*
import com.ayakasir.app.core.data.remote.dto.*

// Entity -> DTO (for upload to Supabase)

fun UserEntity.toDto() = UserDto(
    id = id,
    name = name,
    pinHash = pinHash,
    pinSalt = pinSalt,
    role = role,
    featureAccess = featureAccess,
    isActive = isActive,
    synced = synced,
    updatedAt = updatedAt,
    createdAt = createdAt
)

fun CategoryEntity.toDto() = CategoryDto(
    id = id,
    name = name,
    sortOrder = sortOrder,
    categoryType = categoryType,
    synced = synced,
    updatedAt = updatedAt
)

fun ProductEntity.toDto() = ProductDto(
    id = id,
    categoryId = categoryId,
    name = name,
    description = description,
    price = price,
    imagePath = imagePath,
    isActive = isActive,
    productType = productType,
    synced = synced,
    updatedAt = updatedAt
)

fun VariantEntity.toDto() = VariantDto(
    id = id,
    productId = productId,
    name = name,
    priceAdjustment = priceAdjustment,
    synced = synced,
    updatedAt = updatedAt
)

fun VendorEntity.toDto() = VendorDto(
    id = id,
    name = name,
    phone = phone,
    address = address,
    synced = synced,
    updatedAt = updatedAt
)

fun InventoryEntity.toDto() = InventoryDto(
    productId = productId,
    variantId = variantId,
    currentQty = currentQty,
    minQty = minQty,
    synced = synced,
    updatedAt = updatedAt
)

fun GoodsReceivingEntity.toDto() = GoodsReceivingDto(
    id = id,
    vendorId = vendorId,
    date = date,
    notes = notes,
    synced = synced,
    updatedAt = updatedAt
)

fun GoodsReceivingItemEntity.toDto() = GoodsReceivingItemDto(
    id = id,
    receivingId = receivingId,
    productId = productId,
    variantId = variantId,
    qty = qty,
    costPerUnit = costPerUnit,
    unit = unit,
    synced = synced,
    updatedAt = updatedAt
)

fun TransactionEntity.toDto() = TransactionDto(
    id = id,
    userId = userId,
    date = date,
    total = total,
    paymentMethod = paymentMethod,
    status = status,
    synced = synced,
    updatedAt = updatedAt
)

fun TransactionItemEntity.toDto() = TransactionItemDto(
    id = id,
    transactionId = transactionId,
    productId = productId,
    variantId = variantId,
    productName = productName,
    variantName = variantName,
    qty = qty,
    unitPrice = unitPrice,
    subtotal = subtotal,
    synced = synced,
    updatedAt = updatedAt
)

// DTO -> Entity (for download from Supabase)

fun UserDto.toEntity() = UserEntity(
    id = id,
    name = name,
    pinHash = pinHash,
    pinSalt = pinSalt,
    role = role,
    featureAccess = featureAccess,
    isActive = isActive,
    synced = synced,
    updatedAt = updatedAt,
    createdAt = createdAt
)

fun CategoryDto.toEntity() = CategoryEntity(
    id = id,
    name = name,
    sortOrder = sortOrder,
    categoryType = categoryType ?: "MENU",
    synced = synced,
    updatedAt = updatedAt
)

fun ProductDto.toEntity() = ProductEntity(
    id = id,
    categoryId = categoryId,
    name = name,
    description = description,
    price = price,
    imagePath = imagePath,
    isActive = isActive,
    productType = productType ?: "MENU_ITEM",
    synced = synced,
    updatedAt = updatedAt
)

fun VariantDto.toEntity() = VariantEntity(
    id = id,
    productId = productId,
    name = name,
    priceAdjustment = priceAdjustment,
    synced = synced,
    updatedAt = updatedAt
)

fun VendorDto.toEntity() = VendorEntity(
    id = id,
    name = name,
    phone = phone,
    address = address,
    synced = synced,
    updatedAt = updatedAt
)

fun InventoryDto.toEntity() = InventoryEntity(
    productId = productId,
    variantId = variantId,
    currentQty = currentQty,
    minQty = minQty,
    synced = synced,
    updatedAt = updatedAt
)

fun GoodsReceivingDto.toEntity() = GoodsReceivingEntity(
    id = id,
    vendorId = vendorId,
    date = date,
    notes = notes,
    synced = synced,
    updatedAt = updatedAt
)

fun GoodsReceivingItemDto.toEntity() = GoodsReceivingItemEntity(
    id = id,
    receivingId = receivingId,
    productId = productId,
    variantId = variantId,
    qty = qty,
    costPerUnit = costPerUnit,
    unit = unit,
    synced = synced,
    updatedAt = updatedAt
)

fun TransactionDto.toEntity() = TransactionEntity(
    id = id,
    userId = userId,
    date = date,
    total = total,
    paymentMethod = paymentMethod,
    status = status,
    synced = synced,
    updatedAt = updatedAt
)

fun TransactionItemDto.toEntity() = TransactionItemEntity(
    id = id,
    transactionId = transactionId,
    productId = productId,
    variantId = variantId,
    productName = productName,
    variantName = variantName,
    qty = qty,
    unitPrice = unitPrice,
    subtotal = subtotal,
    synced = synced,
    updatedAt = updatedAt
)

fun ProductComponentEntity.toDto() = ProductComponentDto(
    id = id,
    parentProductId = parentProductId,
    componentProductId = componentProductId,
    componentVariantId = componentVariantId,
    requiredQty = requiredQty,
    unit = unit,
    sortOrder = sortOrder,
    synced = synced,
    updatedAt = updatedAt
)

fun ProductComponentDto.toEntity() = ProductComponentEntity(
    id = id,
    parentProductId = parentProductId,
    componentProductId = componentProductId,
    componentVariantId = componentVariantId,
    requiredQty = requiredQty,
    unit = unit,
    sortOrder = sortOrder,
    synced = synced,
    updatedAt = updatedAt
)

fun CashWithdrawalEntity.toDto() = CashWithdrawalDto(
    id = id,
    userId = userId,
    amount = amount,
    reason = reason,
    date = date,
    synced = synced,
    updatedAt = updatedAt
)

fun CashWithdrawalDto.toEntity() = CashWithdrawalEntity(
    id = id,
    userId = userId,
    amount = amount,
    reason = reason,
    date = date,
    synced = synced,
    updatedAt = updatedAt
)
