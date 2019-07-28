package de.sicherheitskritisch.passbutler.crypto

import java.security.SecureRandom

object RandomGenerator {

    /**
     * Generates a desired amount of random bytes.
     */
    fun generateRandomBytes(count: Int): ByteArray {
        return createRandomInstance().let { nonBlockingSecureRandomInstance ->
            val randomBytesArray = ByteArray(count)
            nonBlockingSecureRandomInstance.nextBytes(randomBytesArray)

            randomBytesArray
        }
    }

    /**
     * Generates a random string with desired length containing of given allowed characters.
     */
    fun generateRandomString(length: Int, allowedCharacters: String): String {
        val allowedCharactersLength = allowedCharacters.length

        if (allowedCharactersLength == 0) {
            throw IllegalArgumentException("The allowed characters string must not be empty!")
        }

        return createRandomInstance().let { nonBlockingSecureRandomInstance ->
            (1..length)
                .map { nonBlockingSecureRandomInstance.nextInt(allowedCharactersLength) }
                .map(allowedCharacters::get)
                .joinToString("")
        }
    }

    /**
     * Using the default `SecureRandom` constructor that uses `/dev/urandom` that is sufficient secure and does not block.
     */
    fun createRandomInstance() = SecureRandom()
}