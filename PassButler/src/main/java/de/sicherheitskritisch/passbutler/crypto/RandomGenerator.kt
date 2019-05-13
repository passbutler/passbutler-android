package de.sicherheitskritisch.passbutler.crypto

import java.security.SecureRandom

object RandomGenerator {

    /**
     * Generates a desired amount of random bytes. It uses the default `SecureRandom` constructor
     * that uses `/dev/urandom` that is sufficient secure and does not block.
     */
    fun generateRandomBytes(count: Int): ByteArray {
        return SecureRandom().let { nonBlockingSecureRandomInstance ->
            val randomBytesArray = ByteArray(count)
            nonBlockingSecureRandomInstance.nextBytes(randomBytesArray)

            randomBytesArray
        }
    }
}