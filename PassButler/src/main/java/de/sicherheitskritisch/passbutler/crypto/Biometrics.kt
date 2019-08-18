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
import java.io.IOException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
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

    @Throws(NoSuchAlgorithmException::class, NoSuchPaddingException::class)
    fun obtainKeyInstance(): Cipher {
        return Cipher.getInstance("$KEY_ALGORITHM_AES/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
    }

    @Throws(CertificateException::class, InvalidAlgorithmParameterException::class, IOException::class, KeyStoreException::class, NoSuchAlgorithmException::class, NoSuchProviderException::class)
    suspend fun generateKey(keyName: String) {
        withContext(Dispatchers.IO) {
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
        }
    }

    @Throws(CertificateException::class, InvalidKeyException::class, IOException::class, KeyStoreException::class, NoSuchAlgorithmException::class, UnrecoverableKeyException::class)
    suspend fun initializeKeyForEncryption(keyName: String, encryptionKeyCipher: Cipher) {
        initializeKey(keyName, encryptionKeyCipher, Cipher.ENCRYPT_MODE)
    }

    @Throws(CertificateException::class, InvalidKeyException::class, IOException::class, KeyStoreException::class, NoSuchAlgorithmException::class, UnrecoverableKeyException::class)
    suspend fun initializeKeyForDecryption(keyName: String, decryptionKeyCipher: Cipher) {
        initializeKey(keyName, decryptionKeyCipher, Cipher.DECRYPT_MODE)
    }

    @Throws(
        CertificateException::class,
        IllegalArgumentException::class,
        InvalidKeyException::class,
        IOException::class,
        KeyStoreException::class,
        NoSuchAlgorithmException::class,
        UnrecoverableKeyException::class
    )
    private suspend fun initializeKey(keyName: String, encryptionKeyCipher: Cipher, cipherMode: Int) {
        if (cipherMode != Cipher.ENCRYPT_MODE || cipherMode != Cipher.DECRYPT_MODE) {
            throw IllegalArgumentException("The cipher moce is invalid!")
        }

        withContext(Dispatchers.IO) {
            val loadKeyStoreParameter = null
            androidKeyStore.load(loadKeyStoreParameter)

            val secretKey = androidKeyStore.getKey(keyName, null) as? SecretKey ?: throw InvalidKeyException("The key was not found!")
            encryptionKeyCipher.init(cipherMode, secretKey)
        }
    }

    // TODO: deleteKey fun
    // TODO: encryptData fun
    // TODO: decryptData fun
}