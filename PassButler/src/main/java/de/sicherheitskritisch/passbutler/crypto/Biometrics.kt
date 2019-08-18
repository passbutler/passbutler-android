package de.sicherheitskritisch.passbutler.crypto

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProperties.KEY_ALGORITHM_AES
import androidx.core.hardware.fingerprint.FingerprintManagerCompat
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object Biometrics {

    val isHardwareCapable: Boolean
        get() = if (Build.VERSION.SDK_INT > 28) {
            val biometricManager = applicationContext.getSystemService(BiometricManager::class.java)

            val isHardwareSupportedConstants = listOf(BiometricManager.BIOMETRIC_SUCCESS, BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)
            val canAuthenticateResult = biometricManager?.canAuthenticate()
            isHardwareSupportedConstants.contains(canAuthenticateResult)
        } else {
            val fingerprintManagerCompat = FingerprintManagerCompat.from(applicationContext)
            fingerprintManagerCompat.isHardwareDetected
        }

    val isKeyguardSecure: Boolean
        get() {
            val keyguardManager = applicationContext.getSystemService(KeyguardManager::class.java)
            return keyguardManager.isKeyguardSecure
        }

    val hasEnrolledBiometrics: Boolean
        get() = if (Build.VERSION.SDK_INT > 28) {
            val biometricManager = applicationContext.getSystemService(BiometricManager::class.java)
            biometricManager?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            val fingerprintManagerCompat = FingerprintManagerCompat.from(applicationContext)
            fingerprintManagerCompat.hasEnrolledFingerprints()
        }

    private val applicationContext
        get() = AbstractPassButlerApplication.applicationContext

    // Throws `KeyStoreException`
    private val androidKeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore")
    }

    // Throws `NoSuchAlgorithmException` and `NoSuchProviderException`
    private val androidKeyGenerator by lazy {
        KeyGenerator.getInstance(KEY_ALGORITHM_AES, "AndroidKeyStore")
    }

    @Throws(ObtainKeyFailedException::class)
    fun obtainKeyInstance(): Cipher {
        return try {
            Cipher.getInstance("$KEY_ALGORITHM_AES/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
        } catch (e: Exception) {
            throw ObtainKeyFailedException(e)
        }
    }

    @Throws(GenerateKeyFailedException::class)
    suspend fun generateKey(keyName: String) {
        withContext(Dispatchers.IO) {
            try {
                val loadKeyStoreParameter = null
                androidKeyStore.load(loadKeyStoreParameter)

                // The key must only be used for encryption and decryption
                val keyUsagePurposes = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT

                val keyParameterBuilder = KeyGenParameterSpec.Builder(keyName, keyUsagePurposes)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    // Biometric authentication is enforced before key usage:
                    .setUserAuthenticationRequired(true)
                    // The key gets invalidated if Android lock screen is disabled or new biometrics is been enrolled:
                    .setInvalidatedByBiometricEnrollment(true)

                val keyParameters = keyParameterBuilder.build()
                androidKeyGenerator.init(keyParameters)

                androidKeyGenerator.generateKey()
            } catch (e: Exception) {
                throw GenerateKeyFailedException(e)
            }
        }
    }

    @Throws(InitializeKeyFailedException::class)
    suspend fun initializeKeyForEncryption(keyName: String, encryptionKey: Cipher) {
        initializeKey(keyName, encryptionKey, Cipher.ENCRYPT_MODE)
    }

    @Throws(InitializeKeyFailedException::class)
    suspend fun initializeKeyForDecryption(keyName: String, decryptionKey: Cipher) {
        initializeKey(keyName, decryptionKey, Cipher.DECRYPT_MODE)
    }

    @Throws(InitializeKeyFailedException::class)
    private suspend fun initializeKey(keyName: String, encryptionKey: Cipher, cipherMode: Int) {
        withContext(Dispatchers.IO) {
            try {
                if (cipherMode != Cipher.ENCRYPT_MODE && cipherMode != Cipher.DECRYPT_MODE) {
                    throw IllegalArgumentException("The cipher mode is invalid!")
                }

                val loadKeyStoreParameter = null
                androidKeyStore.load(loadKeyStoreParameter)

                val secretKey = androidKeyStore.getKey(keyName, null) as? SecretKey ?: throw InvalidKeyException("The key was not found!")
                encryptionKey.init(cipherMode, secretKey)

            } catch (e: Exception) {
                throw InitializeKeyFailedException(e)
            }
        }
    }

    @Throws(EncryptionFailedException::class)
    suspend fun encryptData(encryptionKey: Cipher, data: ByteArray): ByteArray? {
        return withContext(Dispatchers.Default) {
            try {
                encryptionKey.doFinal(data)
            } catch (e: Exception) {
                throw EncryptionFailedException(e)
            }
        }
    }

    @Throws(DecryptionFailedException::class)
    suspend fun decryptData(decryptionKey: Cipher, data: ByteArray): ByteArray {
        return withContext(Dispatchers.Default) {
            try {
                decryptionKey.doFinal(data)
            } catch (e: Exception) {
                throw DecryptionFailedException(e)
            }
        }
    }

    @Throws(RemoveKeyFailedException::class)
    suspend fun removeKey(keyName: String) {
        withContext(Dispatchers.IO) {
            try {
                val loadKeyStoreParameter = null
                androidKeyStore.load(loadKeyStoreParameter)

                androidKeyStore.deleteEntry(keyName)
            } catch (e: Exception) {
                throw RemoveKeyFailedException(e)
            }
        }
    }

    class ObtainKeyFailedException(cause: Throwable) : Exception(cause)
    class GenerateKeyFailedException(cause: Throwable) : Exception(cause)
    class InitializeKeyFailedException(cause: Throwable) : Exception(cause)
    class EncryptionFailedException(cause: Throwable) : Exception(cause)
    class DecryptionFailedException(cause: Throwable) : Exception(cause)
    class RemoveKeyFailedException(cause: Throwable) : Exception(cause)
}