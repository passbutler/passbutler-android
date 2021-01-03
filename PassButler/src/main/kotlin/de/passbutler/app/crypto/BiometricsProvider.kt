package de.passbutler.app.crypto

import android.app.KeyguardManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties.BLOCK_MODE_GCM
import android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
import android.security.keystore.KeyProperties.KEY_ALGORITHM_AES
import android.security.keystore.KeyProperties.PURPOSE_DECRYPT
import android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
import androidx.biometric.BiometricManager
import de.passbutler.app.PassButlerApplication
import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.crypto.BiometricsProviding
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.util.concurrent.Executor
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Suppress("BlockingMethodInNonBlockingContext")
class BiometricsProvider : BiometricsProviding {

    override val isBiometricAvailable: Boolean
        get() = isHardwareCapable && isKeyguardSecure && hasEnrolledBiometrics

    private val isHardwareCapable: Boolean
        get() {
            val biometricManager = BiometricManager.from(applicationContext)
            val isHardwareSupportedConstants = listOf(BiometricManager.BIOMETRIC_SUCCESS, BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)
            val canAuthenticateResult = biometricManager.canAuthenticate()

            return isHardwareSupportedConstants.contains(canAuthenticateResult)
        }

    private val isKeyguardSecure: Boolean
        get() {
            val keyguardManager = applicationContext.getSystemService(KeyguardManager::class.java)
            return keyguardManager?.isKeyguardSecure ?: false
        }

    private val hasEnrolledBiometrics: Boolean
        get() {
            val biometricManager = BiometricManager.from(applicationContext)
            return biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }

    private val applicationContext
        get() = PassButlerApplication.applicationContext

    // Throws `KeyStoreException`
    private val androidKeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore")
    }

    // Throws `NoSuchAlgorithmException` and `NoSuchProviderException`
    private val androidKeyGenerator by lazy {
        KeyGenerator.getInstance(KEY_ALGORITHM_AES, "AndroidKeyStore")
    }


    override fun obtainKeyInstance(): Result<Cipher> {
        return try {
            Success(Cipher.getInstance("${KEY_ALGORITHM_AES}/${BLOCK_MODE_GCM}/${ENCRYPTION_PADDING_NONE}"))
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    override suspend fun generateKey(keyName: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                initializeAndroidKeyStore()

                // The key must only be used for encryption and decryption
                val keyUsagePurposes = PURPOSE_ENCRYPT or PURPOSE_DECRYPT
                val keyParameterBuilder = KeyGenParameterSpec.Builder(keyName, keyUsagePurposes)
                    .setKeySize(AES_KEY_BIT_SIZE)
                    .setBlockModes(BLOCK_MODE_GCM)
                    .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
                    // Do not allow non random IV
                    .setRandomizedEncryptionRequired(true)
                    // Biometric authentication is enforced before key usage:
                    .setUserAuthenticationRequired(true)
                    // The key gets invalidated if Android lock screen is disabled or new biometrics is been enrolled:
                    .setInvalidatedByBiometricEnrollment(true)

                // Enable keyguard-bound state if possible
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    keyParameterBuilder.setUnlockedDeviceRequired(true)
                }

                val keyParameters = keyParameterBuilder.build()
                androidKeyGenerator.init(keyParameters)

                androidKeyGenerator.generateKey()

                Success(Unit)
            } catch (exception: Exception) {
                Failure(exception)
            }
        }
    }

    override suspend fun initializeKeyForEncryption(keyName: String, encryptionCipher: Cipher): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                initializeAndroidKeyStore()

                val secretKey = androidKeyStore.getKey(keyName, null) as? SecretKey ?: throw InvalidKeyException("The key '$keyName' was not found!")
                encryptionCipher.init(Cipher.ENCRYPT_MODE, secretKey)

                Success(Unit)
            } catch (exception: Exception) {
                Failure(exception)
            }
        }
    }

    override suspend fun initializeKeyForDecryption(keyName: String, decryptionCipher: Cipher, initializationVector: ByteArray): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                initializeAndroidKeyStore()

                val secretKey = androidKeyStore.getKey(keyName, null) as? SecretKey ?: throw InvalidKeyException("The key '$keyName' was not found!")
                decryptionCipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_AUTHENTICATION_TAG_BIT_SIZE, initializationVector))

                Success(Unit)
            } catch (exception: Exception) {
                Failure(exception)
            }
        }
    }

    override suspend fun encryptData(encryptionCipher: Cipher, data: ByteArray): Result<ByteArray> {
        return withContext(Dispatchers.Default) {
            try {
                Success(encryptionCipher.doFinal(data))
            } catch (exception: Exception) {
                Failure(exception)
            }
        }
    }

    override suspend fun decryptData(decryptionCipher: Cipher, data: ByteArray): Result<ByteArray> {
        return withContext(Dispatchers.Default) {
            try {
                Success(decryptionCipher.doFinal(data))
            } catch (exception: Exception) {
                Failure(exception)
            }
        }
    }

    override suspend fun removeKey(keyName: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                initializeAndroidKeyStore()
                androidKeyStore.deleteEntry(keyName)

                Success(Unit)
            } catch (exception: Exception) {
                Failure(exception)
            }
        }
    }

    @Throws(CertificateException::class, IOException::class, NoSuchAlgorithmException::class)
    private fun initializeAndroidKeyStore() {
        val loadKeyStoreParameter = null
        androidKeyStore.load(loadKeyStoreParameter)
    }

    companion object {
        private const val AES_KEY_BIT_SIZE = 256
        private const val GCM_AUTHENTICATION_TAG_BIT_SIZE = 128
    }
}

class BiometricAuthenticationCallbackExecutor(private val coroutineScope: CoroutineScope, private val coroutineDispatcher: CoroutineDispatcher) : Executor {
    override fun execute(runnable: Runnable) {
        coroutineScope.launch(coroutineDispatcher) {
            runnable.run()
        }
    }
}