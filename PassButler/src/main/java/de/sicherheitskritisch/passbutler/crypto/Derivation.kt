package de.sicherheitskritisch.passbutler.crypto

import de.sicherheitskritisch.passbutler.base.bitSize
import de.sicherheitskritisch.passbutler.base.toHexString
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

const val MASTER_KEY_ITERATION_COUNT = 100_000
const val MASTER_KEY_BIT_LENGTH = 256

const val MASTER_PASSWORD_AUTHENTICATION_HASH_ITERATION_COUNT = 100_001
const val MASTER_PASSWORD_AUTHENTICATION_HASH_BIT_LENGTH = 256

object Derivation {

    /**
     * Derives a authentication hash based on username/password using PBKDF2 with SHA-256 used to avoid sending password in clear text.
     */
    @Throws(IllegalArgumentException::class, DerivationFailedException::class)
    fun deriveLocalAuthenticationHash(username: String, password: String): String {
        if (username.isBlank()) {
            throw IllegalArgumentException("The username must not be empty!")
        }

        if (password.isBlank()) {
            throw IllegalArgumentException("The password must not be empty!")
        }

        return try {
            val preparedPassword = normalizeString(trimString(password))

            val preparedUsername = normalizeString(trimString(username))
            val salt = preparedUsername.toByteArray(Charsets.UTF_8)

            val resultingBytes = performPBKDFWithSHA256(preparedPassword, salt, MASTER_PASSWORD_AUTHENTICATION_HASH_ITERATION_COUNT, MASTER_PASSWORD_AUTHENTICATION_HASH_BIT_LENGTH)
            resultingBytes.toHexString()
        } catch (e: Exception) {
            throw DerivationFailedException(e)
        }
    }

    /**
     * Derives the symmetric master key from a password using PBKDF2 with SHA-256.
     */
    @Throws(IllegalArgumentException::class, DerivationFailedException::class)
    fun deriveMasterKey(password: String, keyDerivationInformation: KeyDerivationInformation): ByteArray {
        if (password.isBlank()) {
            throw IllegalArgumentException("The password must not be empty!")
        }

        // The salt should have the same size as the derived key
        if (keyDerivationInformation.salt.bitSize != MASTER_KEY_BIT_LENGTH) {
            throw IllegalArgumentException("The salt must be 256 bits long!")
        }

        return try {
            val preparedPassword = normalizeString(trimString(password))
            performPBKDFWithSHA256(preparedPassword, keyDerivationInformation.salt, keyDerivationInformation.iterationCount, MASTER_KEY_BIT_LENGTH)
        } catch (e: Exception) {
            throw DerivationFailedException(e)
        }
    }

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