package de.sicherheitskritisch.passbutler.crypto

import de.sicherheitskritisch.passbutler.base.L
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

sealed class Algorithm(val stringRepresentation: String) {

    abstract fun generateInitializationVector(): ByteArray
    abstract fun encrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray?
    abstract fun decrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray?

    object AES256GCM : Algorithm("AES-256-GCM") {

        private const val AES_KEY_LENGTH = 256
        private const val GCM_INITIALIZATION_VECTOR_LENGTH = 96
        private const val GCM_AUTHENTICATION_TAG_LENGTH = 128

        override fun generateInitializationVector(): ByteArray {
            /*
            return withContext(Dispatchers.IO) {
                val blockingSecureRandomInstance = SecureRandom.getInstanceStrong()
                val bytesCount = GCM_INITIALIZATION_VECTOR_LENGTH / 8

                ByteArray(bytesCount).also {
                    blockingSecureRandomInstance.nextBytes(it)
                }
            }
            */

            // TODO: Implement
            return ByteArray(0)
        }

        override fun encrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray? {
            return try {
                if (initializationVector.size * 8 != GCM_INITIALIZATION_VECTOR_LENGTH) {
                    throw IllegalArgumentException("The initialization vector must be 96 bits long!")
                }

                if (encryptionKey.size * 8 != AES_KEY_LENGTH) {
                    throw IllegalArgumentException("The encryption key must be 256 bits long!")
                }

                val secretKey = SecretKeySpec(encryptionKey, "AES")
                val gcmParameterSpec = GCMParameterSpec(GCM_AUTHENTICATION_TAG_LENGTH, initializationVector)

                // The GCM is no classic block mode and thus has no padding
                val encryptCipherInstance = Cipher.getInstance("AES/GCM/NoPadding")
                encryptCipherInstance.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec)

                val encryptedData = encryptCipherInstance.doFinal(data)

                encryptedData
            } catch (e: Exception) {
                L.w("Algorithm.AES256GCM", "encrypt(): The value could not be encrypted!", e)
                null
            }
        }

        override fun decrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray? {
            // TODO: Implement
            return ByteArray(0)
        }
    }
}

/**
 * Retrieves `Algorithm` instance by the string representation. Similar to `Enum.valueOf(String)` method.
 */
fun String.toAlgorithm(): Algorithm? {
    return when (this) {
        Algorithm.AES256GCM.stringRepresentation -> Algorithm.AES256GCM
        else -> null
    }
}

/**
 * Clears out a `ByteArray` for security reasons (for crypto keys etc.).
 */
fun ByteArray.clear() {
    this.forEachIndexed { index, _ ->
        this[index] = 0
    }
}
