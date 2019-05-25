package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.clear
import de.sicherheitskritisch.passbutler.base.optionalContentNotEquals
import de.sicherheitskritisch.passbutler.crypto.KeyDerivation
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * This viewmodel is held by `RootViewModel` end contains the business logic for logged-in user.
 *
 * No need to inherit from `CoroutineScopeAndroidViewModel` because this viewmodel is not held by a `Fragment`,
 * thus `onCleared()` is never called. Instead `cancelJobs()` is manually called by `RootViewModel`.
 */
class UserViewModel(private val userManager: UserManager, private val user: User, userMasterPassword: String?) : ViewModel(), CoroutineScope {

    val username = MutableLiveData<String>()

    val lockTimeoutSetting = MutableLiveData<Int>()
    val hidePasswordsSetting = MutableLiveData<Boolean>()

    private val lockTimeoutSettingChangedObserver: (Int?) -> Unit = { applyCurrentUserSettings() }
    private val hidePasswordsSettingChangedObserver: (Boolean?) -> Unit = { applyCurrentUserSettings() }

    private var masterEncryptionKey: ByteArray? = null
        set(newMasterEncryptionKey) {
            if (field.optionalContentNotEquals(newMasterEncryptionKey)) {
                field = newMasterEncryptionKey
                onMasterEncryptionKeyWasSet(newMasterEncryptionKey)
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

    init {
        username.value = user.username

        // If the master password was supplied (only on login), directly unlock resources
        if (userMasterPassword != null) {
            launch {
                try {
                    unlockMasterEncryptionKey(userMasterPassword)
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
                val masterKeySalt = user.masterKeyDerivationInformation.salt
                val masterKeyIterationCount = user.masterKeyDerivationInformation.iterationCount
                masterKey = KeyDerivation.deriveAES256KeyFromPassword(masterPassword, masterKeySalt, masterKeyIterationCount)

                val decryptedProtectedMasterEncryptionKey = user.masterEncryptionKey.decrypt(masterKey) {
                    CryptographicKey.deserialize(it)
                }

                if (decryptedProtectedMasterEncryptionKey != null) {
                    masterEncryptionKey = decryptedProtectedMasterEncryptionKey.key
                } else {
                    throw MissingMasterEncryptionKeyException()
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

    private fun onMasterEncryptionKeyWasSet(newMasterEncryptionKey: ByteArray?) {
        if (newMasterEncryptionKey != null) {
            L.d("UserViewModel", "setMasterEncryptionKey(): The master encryption key was set, decrypt and update user settings...")

            settings = user.settings.decrypt(newMasterEncryptionKey) {
                UserSettings.deserialize(it)
            }

            lockTimeoutSetting.postValue(settings?.lockTimeout)
            hidePasswordsSetting.postValue(settings?.hidePasswords)

            // Register observers after field initialisations to avoid initial observer calls
            registerObservers()
        } else {
            L.d("UserViewModel", "setMasterEncryptionKey(): The master encryption key was reset, clear user settings...")

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

    private fun applyCurrentUserSettings() {
        val newLockTimeoutSetting = lockTimeoutSetting.value
        val newHidePasswordsSetting = hidePasswordsSetting.value

        if (newLockTimeoutSetting != null && newHidePasswordsSetting != null) {
            // Update settings only if (still) set
            settings?.let {
                settings = it.copy(
                    lockTimeout = newLockTimeoutSetting,
                    hidePasswords = newHidePasswordsSetting
                )
            }
        }
    }

    private fun persistUserSettings() {
        // Execute encryption on the dispatcher for CPU load
        launch(Dispatchers.Default) {
            val masterEncryptionKey = masterEncryptionKey
            val settings = settings

            // Persist user with new settings only master encryption key and settings are (still) set
            if (masterEncryptionKey != null && settings != null) {
                user.settings.update(masterEncryptionKey, settings)
                userManager.updateUser(user)
            }
        }
    }

    class MissingMasterEncryptionKeyException : Exception()
    class UnlockFailedException(cause: Exception? = null) : Exception(cause)
}
