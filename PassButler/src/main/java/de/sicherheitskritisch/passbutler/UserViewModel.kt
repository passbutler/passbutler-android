package de.sicherheitskritisch.passbutler

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.NonNullValueGetterLiveData
import de.sicherheitskritisch.passbutler.base.clear
import de.sicherheitskritisch.passbutler.base.viewmodels.ModelBasedViewModel
import de.sicherheitskritisch.passbutler.base.viewmodels.SensibleDataViewModel
import de.sicherheitskritisch.passbutler.crypto.Biometrics
import de.sicherheitskritisch.passbutler.crypto.Derivation
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.EncryptedValue
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import de.sicherheitskritisch.passbutler.crypto.models.ProtectedValue
import de.sicherheitskritisch.passbutler.database.models.Item
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
) : ViewModel(), ModelBasedViewModel<User>, SensibleDataViewModel<String>, CoroutineScope {

    val userType
        get() = userManager.loggedInStateStorage.userType

    val encryptedMasterPassword
        get() = userManager.loggedInStateStorage.encryptedMasterPassword

    val username
        get() = user.username

    val itemViewModels = MutableLiveData<List<ItemViewModel>>()

    val automaticLockTimeout = MutableLiveData<Int?>()
    val hidePasswordsEnabled = MutableLiveData<Boolean?>()

    val biometricUnlockAvailable = NonNullValueGetterLiveData {
        Biometrics.isHardwareCapable && Biometrics.isKeyguardSecure && Biometrics.hasEnrolledBiometrics
    }

    val biometricUnlockEnabled = NonNullValueGetterLiveData {
        biometricUnlockAvailable.value && userManager.loggedInStateStorage.encryptedMasterPassword != null
    }

    // Save instance to be able to unregister observer on this instance
    private val items = userManager.findItems()

    private val itemsObserver = Observer<List<Item>?> { newItems ->
        updateItemsJob?.cancel()
        updateItemsJob = launch {
            val oldItemViewModels = itemViewModels.value
            val newItemViewModels = newItems?.mapNotNull { newItem ->
                oldItemViewModels?.find { it.id == newItem.id } ?: run {
                    val itemAuthorization = userManager.findItemAuthorization(newItem)

                    if (itemAuthorization != null) {
                        ItemViewModel(newItem, itemAuthorization)
                    } else {
                        L.e("UserViewModel", "ItemsObserver: The item authorization of item ${newItem.id} was not found - skip item!")
                        null
                    }
                }
            } ?: listOf()

            // Decrypt new items that are still not unlocked
            newItemViewModels
                .filter { it.sensibleDataLocked == false }
                .map {
                    async {
                        val itemEncryptionSecretKey = itemEncryptionSecretKey ?: throw IllegalStateException("The item encryption key is null despite item decryption was started!")
                        it.decryptSensibleData(itemEncryptionSecretKey)
                    }
                }
                .forEach {
                    // TODO: exclude items that are failed and clear them
                    // L.e("ItemViewModel", "decryptSensibleData(): The item could not be decrypted!", e)
                    it.await()
                }

            withContext(Dispatchers.Main) {
                itemViewModels.value = newItemViewModels
            }
        }
    }

    private var updateItemsJob: Job? = null

    private val automaticLockTimeoutChangedObserver = Observer<Int?> { applyUserSettings() }
    private val hidePasswordsEnabledChangedObserver = Observer<Boolean?> { applyUserSettings() }

    private var masterEncryptionKey: ByteArray? = null
    private var itemEncryptionSecretKey: ByteArray? = null
    private var settings: UserSettings? = null

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
            launch(Dispatchers.Default) {
                try {
                    decryptSensibleData(masterPassword)
                } catch (e: UnlockFailedException) {
                    L.w("UserViewModel", "init(): The initial unlock of the resources after login failed - logout user because of unusable state!", e)
                    logout()
                }
            }
        }
    }

    fun cancelJobs() {
        L.d("UserViewModel", "cancelJobs()")
        coroutineJob.cancel()
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    @Throws(UnlockFailedException::class)
    override suspend fun decryptSensibleData(masterPassword: String) {
        L.d("UserViewModel", "decryptSensibleData()")

        try {
            masterEncryptionKey = withContext(Dispatchers.Default) {
                decryptMasterEncryptionKey(masterPassword).also { masterEncryptionKey ->
                    itemEncryptionSecretKey = decryptItemEncryptionSecretKey(masterEncryptionKey)

                    withContext(Dispatchers.Main) {
                        items.observeForever(itemsObserver)
                    }

                    val decryptedSettings = decryptUserSettings(masterEncryptionKey)

                    withContext(Dispatchers.Main) {
                        automaticLockTimeout.value = decryptedSettings.automaticLockTimeout
                        hidePasswordsEnabled.value = decryptedSettings.hidePasswords

                        // Register observers after field initialisations to avoid initial observer calls (but actually `LiveData` notifies observer nevertheless)
                        automaticLockTimeout.observeForever(automaticLockTimeoutChangedObserver)
                        hidePasswordsEnabled.observeForever(hidePasswordsEnabledChangedObserver)
                    }
                }
            }

        } catch (e: Exception) {
            // If the operation failed, reset everything to avoid a dirty state
            clearSensibleData()

            throw UnlockFailedException(e)
        }
    }

    override suspend fun clearSensibleData() {
        L.d("UserViewModel", "clearSensibleData()")

        masterEncryptionKey?.clear()
        masterEncryptionKey = null

        itemEncryptionSecretKey?.clear()
        itemEncryptionSecretKey = null

        withContext(Dispatchers.Main) {
            // First remove observer and than cancel the (possible) running update `ItemViewModel` list job
            items.removeObserver(itemsObserver)
            updateItemsJob?.cancel()

            itemViewModels.value?.forEach { it.clearSensibleData() }
        }

        withContext(Dispatchers.Main) {
            // Unregister observers before setting field reset to avoid unnecessary observer calls
            automaticLockTimeout.removeObserver(automaticLockTimeoutChangedObserver)
            hidePasswordsEnabled.removeObserver(hidePasswordsEnabledChangedObserver)

            automaticLockTimeout.value = null
            hidePasswordsEnabled.value = null
        }
    }

    fun updateItem(itemViewModel: ItemViewModel) {
        launch(Dispatchers.IO) {
            val item = itemViewModel.createModel()

            val now = Date()
            item.modified = now

            userManager.updateItem(item)
        }
    }

    fun deleteItem(itemViewModel: ItemViewModel) {
        launch(Dispatchers.IO) {
            val item = itemViewModel.createModel()

            val now = Date()
            item.modified = now

            userManager.deleteItem(item)
        }
    }

    @Throws(SynchronizeDataFailedException::class)
    suspend fun synchronizeData() {
        try {
            userManager.synchronize()
        } catch (e: Exception) {
            throw SynchronizeDataFailedException(e)
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

                val user = createModel()
                userManager.updateUser(user)

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
                // Test if master password is correct via thrown exception
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

    suspend fun logout() {
        userManager.logoutUser()
    }

    @Throws(DecryptMasterEncryptionKeyFailedException::class)
    private fun decryptMasterEncryptionKey(masterPassword: String): ByteArray {
        var masterKey: ByteArray? = null

        return try {
            masterKey = Derivation.deriveMasterKey(masterPassword, masterKeyDerivationInformation)

            val decryptedProtectedMasterEncryptionKey = protectedMasterEncryptionKey.decrypt(masterKey, CryptographicKey.Deserializer)
            decryptedProtectedMasterEncryptionKey.key
        } catch (e: Exception) {
            throw DecryptMasterEncryptionKeyFailedException(e)
        } finally {
            masterKey?.clear()
        }
    }

    @Throws(DecryptItemEncryptionSecretKeyFailedException::class)
    private fun decryptItemEncryptionSecretKey(masterEncryptionKey: ByteArray): ByteArray {
        return try {
            protectedItemEncryptionSecretKey.decrypt(masterEncryptionKey, CryptographicKey.Deserializer).key
        } catch (e: Exception) {
            throw DecryptItemEncryptionSecretKeyFailedException(e)
        }
    }

    @Throws(DecryptUserSettingsFailedException::class)
    private fun decryptUserSettings(masterEncryptionKey: ByteArray): UserSettings {
        return try {
            protectedSettings.decrypt(masterEncryptionKey, UserSettings.Deserializer)
        } catch (e: Exception) {
            throw DecryptUserSettingsFailedException(e)
        }
    }

    private fun applyUserSettings() {
        val automaticLockTimeoutValue = automaticLockTimeout.value
        val hidePasswordsEnabledValue = hidePasswordsEnabled.value

        if (automaticLockTimeoutValue != null && hidePasswordsEnabledValue != null) {
            val updatedSettings = UserSettings(automaticLockTimeoutValue, hidePasswordsEnabledValue)

            // Only persist settings if changed and not uninitialized
            if (updatedSettings != settings && settings != null) {
                persistUserSettings(updatedSettings)
            }

            settings = updatedSettings
        }
    }

    private fun persistUserSettings(settings: UserSettings) {
        launch(Dispatchers.Default) {
            val masterEncryptionKey = masterEncryptionKey

            // Only persist if master encryption key is set (user logged-in and state unlocked)
            if (masterEncryptionKey != null) {
                try {
                    protectedSettings.update(masterEncryptionKey, settings)

                    val user = createModel()
                    userManager.updateUser(user)
                } catch (e: Exception) {
                    L.w("UserViewModel", "persistUserSettings(): The user settings could not be updated!", e)
                }
            }
        }
    }

    override fun createModel(): User {
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
    class DecryptItemEncryptionSecretKeyFailedException(cause: Exception? = null) : Exception(cause)
    class DecryptUserSettingsFailedException(cause: Exception? = null) : Exception(cause)
    class SynchronizeDataFailedException(cause: Exception? = null) : Exception(cause)
    class UpdateMasterPasswordFailedException(cause: Exception? = null) : Exception(cause)
    class EnableBiometricUnlockFailedException(cause: Exception? = null) : Exception(cause)
    class DisableBiometricUnlockFailedException(cause: Exception? = null) : Exception(cause)

    companion object {
        const val BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME = "MasterPasswordEncryptionKey"
    }
}
