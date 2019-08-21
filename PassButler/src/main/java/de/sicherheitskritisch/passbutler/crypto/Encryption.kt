package de.sicherheitskritisch.passbutler.crypto

import de.sicherheitskritisch.passbutler.base.bitSize
import de.sicherheitskritisch.passbutler.base.byteSize
import de.sicherheitskritisch.passbutler.base.putString
import org.json.JSONException
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

sealed class EncryptionAlgorithm(val stringRepresentation: String) {

    override fun toString(): String {
        return stringRepresentation
    }

    sealed class Symmetric(stringRepresentation: String) : EncryptionAlgorithm(stringRepresentation) {

        abstract fun generateEncryptionKey(): ByteArray
        abstract fun generateInitializationVector(): ByteArray
        abstract fun encrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray
        abstract fun decrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray

        object AES256GCM : EncryptionAlgorithm.Symmetric("AES-256-GCM") {

            private const val AES_KEY_BIT_SIZE = 256
            private const val GCM_INITIALIZATION_VECTOR_BIT_SIZE = 96
            private const val GCM_AUTHENTICATION_TAG_BIT_SIZE = 128

            @Throws(GenerateEncryptionKeyFailedException::class)
            override fun generateEncryptionKey(): ByteArray {
                return try {
                    val keyGenerator = KeyGenerator.getInstance("AES")
                    keyGenerator.init(AES_KEY_BIT_SIZE)

                    val secretKey = keyGenerator.generateKey()
                    secretKey.encoded
                } catch (e: Exception) {
                    throw GenerateEncryptionKeyFailedException(e)
                }
            }

            override fun generateInitializationVector(): ByteArray {
                val bytesCount = GCM_INITIALIZATION_VECTOR_BIT_SIZE.byteSize
                return RandomGenerator.generateRandomBytes(bytesCount)
            }

            @Throws(EncryptionFailedException::class)
            override fun encrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray {
                return try {
                    if (initializationVector.bitSize != GCM_INITIALIZATION_VECTOR_BIT_SIZE) {
                        throw IllegalArgumentException("The initialization vector must be $GCM_INITIALIZATION_VECTOR_BIT_SIZE bits long!")
                    }

                    if (encryptionKey.bitSize != AES_KEY_BIT_SIZE) {
                        throw IllegalArgumentException("The encryption key must be $AES_KEY_BIT_SIZE bits long!")
                    }

                    val secretKeySpec = SecretKeySpec(encryptionKey, "AES")
                    val gcmParameterSpec = GCMParameterSpec(GCM_AUTHENTICATION_TAG_BIT_SIZE, initializationVector)

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
                    if (initializationVector.bitSize != GCM_INITIALIZATION_VECTOR_BIT_SIZE) {
                        throw IllegalArgumentException("The initialization vector must be $GCM_INITIALIZATION_VECTOR_BIT_SIZE bits long!")
                    }

                    if (encryptionKey.bitSize != AES_KEY_BIT_SIZE) {
                        throw IllegalArgumentException("The encryption key must be $AES_KEY_BIT_SIZE bits long!")
                    }

                    val secretKeySpec = SecretKeySpec(encryptionKey, "AES")
                    val gcmParameterSpec = GCMParameterSpec(GCM_AUTHENTICATION_TAG_BIT_SIZE, initializationVector)

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
    }

    class GenerateEncryptionKeyFailedException(cause: Exception? = null) : Exception(cause)
    class EncryptionFailedException(cause: Exception? = null) : Exception(cause)
    class DecryptionFailedException(cause: Exception? = null) : Exception(cause)
}

/**
 * Convenience method to put a `EncryptionAlgorithm.Symmetric` value to `JSONObject`.
 */
@Throws(JSONException::class)
fun JSONObject.putSymmetricEncryptionAlgorithm(name: String, value: EncryptionAlgorithm.Symmetric): JSONObject {
    val algorithmStringRepresentation = value.stringRepresentation
    return putString(name, algorithmStringRepresentation)
}

/**
 * Convenience method to get a `EncryptionAlgorithm.Symmetric` value from `JSONObject`.
 */
@Throws(JSONException::class)
fun JSONObject.getSymmetricEncryptionAlgorithm(name: String): EncryptionAlgorithm.Symmetric {
    return when (val algorithmStringRepresentation = getString(name)) {
        EncryptionAlgorithm.Symmetric.AES256GCM.stringRepresentation -> EncryptionAlgorithm.Symmetric.AES256GCM
        else -> throw JSONException("The EncryptionAlgorithm.Symmetric string representation '$algorithmStringRepresentation' could not be found!")
    }
}
