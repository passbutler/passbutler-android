package de.sicherheitskritisch.passbutler.crypto

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProperties.KEY_ALGORITHM_AES
import androidx.core.hardware.fingerprint.FingerprintManagerCompat
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.InvalidKeyException
import java.security.KeyStore
import java.util.concurrent.Executor
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

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
                    // Do not allow non random IV
                    .setRandomizedEncryptionRequired(true)
                    // Biometric authentication is enforced before key usage:
                    .setUserAuthenticationRequired(true)
                    // The key gets invalidated if Android lock screen is disabled or new biometrics is been enrolled:
                    .setInvalidatedByBiometricEnrollment(true)

                // TODO: Set `setUnlockedDeviceRequired(true)`?

                val keyParameters = keyParameterBuilder.build()
                androidKeyGenerator.init(keyParameters)

                androidKeyGenerator.generateKey()
            } catch (e: Exception) {
                throw GenerateKeyFailedException(e)
            }
        }
    }

    // TODO: Ensure AES-256 + better mode than CBC

    @Throws(InitializeKeyFailedException::class)
    suspend fun initializeKeyForEncryption(keyName: String, encryptionCipher: Cipher) {
        withContext(Dispatchers.IO) {
            try {
                val loadKeyStoreParameter = null
                androidKeyStore.load(loadKeyStoreParameter)

                val secretKey = androidKeyStore.getKey(keyName, null) as? SecretKey ?: throw InvalidKeyException("The key was not found!")
                encryptionCipher.init(Cipher.ENCRYPT_MODE, secretKey)
            } catch (e: Exception) {
                throw InitializeKeyFailedException(e)
            }
        }
    }

    @Throws(InitializeKeyFailedException::class)
    suspend fun initializeKeyForDecryption(keyName: String, decryptionCipher: Cipher, initializationVector: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                val loadKeyStoreParameter = null
                androidKeyStore.load(loadKeyStoreParameter)

                val secretKey = androidKeyStore.getKey(keyName, null) as? SecretKey ?: throw InvalidKeyException("The key was not found!")
                decryptionCipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(initializationVector))
            } catch (e: Exception) {
                throw InitializeKeyFailedException(e)
            }
        }
    }

    @Throws(EncryptionFailedException::class)
    suspend fun encryptData(encryptionCipher: Cipher, data: ByteArray): ByteArray {
        return withContext(Dispatchers.Default) {
            try {
                encryptionCipher.doFinal(data)
            } catch (e: Exception) {
                throw EncryptionFailedException(e)
            }
        }
    }

    @Throws(DecryptionFailedException::class)
    suspend fun decryptData(decryptionCipher: Cipher, data: ByteArray): ByteArray {
        return withContext(Dispatchers.Default) {
            try {
                decryptionCipher.doFinal(data)
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

class BiometricAuthenticationCallbackExecutor(private val coroutineScope: CoroutineScope) : Executor {
    override fun execute(runnable: Runnable) {
        coroutineScope.launch(Dispatchers.IO) {
            runnable.run()
        }
    }
}