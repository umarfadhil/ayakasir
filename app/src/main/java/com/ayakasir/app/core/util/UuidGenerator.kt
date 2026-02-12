package com.ayakasir.app.core.util

import java.util.UUID

object UuidGenerator {
    fun generate(): String = UUID.randomUUID().toString()
}
