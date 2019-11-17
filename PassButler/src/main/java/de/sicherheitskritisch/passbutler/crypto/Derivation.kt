package de.sicherheitskritisch.passbutler.crypto

import de.sicherheitskritisch.passbutler.base.bitSize
import de.sicherheitskritisch.passbutler.base.toHexString
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
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
    @Throws(DerivationFailedException::class)
    fun deriveLocalAuthenticationHash(username: String, password: String): String {
        return try {
            require(!username.isBlank()) { "The username must not be empty!" }
            require(!password.isBlank()) { "The password must not be empty!" }

            val preparedPassword = normalizeString(trimString(password))

            val preparedUsername = normalizeString(trimString(username))
            val salt = preparedUsername.toByteArray(Charsets.UTF_8)

            val resultingBytes = performPBKDFWithSHA256(preparedPassword, salt, MASTER_PASSWORD_AUTHENTICATION_HASH_ITERATION_COUNT, MASTER_PASSWORD_AUTHENTICATION_HASH_BIT_LENGTH)
            resultingBytes.toHexString()
        } catch (exception: Exception) {
            throw DerivationFailedException(exception)
        }
    }

    /**
     * Derives a authentication hash based on a given password using PBKDF2 with SHA-256.
     * This method re-implements `werkzeug.security.generate_password_hash` from Python Werkzeug framework.
     */
    @Throws(DerivationFailedException::class)
    fun deriveServerAuthenticationHash(password: String): String {
        return try {
            require(!password.isBlank()) { "The password must not be empty!" }

            val saltString = RandomGenerator.generateRandomString(SERVER_AUTHENTICATION_HASH_SALT_LENGTH, SERVER_AUTHENTICATION_HASH_SALT_VALID_CHARACTERS)
            val saltBytes = saltString.toByteArray(Charsets.UTF_8)

            val iterationCount = SERVER_AUTHENTICATION_HASH_ITERATION_COUNT
            val hashBytes = performPBKDFWithSHA256(password, saltBytes, iterationCount, SERVER_AUTHENTICATION_HASH_BIT_LENGTH)
            val hashString = hashBytes.toHexString()

            "pbkdf2:sha256:$iterationCount\$$saltString\$$hashString"
        } catch (exception: Exception) {
            throw DerivationFailedException(exception)
        }
    }

    /**
     * Derives the symmetric master key from a password using PBKDF2 with SHA-256.
     */
    @Throws(DerivationFailedException::class)
    fun deriveMasterKey(password: String, keyDerivationInformation: KeyDerivationInformation): ByteArray {
        return try {
            require(!password.isBlank()) { "The password must not be empty!" }

            // The salt should have the same size as the derived key
            require(keyDerivationInformation.salt.bitSize == MASTER_KEY_BIT_LENGTH) { "The salt must be 256 bits long!" }

            val preparedPassword = normalizeString(trimString(password))
            performPBKDFWithSHA256(preparedPassword, keyDerivationInformation.salt, keyDerivationInformation.iterationCount, MASTER_KEY_BIT_LENGTH)
        } catch (exception: Exception) {
            throw DerivationFailedException(exception)
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

    class DerivationFailedException(cause: Exception? = null) : Exception(cause)
}