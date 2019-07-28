package de.sicherheitskritisch.passbutler

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.clear
import de.sicherheitskritisch.passbutler.base.optionalContentNotEquals
import de.sicherheitskritisch.passbutler.crypto.Derivation
import de.sicherheitskritisch.passbutler.crypto.ProtectedValue
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
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
    private val masterKeyDerivationInformation: KeyDerivationInformation,
    private val protectedMasterEncryptionKey: ProtectedValue<CryptographicKey>,
    private val protectedSettings: ProtectedValue<UserSettings>,
    masterPassword: String?
) : ViewModel(), CoroutineScope {

    val username
        get() = user.username

    val lockTimeoutSetting = MutableLiveData<Int?>()
    val hidePasswordsSetting = MutableLiveData<Boolean?>()

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

    constructor(userManager: UserManager, user: User, masterPassword: String?) : this(
        userManager,
        user,
        user.masterKeyDerivationInformation ?: throw IllegalArgumentException("The given user has no master key derivation information!"),
        user.masterEncryptionKey ?: throw IllegalArgumentException("The given user has no master encryption key!"),
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
        // Execute deserialization/decryption on the dispatcher for CPU load
        withContext(Dispatchers.Default) {
            var masterKey: ByteArray? = null

            try {
                masterKey = Derivation.deriveMasterKey(masterPassword, masterKeyDerivationInformation)

                val decryptedProtectedMasterEncryptionKey = protectedMasterEncryptionKey.decrypt(masterKey) {
                    CryptographicKey.deserialize(it)
                }

                if (decryptedProtectedMasterEncryptionKey != null) {
                    masterEncryptionKey = decryptedProtectedMasterEncryptionKey.key
                } else {
                    throw IllegalStateException("The master encryption key could not be decrypted!")
                }
            } catch (e: Exception) {
                throw UnlockFailedException(e)
            } finally {
                masterKey?.clear()
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

    private fun decryptUserSettings() {
        val masterEncryptionKey = masterEncryptionKey

        if (masterEncryptionKey != null) {
            L.d("UserViewModel", "decryptUserSettings(): The master encryption key was set, decrypt and update user settings...")

            settings = protectedSettings.decrypt(masterEncryptionKey) {
                UserSettings.deserialize(it)
            }

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

    @Throws(IllegalStateException::class)
    private fun persistUserSettings() {
        // Execute encryption on the dispatcher for CPU load
        launch(Dispatchers.Default) {
            val masterEncryptionKey = masterEncryptionKey ?: throw IllegalStateException("The master encryption key is null despite it was tried to persist the user settings!")
            val settings = settings ?: throw IllegalStateException("The user settings is null despite it was tried to persist the user settings!")

            protectedSettings.update(masterEncryptionKey, settings)

            // Switch back on IO dispatcher
            withContext(Dispatchers.IO) {
                val user = createUserModel()
                userManager.updateUser(user)
            }
        }
    }

    private fun createUserModel(): User {
        // Only update fields that are allowed to modify (server reject changes on non-allowed field anyway)
        return user.copy(
            masterKeyDerivationInformation = masterKeyDerivationInformation,
            masterEncryptionKey = protectedMasterEncryptionKey,
            settings = protectedSettings,
            modified = Date()
        )
    }

    class UnlockFailedException(cause: Exception? = null) : Exception(cause)
}
