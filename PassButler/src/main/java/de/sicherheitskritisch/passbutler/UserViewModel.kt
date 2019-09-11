package de.sicherheitskritisch.passbutler

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.NonNullValueGetterLiveData
import de.sicherheitskritisch.passbutler.base.SignalEmitter
import de.sicherheitskritisch.passbutler.base.clear
import de.sicherheitskritisch.passbutler.crypto.Biometrics
import de.sicherheitskritisch.passbutler.crypto.Derivation
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.EncryptedValue
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import de.sicherheitskritisch.passbutler.crypto.models.ProtectedValue
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.crypto.Cipher
import kotlin.coroutines.CoroutineContext

/**
 * This viewmodel is held by `RootViewModel` end contains the business logic for logged-in user.
 *
 * No need to inherit from `CoroutineScopeAndroidViewModel` because this viewmodel is not held by a `Fragment`,
 * thus `onCleared()` is never called. Instead `cancelJobs()` is manually called by `RootViewModel`.
 */
class UserViewModel private constructor(
    private val userManager: UserManager,
    private val user: User,
    private var masterPasswordAuthenticationHash: String,
    private val masterKeyDerivationInformation: KeyDerivationInformation,
    private val protectedMasterEncryptionKey: ProtectedValue<CryptographicKey>,
    private val itemEncryptionPublicKey: CryptographicKey,
    private val protectedItemEncryptionSecretKey: ProtectedValue<CryptographicKey>,
    private val protectedSettings: ProtectedValue<UserSettings>,
    masterPassword: String?
) : ViewModel(), CoroutineScope {

    val username
        get() = user.username

    val automaticLockTimeout = MutableLiveData<Int?>()
    val hidePasswordsEnabled = MutableLiveData<Boolean?>()

    val biometricUnlockAvailable = NonNullValueGetterLiveData {
        Biometrics.isHardwareCapable && Biometrics.isKeyguardSecure && Biometrics.hasEnrolledBiometrics
    }

    val biometricUnlockEnabled = NonNullValueGetterLiveData {
        biometricUnlockAvailable.value && userManager.loggedInStateStorage.encryptedMasterPassword != null
    }

    val unlockFinished = SignalEmitter()

    private val automaticLockTimeoutChangedObserver: (Int?) -> Unit = { applyUserSettings() }
    private val hidePasswordsEnabledChangedObserver: (Boolean?) -> Unit = { applyUserSettings() }

    private var masterEncryptionKey: ByteArray? = null

    private var settings: UserSettings? = null
        set(newSettingsValue) {
            if (newSettingsValue != field) {
                val fieldWasUninitialized = (field == null)
                field = newSettingsValue

                // Do not persist the first time (if field was uninitialized) because it is unnecessary
                if (!fieldWasUninitialized) {
                    persistUserSettings()
                }
            }
        }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + coroutineJob

    private val coroutineJob = SupervisorJob()

    @Throws(IllegalArgumentException::class)
    constructor(userManager: UserManager, user: User, masterPassword: String?) : this(
        userManager,
        user,
        user.masterPasswordAuthenticationHash ?: throw IllegalArgumentException("The given user has no master password authentication hash!"),
        user.masterKeyDerivationInformation ?: throw IllegalArgumentException("The given user has no master key derivation information!"),
        user.masterEncryptionKey ?: throw IllegalArgumentException("The given user has no master encryption key!"),
        user.itemEncryptionPublicKey,
        user.itemEncryptionSecretKey ?: throw IllegalArgumentException("The given user has no item encryption secret key!"),
        user.settings ?: throw IllegalArgumentException("The given user has no user settings!"),
        masterPassword
    )

    init {
        // If the master password was supplied (only on login), directly unlock resources
        if (masterPassword != null) {
            launch {
                try {
                    unlockMasterEncryptionKey(masterPassword)
                } catch (e: UnlockFailedException) {
                    // TODO: This also occurs if the user settings could not be deserialized or webservice initialisation and should not crash the app on login
                    throw IllegalStateException("The unlock of the master encryption key failed despite the master password was supplied from login!", e)
                }
            }
        }
    }

    fun cancelJobs() {
        L.d("UserViewModel", "cancelJobs()")
        coroutineJob.cancel()
    }

    @Throws(UnlockFailedException::class)
    suspend fun unlockMasterEncryptionKey(masterPassword: String) {
        withContext(Dispatchers.IO) {
            L.d("UserViewModel", "unlockMasterEncryptionKey()")

            try {
                masterEncryptionKey = decryptMasterEncryptionKey(masterPassword).also {
                    decryptUserSettings(it)
                }

                if (userManager.loggedInStateStorage.userType is UserType.Server) {
                    userManager.restoreWebservices(masterPassword)
                }

                unlockFinished.emit()
            } catch (e: Exception) {
                throw UnlockFailedException(e)
            }
        }
    }

    // TODO: Add throws annotation
    private fun decryptUserSettings(masterEncryptionKey: ByteArray) {
        settings = protectedSettings.decrypt(masterEncryptionKey, UserSettings.Deserializer).also {
            automaticLockTimeout.postValue(it.automaticLockTimeout)
            hidePasswordsEnabled.postValue(it.hidePasswords)
        }

        // Register observers after field initialisations to avoid initial observer calls
        registerSettingsChangedObservers()
    }

    private fun registerSettingsChangedObservers() {
        // The `LiveData` observe calls must be done on main thread
        launch(Dispatchers.Main) {
            automaticLockTimeout.observeForever(automaticLockTimeoutChangedObserver)
            hidePasswordsEnabled.observeForever(hidePasswordsEnabledChangedObserver)
        }
    }

    suspend fun clearMasterEncryptionKey() {
        withContext(Dispatchers.IO) {
            L.d("UserViewModel", "clearMasterEncryptionKey()")

            masterEncryptionKey?.clear()
            masterEncryptionKey = null
            clearUserSettings()
        }
    }

    private fun clearUserSettings() {
        // Unregister observers before setting field reset to avoid unnecessary observer calls
        unregisterSettingsChangedObservers()

        settings = null
        automaticLockTimeout.postValue(null)
        hidePasswordsEnabled.postValue(null)
    }

    private fun unregisterSettingsChangedObservers() {
        // The `LiveData` remove observer calls must be done on main thread
        launch(Dispatchers.Main) {
            automaticLockTimeout.removeObserver(automaticLockTimeoutChangedObserver)
            hidePasswordsEnabled.removeObserver(hidePasswordsEnabledChangedObserver)
        }
    }

    @Throws(UpdateMasterPasswordFailedException::class)
    suspend fun updateMasterPassword(newMasterPassword: String) {
        withContext(Dispatchers.Default) {
            var newMasterKey: ByteArray? = null

            try {
                val masterEncryptionKey = masterEncryptionKey ?: throw IllegalStateException("The master encryption key is null despite it was tried to update the master password!")

                val newLocalMasterPasswordAuthenticationHash = Derivation.deriveLocalAuthenticationHash(username, newMasterPassword)
                val newServerMasterPasswordAuthenticationHash = Derivation.deriveServerAuthenticationHash(newLocalMasterPasswordAuthenticationHash)
                masterPasswordAuthenticationHash = newServerMasterPasswordAuthenticationHash

                newMasterKey = Derivation.deriveMasterKey(newMasterPassword, masterKeyDerivationInformation)
                protectedMasterEncryptionKey.update(newMasterKey, CryptographicKey(masterEncryptionKey))

                // Disable biometric unlock because master password re-encryption would require biometric authentication and made flow more complex
                disableBiometricUnlock()

                withContext(Dispatchers.IO) {
                    val user = createUserModel()
                    userManager.updateUser(user)
                }

                // The auth webservice needs to re-initialized because of master password change
                userManager.initializeAuthWebservice(newMasterPassword)
            } catch (e: Exception) {
                throw UpdateMasterPasswordFailedException(e)
            } finally {
                newMasterKey?.clear()
            }
        }
    }

    @Throws(EnableBiometricUnlockFailedException::class)
    suspend fun enableBiometricUnlock(initializedSetupBiometricUnlockCipher: Cipher, masterPassword: String) {
        withContext(Dispatchers.Default) {
            try {
                // Test if master password is correct
                decryptMasterEncryptionKey(masterPassword)

                val encryptedMasterPasswordInitializationVector = initializedSetupBiometricUnlockCipher.iv
                val encryptedMasterPassword = Biometrics.encryptData(initializedSetupBiometricUnlockCipher, masterPassword.toByteArray())

                userManager.loggedInStateStorage.encryptedMasterPassword = EncryptedValue(encryptedMasterPasswordInitializationVector, encryptedMasterPassword)
                userManager.loggedInStateStorage.persist()

                biometricUnlockEnabled.notifyChange()
            } catch (e: Exception) {
                // Try to rollback biometric unlock setup if anything failed
                disableBiometricUnlock()

                throw EnableBiometricUnlockFailedException(e)
            }
        }
    }

    @Throws(DisableBiometricUnlockFailedException::class)
    suspend fun disableBiometricUnlock() {
        withContext(Dispatchers.IO) {
            try {
                Biometrics.removeKey(BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME)

                userManager.loggedInStateStorage.encryptedMasterPassword = null
                userManager.loggedInStateStorage.persist()

                biometricUnlockEnabled.notifyChange()
            } catch (e: Exception) {
                throw DisableBiometricUnlockFailedException(e)
            }
        }
    }

    @Throws(DecryptMasterEncryptionKeyFailedException::class)
    private suspend fun decryptMasterEncryptionKey(masterPassword: String): ByteArray {
        return withContext(Dispatchers.Default) {
            var masterKey: ByteArray? = null

            try {
                masterKey = Derivation.deriveMasterKey(masterPassword, masterKeyDerivationInformation)

                val decryptedProtectedMasterEncryptionKey = protectedMasterEncryptionKey.decrypt(masterKey, CryptographicKey.Deserializer)
                decryptedProtectedMasterEncryptionKey.key
            } catch (e: Exception) {
                throw DecryptMasterEncryptionKeyFailedException(e)
            } finally {
                masterKey?.clear()
            }
        }
    }

    private fun applyUserSettings() {
        val automaticLockTimeoutValue = automaticLockTimeout.value
        val hidePasswordsEnabledValue = hidePasswordsEnabled.value

        if (automaticLockTimeoutValue != null && hidePasswordsEnabledValue != null) {
            settings = UserSettings(automaticLockTimeoutValue, hidePasswordsEnabledValue)
        }
    }

    private fun persistUserSettings() {
        launch(Dispatchers.Default) {
            val masterEncryptionKey = masterEncryptionKey
            val settings = settings

            // Only persist if master encryption key and settings are set (user logged-in and state unlocked)
            if (masterEncryptionKey != null && settings != null) {
                try {
                    protectedSettings.update(masterEncryptionKey, settings)

                    withContext(Dispatchers.IO) {
                        val user = createUserModel()
                        userManager.updateUser(user)
                    }
                } catch (e: Exception) {
                    L.w("UserViewModel", "persistUserSettings(): The user settings could not be updated!", e)
                }
            }
        }
    }

    private fun createUserModel(): User {
        // Only update fields that are allowed to modify (server reject changes on non-allowed field anyway)
        return user.copy(
            masterPasswordAuthenticationHash = masterPasswordAuthenticationHash,
            masterKeyDerivationInformation = masterKeyDerivationInformation,
            masterEncryptionKey = protectedMasterEncryptionKey,
            settings = protectedSettings,
            modified = Date()
        )
    }

    class UnlockFailedException(cause: Exception? = null) : Exception(cause)
    class DecryptMasterEncryptionKeyFailedException(cause: Exception? = null) : Exception(cause)
    class UpdateMasterPasswordFailedException(cause: Exception? = null) : Exception(cause)
    class EnableBiometricUnlockFailedException(cause: Exception? = null) : Exception(cause)
    class DisableBiometricUnlockFailedException(cause: Exception? = null) : Exception(cause)

    companion object {
        const val BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME = "MasterPasswordEncryptionKey"
    }
}
