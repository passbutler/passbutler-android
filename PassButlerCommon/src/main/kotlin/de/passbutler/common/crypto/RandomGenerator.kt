package de.passbutler.common.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

object RandomGenerator {

    /**
     * Generates a desired amount of random bytes.
     */
    suspend fun generateRandomBytes(count: Int): ByteArray {
        return withContext(Dispatchers.IO) {
            createRandomInstance().let { nonBlockingSecureRandomInstance ->
                val randomBytesArray = ByteArray(count)
                nonBlockingSecureRandomInstance.nextBytes(randomBytesArray)

                randomBytesArray
            }
        }
    }

    /**
     * Generates a random string with desired length containing of given allowed characters.
     */
    @Throws(IllegalArgumentException::class)
    suspend fun generateRandomString(length: Int, allowedCharacters: String): String {
        val allowedCharactersLength = allowedCharacters.length
        require(allowedCharactersLength != 0) { "The allowed characters string must not be empty!" }

        return withContext(Dispatchers.IO) {
            createRandomInstance().let { nonBlockingSecureRandomInstance ->
                (1..length)
                    .map { nonBlockingSecureRandomInstance.nextInt(allowedCharactersLength) }
                    .map(allowedCharacters::get)
                    .joinToString("")
            }
        }
    }

    /**
     * Using the default `SecureRandom` constructor that uses `/dev/urandom` that is sufficient secure and does not block.
     */
    fun createRandomInstance() = SecureRandom()
}