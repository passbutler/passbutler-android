package de.sicherheitskritisch.passbutler

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.SignalEmitter
import de.sicherheitskritisch.passbutler.base.ValueGetterLiveData
import de.sicherheitskritisch.passbutler.base.clear
import de.sicherheitskritisch.passbutler.base.optionalContentNotEquals
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

    val lockTimeoutSetting = MutableLiveData<Int?>()
    val hidePasswordsSetting = MutableLiveData<Boolean?>()

    val biometricUnlockAvailable = ValueGetterLiveData {
        Biometrics.isHardwareCapable && Biometrics.isKeyguardSecure && Biometrics.hasEnrolledBiometrics
    }

    val biometricUnlockEnabled = ValueGetterLiveData {
        biometricUnlockAvailable.value && userManager.loggedInStateStorage.encryptedMasterPassword != null
    }

    val unlockFinished = SignalEmitter()

    private val lockTimeoutSettingChangedObserver: (Int?) -> Unit = { applyUserSettings() }
    private val hidePasswordsSettingChangedObserver: (Boolean?) -> Unit = { applyUserSettings() }

    private var masterEncryptionKey: ByteArray? = null
        set(newMasterEncryptionKey) {
            if (field.optionalContentNotEquals(newMasterEncryptionKey)) {
                field = newMasterEncryptionKey
                decryptUserSettings()
            }
        }

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
            try {
                masterEncryptionKey = decryptMasterEncryptionKey(masterPassword)

                if (userManager.loggedInStateStorage.userType is UserType.Server) {
                    userManager.restoreWebservices(masterPassword)
                }

                unlockFinished.emit()
            } catch (e: Exception) {
                throw UnlockFailedException(e)
            }
        }
    }

    suspend fun clearMasterEncryptionKey() {
        // Execute on the same dispatcher as the `unlockMasterEncryptionKey` for uniformity reasons
        withContext(Dispatchers.Default) {
            masterEncryptionKey?.clear()
            masterEncryptionKey = null
        }
    }

    @Throws(UpdateMasterPasswordFailedException::class)
    suspend fun updateMasterPassword(newMasterPassword: String) {
        // Execute deserialization/decryption on the dispatcher for CPU load
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
        // Execute encryption on the dispatcher for CPU load
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
        // Execute deserialization/decryption on the dispatcher for CPU load
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

    private fun decryptUserSettings() {
        val masterEncryptionKey = masterEncryptionKey

        if (masterEncryptionKey != null) {
            L.d("UserViewModel", "decryptUserSettings(): The master encryption key was set, decrypt and update user settings...")

            settings = protectedSettings.decrypt(masterEncryptionKey, UserSettings.Deserializer)
            lockTimeoutSetting.postValue(settings?.lockTimeout)
            hidePasswordsSetting.postValue(settings?.hidePasswords)

            // Register observers after field initialisations to avoid initial observer calls
            registerObservers()
        } else {
            L.d("UserViewModel", "decryptUserSettings(): The master encryption key was reset, clear user settings...")

            // Unregister observers before setting field reset to avoid unnecessary observer calls
            unregisterObservers()

            settings = null
            lockTimeoutSetting.postValue(null)
            hidePasswordsSetting.postValue(null)
        }
    }

    private fun registerObservers() {
        // The `LiveData` observe calls must be done on main thread
        launch(Dispatchers.Main) {
            lockTimeoutSetting.observeForever(lockTimeoutSettingChangedObserver)
            hidePasswordsSetting.observeForever(hidePasswordsSettingChangedObserver)
        }
    }

    private fun unregisterObservers() {
        // The `LiveData` remove observer calls must be done on main thread
        launch(Dispatchers.Main) {
            lockTimeoutSetting.removeObserver(lockTimeoutSettingChangedObserver)
            hidePasswordsSetting.removeObserver(hidePasswordsSettingChangedObserver)
        }
    }

    private fun applyUserSettings() {
        val newLockTimeoutSetting = lockTimeoutSetting.value
        val newHidePasswordsSetting = hidePasswordsSetting.value

        if (newLockTimeoutSetting != null && newHidePasswordsSetting != null) {
            settings = UserSettings(newLockTimeoutSetting, newHidePasswordsSetting)
        }
    }

    private fun persistUserSettings() {
        // Execute encryption on the dispatcher for CPU load
        launch(Dispatchers.Default) {
            val masterEncryptionKey = masterEncryptionKey
            val settings = settings

            // Only persist if master encryption key and settings are set (user logged-in and state unlocked)
            if (masterEncryptionKey != null && settings != null) {
                protectedSettings.update(masterEncryptionKey, settings)

                // Switch back on IO dispatcher
                withContext(Dispatchers.IO) {
                    val user = createUserModel()
                    userManager.updateUser(user)
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
