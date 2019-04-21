package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserSettings

class UserViewModel(private val userManager: UserManager, private val user: User) : ViewModel() {

    val username = MutableLiveData<String>()
    val lockTimeout = MutableLiveData<Int>()

    private val settings = MutableLiveData<UserSettings>()

    // TODO: Where to get the key?
    private val userSettingsEncryptionKey
        get() = ByteArray(0)

    private val settingsObserver = Observer<UserSettings> {
        it?.let { newUserSettings ->
            // Update internal fields based on settings
            lockTimeout.value = newUserSettings.lockTimeout

            // Update settings in database
            user.settings.update(userSettingsEncryptionKey, newUserSettings)
            userManager.updateUser(user)
        }
    }

    init {
        username.value = user.username
        settings.value = user.settings.decrypt(userSettingsEncryptionKey) {
            UserSettings.deserialize(it)
        }

        // Register observers afterwards to avoid initial observer calls
        registerObservers()
    }

    private fun registerObservers() {
        settings.observeForever(settingsObserver)

        lockTimeout.observeForever {
            it?.let { newLockTimeout ->
                // Clone object with updated setting value to be sure object is different to make change detection more transparent
                val updatedUserSettings = settings.value?.copy(lockTimeout = newLockTimeout)
                settings.value = updatedUserSettings
            }
        }
    }
}
