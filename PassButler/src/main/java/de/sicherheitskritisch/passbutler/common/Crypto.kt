package de.sicherheitskritisch.passbutler.common

import kotlinx.coroutines.runBlocking
import java.security.SecureRandom

object Crypto {

    fun deriveMasterKeyFromPassword(password: String, salt: String): List<Byte> {
        return listOf()
    }

    fun generateSymmetricKey(): List<Byte> {
        return listOf()
    }

    suspend fun generateRandomBytes(count: Int): List<Byte> {
        runBlocking {  }
        val secureRandomInstance = SecureRandom.getInstance("NativePRNGBlocking")

        return ByteArray(count).also {
            secureRandomInstance.nextBytes(it)
        }.asList()
    }
}
