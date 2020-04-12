package de.passbutler.common.crypto

import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.bitSize
import de.passbutler.common.base.toHexString
import de.passbutler.common.crypto.models.KeyDerivationInformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.NoSuchAlgorithmException
import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

const val MASTER_KEY_ITERATION_COUNT = 100_000
const val MASTER_KEY_BIT_LENGTH = 256

const val MASTER_PASSWORD_AUTHENTICATION_HASH_ITERATION_COUNT = 100_001
const val MASTER_PASSWORD_AUTHENTICATION_HASH_BIT_LENGTH = 256

const val SERVER_AUTHENTICATION_HASH_SALT_LENGTH = 8
const val SERVER_AUTHENTICATION_HASH_SALT_VALID_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
const val SERVER_AUTHENTICATION_HASH_ITERATION_COUNT = 150_000
const val SERVER_AUTHENTICATION_HASH_BIT_LENGTH = 256

object Derivation {

    /**
     * Derives a authentication hash based on given username/password using PBKDF2 with SHA-256.
     * This method is used to avoid sending master password from client to server in clear text.
     */
    @Throws(IllegalArgumentException::class)
    suspend fun deriveLocalAuthenticationHash(username: String, password: String): Result<String> {
        require(!username.isBlank()) { "The username must not be empty!" }
        require(!password.isBlank()) { "The password must not be empty!" }

        return withContext(Dispatchers.Default) {
            try {
                val preparedPassword = normalizeString(trimString(password))

                val preparedUsername = normalizeString(trimString(username))
                val salt = preparedUsername.toByteArray(Charsets.UTF_8)

                val resultingBytes = performPBKDFWithSHA256(
                    preparedPassword,
                    salt,
                    MASTER_PASSWORD_AUTHENTICATION_HASH_ITERATION_COUNT,
                    MASTER_PASSWORD_AUTHENTICATION_HASH_BIT_LENGTH
                )
                Success(resultingBytes.toHexString())
            } catch (exception: Exception) {
                Failure(exception)
            }
        }
    }

    /**
     * Derives a authentication hash based on a given password using PBKDF2 with SHA-256.
     * This method re-implements `werkzeug.security.generate_password_hash` from Python Werkzeug framework.
     */
    @Throws(IllegalArgumentException::class)
    suspend fun deriveServerAuthenticationHash(password: String): Result<String> {
        require(!password.isBlank()) { "The password must not be empty!" }

        return withContext(Dispatchers.Default) {
            try {
                val saltString = RandomGenerator.generateRandomString(
                    SERVER_AUTHENTICATION_HASH_SALT_LENGTH,
                    SERVER_AUTHENTICATION_HASH_SALT_VALID_CHARACTERS
                )
                val saltBytes = saltString.toByteArray(Charsets.UTF_8)

                val iterationCount = SERVER_AUTHENTICATION_HASH_ITERATION_COUNT
                val hashBytes = performPBKDFWithSHA256(
                    password,
                    saltBytes,
                    iterationCount,
                    SERVER_AUTHENTICATION_HASH_BIT_LENGTH
                )
                val hashString = hashBytes.toHexString()
                val hashStringWithMetaInformation = "pbkdf2:sha256:$iterationCount\$$saltString\$$hashString"

                Success(hashStringWithMetaInformation)
            } catch (exception: Exception) {
                Failure(exception)
            }
        }
    }

    /**
     * Derives the symmetric master key from a password using PBKDF2 with SHA-256.
     */
    @Throws(IllegalArgumentException::class)
    suspend fun deriveMasterKey(password: String, keyDerivationInformation: KeyDerivationInformation): Result<ByteArray> {
        require(!password.isBlank()) { "The password must not be empty!" }

        // The salt should have the same size as the derived key
        require(keyDerivationInformation.salt.bitSize == MASTER_KEY_BIT_LENGTH) { "The salt must be 256 bits long!" }

        return withContext(Dispatchers.Default) {
            try {
                val preparedPassword = normalizeString(trimString(password))
                val hashBytes = performPBKDFWithSHA256(
                    preparedPassword,
                    keyDerivationInformation.salt,
                    keyDerivationInformation.iterationCount,
                    MASTER_KEY_BIT_LENGTH
                )

                Success(hashBytes)
            } catch (exception: Exception) {
                Failure(exception)
            }
        }
    }

    @Throws(NoSuchAlgorithmException::class)
    private fun performPBKDFWithSHA256(password: String, salt: ByteArray, iterationCount: Int, resultLength: Int): ByteArray {
        val pbeKeySpec = PBEKeySpec(password.toCharArray(), salt, iterationCount, resultLength)
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2withHmacSHA256")
        val secretKeyBytes = secretKeyFactory.generateSecret(pbeKeySpec).encoded
        return secretKeyBytes
    }

    /**
     * Removes leading and trailing spaces from input string (whitespace characters at
     * the start/end should be avoided because they may not be visible to the user).
     */
    private fun trimString(input: String): String {
        return input.trim()
    }

    /**
     * Ensures a non-ASCII string (Unicode) is converted to a common representation, to avoid
     * the same input is encoded/interpreted different on multiple platforms.
     */
    private fun normalizeString(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFKD)
    }
}