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
import kotlin.coroutines.CoroutineContext

class UserViewModel(private val userManager: UserManager, private val user: User) : ViewModel(), CoroutineScope {

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
                    L.d("UserViewModel", "setMasterEncryptionKey(): The master encryption key was unset, clear user settings...")

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
                field = newSettingsValue
                persistUserSettings(newSettingsValue)
            }
        }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + coroutineJob

    private val coroutineJob = SupervisorJob()

    init {
        username.value = user.username

        // TODO: Remove hardcoded password
        unlockCryptoResources("1234")
    }

    fun unlockCryptoResources(masterPassword: String) {
        // Execute deserialization/decryption on the dispatcher for CPU load
        launch(Dispatchers.Default) {
            val masterKeySalt = user.masterKeyDerivationInformation.salt
            val masterKeyIterationCount = user.masterKeyDerivationInformation.iterationCount
            val masterKey = KeyDerivation.deriveAES256KeyFromPassword(masterPassword, masterKeySalt, masterKeyIterationCount)

            masterEncryptionKey = user.masterEncryptionKey.decrypt(masterKey) {
                CryptographicKey.deserialize(it)
            }?.key

            masterKey.clear()
        }
    }

    fun clearCryptoResources() {
        launch(Dispatchers.Default) {
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
            settings?.copy(
                lockTimeout = newLockTimeoutSetting,
                hidePasswords = newHidePasswordsSetting
            )?.let { updatedSettings ->
                settings = updatedSettings
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
}
