package de.sicherheitskritisch.passbutler.crypto

import de.sicherheitskritisch.passbutler.base.bitSize
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Derivation {

    private const val SYMMETRIC_KEY_LENGTH = 256

    /**
     * Derives a cryptographic symmetric key from a password using PBKDF2 with SHA-256.
     */
    @Throws(IllegalArgumentException::class, KeyDerivationFailedException::class)
    fun deriveSymmetricKey(password: String, keyDerivationInformation: KeyDerivationInformation): ByteArray {
        if (password.isBlank()) {
            throw IllegalArgumentException("The password must not be empty!")
        }

        // The salt should have the same size as the derived key
        if (keyDerivationInformation.salt.bitSize != SYMMETRIC_KEY_LENGTH) {
            throw IllegalArgumentException("The salt must be 256 bits long!")
        }

        return try {
            val preparedPassword = normalizePassword(trimPassword(password))

            val pbeKeySpec = PBEKeySpec(preparedPassword.toCharArray(), keyDerivationInformation.salt, keyDerivationInformation.iterationCount, SYMMETRIC_KEY_LENGTH)
            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2withHmacSHA256")
            val secretKeyBytes = secretKeyFactory.generateSecret(pbeKeySpec).encoded

            secretKeyBytes
        } catch (e: Exception) {
            throw KeyDerivationFailedException(e)
        }
    }

    /**
     * Removes leading and trailing spaces from password string (whitespace characters at
     * the start/end should be avoided because they may not be visible to the user).
     */
    private fun trimPassword(password: String): String {
        return password.trim()
    }

    /**
     * Ensures a non-ASCII string (Unicode) is converted to a common representation, to avoid
     * the same password is encoded/interpreted different on multiple platforms.
     */
    private fun normalizePassword(password: String): String {
        return Normalizer.normalize(password, Normalizer.Form.NFKD)
    }

    class KeyDerivationFailedException(cause: Exception? = null) : Exception(cause)
}