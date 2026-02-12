package com.ayakasir.app.core.sync

import javax.inject.Inject

class ConflictResolver @Inject constructor() {

    enum class Resolution { KEEP_LOCAL, KEEP_REMOTE, MERGE }

    fun resolve(
        tableName: String,
        localUpdatedAt: Long,
        remoteUpdatedAt: Long
    ): Resolution {
        return when (tableName) {
            "transactions", "transaction_items",
            "goods_receiving", "goods_receiving_items" -> {
                Resolution.KEEP_LOCAL
            }
            "inventory" -> {
                Resolution.MERGE
            }
            else -> {
                if (localUpdatedAt >= remoteUpdatedAt) {
                    Resolution.KEEP_LOCAL
                } else {
                    Resolution.KEEP_REMOTE
                }
            }
        }
    }
}
