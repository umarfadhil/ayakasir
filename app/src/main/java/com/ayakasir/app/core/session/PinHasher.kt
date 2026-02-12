package com.ayakasir.app.core.session

import java.security.MessageDigest
import java.security.SecureRandom

object PinHasher {
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHexString()
    }

    fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = (salt + pin).toByteArray(Charsets.UTF_8)
        return digest.digest(input).toHexString()
    }

    fun verify(pin: String, salt: String, expectedHash: String): Boolean {
        return hash(pin, salt) == expectedHash
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }
}
