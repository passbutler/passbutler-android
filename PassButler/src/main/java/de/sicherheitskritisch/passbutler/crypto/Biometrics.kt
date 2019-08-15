package de.sicherheitskritisch.passbutler.crypto

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProperties.KEY_ALGORITHM_AES
import androidx.core.hardware.fingerprint.FingerprintManagerCompat
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object Biometrics {

    private const val MASTER_PASSWORD_ENCRYPTION_KEY_NAME = "BiometricsMasterPasswordEncryptionKey"

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

    // TODO: Add documentation
    val isKeyguardSecure: Boolean
        get() {
            val keyguardManager = applicationContext.getSystemService(KeyguardManager::class.java)
            return keyguardManager.isKeyguardSecure
        }

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

    val hasEnrolledBiometrics: Boolean
        get() = if (Build.VERSION.SDK_INT > 28) {
            val biometricManager = applicationContext.getSystemService(BiometricManager::class.java)
            biometricManager?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            val fingerprintManagerCompat = FingerprintManagerCompat.from(applicationContext)
            fingerprintManagerCompat.hasEnrolledFingerprints()
        }

    // TODO: Suspend?
    // TODO: Throws NoSuchAlgorithmException | InvalidAlgorithmParameterException | CertificateException | IOException
    fun generateEncryptionKey() {
        val loadKeyStoreParameter = null
        androidKeyStore.load(loadKeyStoreParameter)
        // Set the alias of the entry in Android KeyStore where the key will appear
        // and the constrains (purposes) in the constructor of the Builder

        // The key must only be used for encryption and decryption
        val keyUsagePurposes = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT

        val keyParameterBuilder = KeyGenParameterSpec.Builder(MASTER_PASSWORD_ENCRYPTION_KEY_NAME, keyUsagePurposes)
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            // Biometric authentication is enforced before key usage:
            .setUserAuthenticationRequired(true)
            // The key gets invalidated if Android lock screen is disabled or new biometrics is been enrolled
            .setInvalidatedByBiometricEnrollment(true)


        val keyParameters = keyParameterBuilder.build()
        androidKeyGenerator.init(keyParameters)

        androidKeyGenerator.generateKey()
    }

    // TODO: Suspend?
    // TODO: Throws NoSuchAlgorithmException | NoSuchPaddingException
    fun obtainEncryptionKeyInstance(): Cipher {
        return Cipher.getInstance("$KEY_ALGORITHM_AES/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
    }

    // TODO: Suspend?
    // TODO: Really catch exception?
    @Throws(RuntimeException::class)
    fun initializeEncryptionKey(encryptionKeyCipher: Cipher): Boolean {
        return try {
            val loadKeyStoreParameter = null
            androidKeyStore.load(loadKeyStoreParameter)

            val secretKey = androidKeyStore.getKey(MASTER_PASSWORD_ENCRYPTION_KEY_NAME, null) as SecretKey
            encryptionKeyCipher.init(Cipher.ENCRYPT_MODE, secretKey)

            true
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Occurs if the Android lock screen has disabled or new biometrics has been enrolled
            false
        } catch (e: Exception) {
            throw RuntimeException("The encryption key could not be initialized!", e)
        }
    }
}