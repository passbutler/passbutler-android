package de.sicherheitskritisch.passbutler.crypto

import java.security.SecureRandom

object RandomGenerator {
    fun generateRandomBytes(count: Int): ByteArray {
        // Use default `SecureRandom` constructor that uses `/dev/urandom` that is sufficient secure and does not block
        return SecureRandom().let { nonBlockingSecureRandomInstance ->
            val randomBytesArray = ByteArray(count)
            nonBlockingSecureRandomInstance.nextBytes(randomBytesArray)

            randomBytesArray
        }
    }
}