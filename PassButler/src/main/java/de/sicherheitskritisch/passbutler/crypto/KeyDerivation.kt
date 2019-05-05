package de.sicherheitskritisch.passbutler.crypto

import de.sicherheitskritisch.passbutler.base.bitSize
import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object KeyDerivation {

    private const val AES_KEY_LENGTH = 256

    /**
     * Derives a cryptographic AES-256 key from a password using PBKDF2 with SHA-1.
     */
    @Throws(IllegalArgumentException::class)
    fun deriveAES256KeyFromPassword(password: String, salt: ByteArray, iterationCount: Int): ByteArray {
        if (password.isBlank()) {
            throw IllegalArgumentException("The password must not be empty!")
        }

        // The salt must have the same size as the derived key
        if (salt.bitSize != AES_KEY_LENGTH) {
            throw IllegalArgumentException("The salt must be 256 bits long!")
        }

        val preparedPassword = normalizePassword(trimPassword(password))

        val pbeKeySpec = PBEKeySpec(preparedPassword.toCharArray(), salt, iterationCount, AES_KEY_LENGTH)
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val secretKeyBytes = secretKeyFactory.generateSecret(pbeKeySpec).encoded

        return secretKeyBytes
    }

    /**
     * Removes leading and trailing spaces from password string (whitespace characters at the start/end
     * should be avoided because they may not be visible to the user).
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
}