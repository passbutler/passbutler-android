package de.sicherheitskritisch.passbutler.crypto

import android.security.keystore.KeyProperties.BLOCK_MODE_ECB
import android.security.keystore.KeyProperties.BLOCK_MODE_GCM
import android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
import android.security.keystore.KeyProperties.ENCRYPTION_PADDING_RSA_OAEP
import android.security.keystore.KeyProperties.KEY_ALGORITHM_AES
import android.security.keystore.KeyProperties.KEY_ALGORITHM_RSA
import de.sicherheitskritisch.passbutler.base.bitSize
import de.sicherheitskritisch.passbutler.base.byteSize
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

sealed class EncryptionAlgorithm(val stringRepresentation: String) {

    override fun toString(): String {
        return stringRepresentation
    }

    sealed class Symmetric(stringRepresentation: String) : EncryptionAlgorithm(stringRepresentation) {

        // TODO: Convert to suspend functions
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
                    val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM_AES)
                    keyGenerator.init(AES_KEY_BIT_SIZE)

                    val secretKey = keyGenerator.generateKey()
                    secretKey.encoded
                } catch (exception: Exception) {
                    throw GenerateEncryptionKeyFailedException(exception)
                }
            }

            override fun generateInitializationVector(): ByteArray {
                val bytesCount = GCM_INITIALIZATION_VECTOR_BIT_SIZE.byteSize
                return RandomGenerator.generateRandomBytes(bytesCount)
            }

            @Throws(EncryptionFailedException::class)
            override fun encrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray {
                return try {
                    require(initializationVector.bitSize == GCM_INITIALIZATION_VECTOR_BIT_SIZE) { "The initialization vector must be $GCM_INITIALIZATION_VECTOR_BIT_SIZE bits long!" }
                    require(encryptionKey.bitSize == AES_KEY_BIT_SIZE) { "The encryption key must be $AES_KEY_BIT_SIZE bits long!" }

                    val secretKeySpec = SecretKeySpec(encryptionKey, KEY_ALGORITHM_AES)
                    val gcmParameterSpec = GCMParameterSpec(GCM_AUTHENTICATION_TAG_BIT_SIZE, initializationVector)

                    // The GCM is no classic block mode and thus has no padding
                    val encryptCipherInstance = Cipher.getInstance("$KEY_ALGORITHM_AES/${BLOCK_MODE_GCM}/${ENCRYPTION_PADDING_NONE}")
                    encryptCipherInstance.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec)

                    val encryptedData = encryptCipherInstance.doFinal(data)

                    encryptedData
                } catch (exception: Exception) {
                    throw EncryptionFailedException(exception)
                }
            }

            @Throws(DecryptionFailedException::class)
            override fun decrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray {
                return try {
                    require(initializationVector.bitSize == GCM_INITIALIZATION_VECTOR_BIT_SIZE) { "The initialization vector must be $GCM_INITIALIZATION_VECTOR_BIT_SIZE bits long!" }
                    require(encryptionKey.bitSize == AES_KEY_BIT_SIZE) { "The encryption key must be $AES_KEY_BIT_SIZE bits long!" }

                    val secretKeySpec = SecretKeySpec(encryptionKey, KEY_ALGORITHM_AES)
                    val gcmParameterSpec = GCMParameterSpec(GCM_AUTHENTICATION_TAG_BIT_SIZE, initializationVector)

                    // The GCM is no classic block mode and thus has no padding
                    val encryptCipherInstance = Cipher.getInstance("$KEY_ALGORITHM_AES/${BLOCK_MODE_GCM}/${ENCRYPTION_PADDING_NONE}")
                    encryptCipherInstance.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParameterSpec)

                    val decryptedData = encryptCipherInstance.doFinal(data)

                    decryptedData
                } catch (exception: Exception) {
                    throw DecryptionFailedException(exception)
                }
            }
        }
    }

    sealed class Asymmetric(stringRepresentation: String) : EncryptionAlgorithm(stringRepresentation) {

        abstract fun generateKeyPair(): KeyPair
        abstract fun encrypt(publicKey: ByteArray, data: ByteArray): ByteArray
        abstract fun decrypt(secretKey: ByteArray, data: ByteArray): ByteArray

        object RSA2048OAEP : EncryptionAlgorithm.Asymmetric("RSA-2048-OAEP") {

            private const val RSA_KEY_LENGTH = 2048

            @Throws(GenerateEncryptionKeyFailedException::class)
            override fun generateKeyPair(): KeyPair {
                val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM_RSA)
                keyPairGenerator.initialize(RSA_KEY_LENGTH)

                return keyPairGenerator.genKeyPair()
            }

            @Throws(EncryptionFailedException::class)
            override fun encrypt(publicKey: ByteArray, data: ByteArray): ByteArray {
                return try {
                    val initializedCipher = Cipher.getInstance("$KEY_ALGORITHM_RSA/$BLOCK_MODE_ECB/$ENCRYPTION_PADDING_RSA_OAEP").apply {
                        val publicKeyInstance = KeyFactory.getInstance(KEY_ALGORITHM_RSA).generatePublic(X509EncodedKeySpec(publicKey))
                        val oaepParameterSpec = createOAEPParameterSpec()
                        init(Cipher.ENCRYPT_MODE, publicKeyInstance, oaepParameterSpec)
                    }

                    initializedCipher.doFinal(data)
                } catch (exception: Exception) {
                    throw EncryptionFailedException(exception)
                }
            }

            @Throws(DecryptionFailedException::class)
            override fun decrypt(secretKey: ByteArray, data: ByteArray): ByteArray {
                return try {
                    val initializedCipher = Cipher.getInstance("$KEY_ALGORITHM_RSA/$BLOCK_MODE_ECB/$ENCRYPTION_PADDING_RSA_OAEP").apply {
                        val secretKeyInstance = KeyFactory.getInstance(KEY_ALGORITHM_RSA).generatePrivate(PKCS8EncodedKeySpec(secretKey))
                        val oaepParameterSpec = createOAEPParameterSpec()
                        init(Cipher.DECRYPT_MODE, secretKeyInstance, oaepParameterSpec)
                    }

                    initializedCipher.doFinal(data)
                } catch (exception: Exception) {
                    throw DecryptionFailedException(exception)
                }
            }

            private fun createOAEPParameterSpec(): OAEPParameterSpec {
                // Use SHA-256 for main and also for MGF1 digest
                return OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
            }
        }
    }

    // TODO: Use `Result` instead
    class GenerateEncryptionKeyFailedException(cause: Exception? = null) : Exception(cause)
    class EncryptionFailedException(cause: Exception? = null) : Exception(cause)
    class DecryptionFailedException(cause: Exception? = null) : Exception(cause)
}