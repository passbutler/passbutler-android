package de.sicherheitskritisch.passbutler.crypto

import de.sicherheitskritisch.passbutler.base.putString
import org.json.JSONException
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

sealed class Algorithm(val stringRepresentation: String) {

    abstract fun generateInitializationVector(): ByteArray
    abstract fun encrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray
    abstract fun decrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray

    object AES256GCM : Algorithm("AES-256-GCM") {

        private const val AES_KEY_LENGTH = 256
        private const val GCM_INITIALIZATION_VECTOR_LENGTH = 96
        private const val GCM_AUTHENTICATION_TAG_LENGTH = 128

        override fun generateInitializationVector(): ByteArray {
            // Use default `SecureRandom` constructor that uses `/dev/urandom` that is sufficient secure and does not block
            return SecureRandom().let { nonBlockingSecureRandomInstance ->
                val bytesCount = GCM_INITIALIZATION_VECTOR_LENGTH.byteSize
                val newInitializationVector = ByteArray(bytesCount)
                nonBlockingSecureRandomInstance.nextBytes(newInitializationVector)

                newInitializationVector
            }
        }

        @Throws(EncryptionFailedException::class)
        override fun encrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray {
            return try {
                if (initializationVector.bitSize != GCM_INITIALIZATION_VECTOR_LENGTH) {
                    throw IllegalArgumentException("The initialization vector must be 96 bits long!")
                }

                if (encryptionKey.bitSize != AES_KEY_LENGTH) {
                    throw IllegalArgumentException("The encryption key must be 256 bits long!")
                }

                val secretKeySpec = SecretKeySpec(encryptionKey, "AES")
                val gcmParameterSpec = GCMParameterSpec(GCM_AUTHENTICATION_TAG_LENGTH, initializationVector)

                // The GCM is no classic block mode and thus has no padding
                val encryptCipherInstance = Cipher.getInstance("AES/GCM/NoPadding")
                encryptCipherInstance.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec)

                val encryptedData = encryptCipherInstance.doFinal(data)

                encryptedData
            } catch (e: Exception) {
                throw EncryptionFailedException(e)
            }
        }

        @Throws(DecryptionFailedException::class)
        override fun decrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray {
            return try {
                if (initializationVector.bitSize != GCM_INITIALIZATION_VECTOR_LENGTH) {
                    throw IllegalArgumentException("The initialization vector must be 96 bits long!")
                }

                if (encryptionKey.bitSize != AES_KEY_LENGTH) {
                    throw IllegalArgumentException("The encryption key must be 256 bits long!")
                }

                val secretKeySpec = SecretKeySpec(encryptionKey, "AES")
                val gcmParameterSpec = GCMParameterSpec(GCM_AUTHENTICATION_TAG_LENGTH, initializationVector)

                // The GCM is no classic block mode and thus has no padding
                val encryptCipherInstance = Cipher.getInstance("AES/GCM/NoPadding")
                encryptCipherInstance.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParameterSpec)

                val decryptedData = encryptCipherInstance.doFinal(data)

                decryptedData
            } catch (e: Exception) {
                throw DecryptionFailedException(e)
            }
        }
    }

    class EncryptionFailedException(cause: Exception? = null) : Exception(cause)
    class DecryptionFailedException(cause: Exception? = null) : Exception(cause)
}

/**
 * Convenience method to put a `Algorithm` value to `JSONObject`.
 */
@Throws(JSONException::class)
fun JSONObject.putAlgorithm(name: String, value: Algorithm): JSONObject {
    val algorithmStringRepresentation = value.stringRepresentation
    return putString(name, algorithmStringRepresentation)
}

/**
 * Convenience method to get a `Algorithm` value from `JSONObject`.
 */
@Throws(JSONException::class)
fun JSONObject.getAlgorithm(name: String): Algorithm {
    return when (val algorithmStringRepresentation = getString(name)) {
        Algorithm.AES256GCM.stringRepresentation -> Algorithm.AES256GCM
        else -> throw JSONException("The algorithm string representation '$algorithmStringRepresentation' could not be found!")
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

private val ByteArray.bitSize: Int
    get() = size * 8

private val Int.byteSize: Int
    get() = this / 8