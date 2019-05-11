package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.clear
import de.sicherheitskritisch.passbutler.base.optionalContentNotEquals
import de.sicherheitskritisch.passbutler.crypto.KeyDerivation
import de.sicherheitskritisch.passbutler.database.models.CryptographicKey
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

// TODO: Use CoroutineScopeAndroidViewModel?
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

                    // Unregister observers before setting field reset to avoid unnecessary observer calls via the settings `LiveData` fields
                    unregisterObservers()

                    settings = null
                    lockTimeoutSetting.postValue(null)
                    hidePasswordsSetting.postValue(null)
                }
            }
        }

    private var settings: UserSettings? = null
        set(newSettingsValue) {
            if (newSettingsValue != field) {
                val fieldWasInitialized = (field == null)
                field = newSettingsValue

                // Persist not if the field was initialized
                if (!fieldWasInitialized) {
                    persistUserSettings(newSettingsValue)
                }
            }
        }

    // TODO: cancel job some time?
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + coroutineJob

    private val coroutineJob = SupervisorJob()

    init {
        username.value = user.username

        // If the master password was supplied (only on login), directly unlock resources
        if (userMasterPassword != null) {
            launch {
                // TODO: catch UnlockFailedException here?
                unlockCryptoResources(userMasterPassword)
            }
        }
    }

    @Throws(UnlockFailedException::class)
    suspend fun unlockCryptoResources(masterPassword: String) {
        // Execute deserialization/decryption on the dispatcher for CPU load
        withContext(Dispatchers.Default) {
            val masterKeySalt = user.masterKeyDerivationInformation.salt
            val masterKeyIterationCount = user.masterKeyDerivationInformation.iterationCount
            val masterKey = KeyDerivation.deriveAES256KeyFromPassword(masterPassword, masterKeySalt, masterKeyIterationCount)

            val decryptedProtectedMasterEncryptionKey = user.masterEncryptionKey.decrypt(masterKey) {
                CryptographicKey.deserialize(it)
            }

            try {
                if (decryptedProtectedMasterEncryptionKey != null) {
                    masterEncryptionKey = decryptedProtectedMasterEncryptionKey.key
                } else {
                    throw UnlockFailedException()
                }
            } finally {
                masterKey.clear()
            }
        }
    }

    suspend fun clearCryptoResources() {
        // Execute on the same dispatcher as the `unlockCryptoResources` for uniformity reasons
        withContext(Dispatchers.Default) {
            masterEncryptionKey?.clear()
            masterEncryptionKey = null
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
            val updatedSettings = settings?.copy(
                lockTimeout = newLockTimeoutSetting,
                hidePasswords = newHidePasswordsSetting
            )

            updatedSettings?.let {
                settings = it
            }
        }
    }

    private fun persistUserSettings(newSettingsValue: UserSettings?) {
        // Execute encryption on the dispatcher for CPU load
        launch(Dispatchers.Default) {
            val masterEncryptionKey = masterEncryptionKey

            if (masterEncryptionKey != null && newSettingsValue != null) {
                user.settings.update(masterEncryptionKey, newSettingsValue)
                userManager.updateUser(user)
            }
        }
    }

    class UnlockFailedException(cause: Exception? = null) : Exception(cause)
}
