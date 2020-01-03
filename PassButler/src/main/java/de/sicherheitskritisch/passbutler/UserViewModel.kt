package de.sicherheitskritisch.passbutler

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import de.sicherheitskritisch.passbutler.base.Failure
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.NonNullValueGetterLiveData
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.Success
import de.sicherheitskritisch.passbutler.base.clear
import de.sicherheitskritisch.passbutler.base.resultOrThrowException
import de.sicherheitskritisch.passbutler.base.viewmodels.ManualCancelledCoroutineScopeViewModel
import de.sicherheitskritisch.passbutler.base.viewmodels.ModelBasedViewModel
import de.sicherheitskritisch.passbutler.crypto.Biometrics
import de.sicherheitskritisch.passbutler.crypto.Derivation
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.EncryptedValue
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import de.sicherheitskritisch.passbutler.crypto.models.ProtectedValue
import de.sicherheitskritisch.passbutler.database.models.Item
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.crypto.Cipher

/**
 * This viewmodel is held by `RootViewModel` end contains the business logic for logged-in user.
 * The method `cancelJobs()` is manually called by `RootViewModel`.
 */
class UserViewModel private constructor(
    val userManager: UserManager,
    private val user: User,
    private var masterPasswordAuthenticationHash: String,
    private val masterKeyDerivationInformation: KeyDerivationInformation,
    private val protectedMasterEncryptionKey: ProtectedValue<CryptographicKey>,
    val itemEncryptionPublicKey: CryptographicKey,
    private val protectedItemEncryptionSecretKey: ProtectedValue<CryptographicKey>,
    private val protectedSettings: ProtectedValue<UserSettings>,
    masterPassword: String?
) : ManualCancelledCoroutineScopeViewModel(), ModelBasedViewModel<User> {

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

    private var itemsObservable: LiveData<List<Item>>? = null

    private val itemsObserver = ItemsChangedObserver()
    private var itemsObserverUpdateJob: Job? = null

    private val automaticLockTimeoutChangedObserver = Observer<Int?> { applyUserSettings() }
    private val hidePasswordsEnabledChangedObserver = Observer<Boolean?> { applyUserSettings() }

    private var masterEncryptionKey: ByteArray? = null
    private var itemEncryptionSecretKey: ByteArray? = null
    private var settings: UserSettings? = null

    private var persistUserSettingsJob: Job? = null

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
                val decryptSensibleDataResult = decryptSensibleData(masterPassword)

                if (decryptSensibleDataResult is Failure) {
                    L.w("UserViewModel", "init(): The initial unlock of the resources after login failed - logout user because of unusable state!", decryptSensibleDataResult.throwable)
                    logout()
                }
            }
        }
    }

    suspend fun decryptSensibleData(masterPassword: String): Result<Unit> {
        L.d("UserViewModel", "decryptSensibleData()")

        return try {
            masterEncryptionKey = decryptMasterEncryptionKey(masterPassword).resultOrThrowException().also { masterEncryptionKey ->
                itemEncryptionSecretKey = decryptItemEncryptionSecretKey(masterEncryptionKey).resultOrThrowException()

                withContext(Dispatchers.Main) {
                    // Save instance to be able to unregister observer on exact this instance
                    itemsObservable = userManager.itemsObservable()
                    itemsObservable?.observeForever(itemsObserver)
                }

                val decryptedSettings = decryptUserSettings(masterEncryptionKey).resultOrThrowException()

                withContext(Dispatchers.Main) {
                    automaticLockTimeout.value = decryptedSettings.automaticLockTimeout
                    hidePasswordsEnabled.value = decryptedSettings.hidePasswords

                    // Register observers after field initialisations to avoid initial observer calls (but actually `LiveData` notifies observer nevertheless)
                    automaticLockTimeout.observeForever(automaticLockTimeoutChangedObserver)
                    hidePasswordsEnabled.observeForever(hidePasswordsEnabledChangedObserver)
                }
            }

            Success(Unit)
        } catch (exception: Exception) {
            L.w("UserViewModel", "decryptSensibleData(): The sensible data could not be decrypted - clear sensible data to avoid corrupt state!")
            clearSensibleData()

            Failure(exception)
        }
    }

    suspend fun clearSensibleData() {
        L.d("UserViewModel", "clearSensibleData()")

        masterEncryptionKey?.clear()
        masterEncryptionKey = null

        itemEncryptionSecretKey?.clear()
        itemEncryptionSecretKey = null

        withContext(Dispatchers.Main) {
            // First remove observer and than cancel the (possible) running update `ItemViewModel` list job
            itemsObservable?.removeObserver(itemsObserver)
            itemsObserverUpdateJob?.cancel()

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

    suspend fun synchronizeData(): Result<Unit> {
        return userManager.synchronize()
    }

    suspend fun updateMasterPassword(newMasterPassword: String): Result<Unit> {
        L.d("UserViewModel", "updateMasterPassword()")

        return withContext(Dispatchers.Default) {
            var newMasterKey: ByteArray? = null

            try {
                val masterEncryptionKey = masterEncryptionKey ?: throw IllegalStateException("The master encryption key is null despite it was tried to update the master password!")

                val newLocalMasterPasswordAuthenticationHash = Derivation.deriveLocalAuthenticationHash(username, newMasterPassword)
                val newServerMasterPasswordAuthenticationHash = Derivation.deriveServerAuthenticationHash(newLocalMasterPasswordAuthenticationHash)
                masterPasswordAuthenticationHash = newServerMasterPasswordAuthenticationHash

                newMasterKey = Derivation.deriveMasterKey(newMasterPassword, masterKeyDerivationInformation)
                protectedMasterEncryptionKey.update(newMasterKey, CryptographicKey(masterEncryptionKey))

                // Disable biometric unlock because master password re-encryption would require biometric authentication and made flow more complex
                disableBiometricUnlock().resultOrThrowException()

                val user = createModel()
                userManager.updateUser(user)

                // The auth webservice needs to re-initialized because of master password change
                userManager.initializeAuthWebservice(newMasterPassword)

                Success(Unit)
            } catch (exception: Exception) {
                Failure(exception)
            } finally {
                newMasterKey?.clear()
            }
        }
    }

    suspend fun enableBiometricUnlock(initializedSetupBiometricUnlockCipher: Cipher, masterPassword: String): Result<Unit> {
        L.d("UserViewModel", "enableBiometricUnlock()")

        return withContext(Dispatchers.Default) {
            try {
                // Test if master password is correct via thrown exception
                decryptMasterEncryptionKey(masterPassword)

                val encryptedMasterPasswordInitializationVector = initializedSetupBiometricUnlockCipher.iv
                val encryptedMasterPassword = Biometrics.encryptData(initializedSetupBiometricUnlockCipher, masterPassword.toByteArray())

                userManager.loggedInStateStorage.encryptedMasterPassword = EncryptedValue(encryptedMasterPasswordInitializationVector, encryptedMasterPassword)
                userManager.loggedInStateStorage.persist()

                biometricUnlockEnabled.notifyChange()

                Success(Unit)
            } catch (exception: Exception) {
                L.w("UserViewModel", "enableBiometricUnlock(): The biometric unlock could not be enabled - disable biometric unlock to avoid corrupt state!")
                disableBiometricUnlock()

                Failure(exception)
            }
        }
    }

    suspend fun disableBiometricUnlock(): Result<Unit> {
        L.d("UserViewModel", "disableBiometricUnlock()")

        return withContext(Dispatchers.IO) {
            try {
                Biometrics.removeKey(BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME)

                userManager.loggedInStateStorage.encryptedMasterPassword = null
                userManager.loggedInStateStorage.persist()

                biometricUnlockEnabled.notifyChange()

                Success(Unit)
            } catch (exception: Exception) {
                Failure(exception)
            }
        }
    }

    suspend fun logout(): Result<Unit> {
        userManager.logoutUser()
        return Success(Unit)
    }

    private suspend fun decryptMasterEncryptionKey(masterPassword: String): Result<ByteArray> {
        return withContext(Dispatchers.Default) {
            var masterKey: ByteArray? = null

            try {
                masterKey = Derivation.deriveMasterKey(masterPassword, masterKeyDerivationInformation)

                val decryptedMasterEncryptionKey = protectedMasterEncryptionKey.decrypt(masterKey, CryptographicKey.Deserializer)
                Success(decryptedMasterEncryptionKey.key)
            } catch (exception: Exception) {
                // Wrap the thrown exception to be able to determine if this call failed (used to show concrete error string in UI)
                val wrappedException = DecryptMasterEncryptionKeyFailedException(exception)

                Failure(wrappedException)
            } finally {
                masterKey?.clear()
            }
        }
    }

    private suspend fun decryptItemEncryptionSecretKey(masterEncryptionKey: ByteArray): Result<ByteArray> {
        return withContext(Dispatchers.Default) {
            try {
                val decryptedItemEncryptionSecretKey = protectedItemEncryptionSecretKey.decrypt(masterEncryptionKey, CryptographicKey.Deserializer)
                Success(decryptedItemEncryptionSecretKey.key)
            } catch (exception: Exception) {
                Failure(exception)
            }
        }
    }

    private suspend fun decryptUserSettings(masterEncryptionKey: ByteArray): Result<UserSettings> {
        return withContext(Dispatchers.Default) {
            try {
                val decryptedUserSettings = protectedSettings.decrypt(masterEncryptionKey, UserSettings.Deserializer)
                Success(decryptedUserSettings)
            } catch (exception: Exception) {
                Failure(exception)
            }
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
        persistUserSettingsJob?.cancel()
        persistUserSettingsJob = launch {
            val masterEncryptionKey = masterEncryptionKey

            // Only persist if master encryption key is set (user logged-in and state unlocked)
            if (masterEncryptionKey != null) {
                try {
                    withContext(Dispatchers.Default) {
                        protectedSettings.update(masterEncryptionKey, settings)
                    }

                    withContext(Dispatchers.IO) {
                        val user = createModel()
                        userManager.updateUser(user)
                    }
                } catch (exception: Exception) {
                    L.w("UserViewModel", "persistUserSettings(): The user settings could not be updated!", exception)
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

    class DecryptMasterEncryptionKeyFailedException(cause: Exception? = null) : Exception(cause)

    private inner class ItemsChangedObserver : Observer<List<Item>?> {
        override fun onChanged(newItems: List<Item>?) {
            itemsObserverUpdateJob?.cancel()
            itemsObserverUpdateJob = launch {
                val newItemViewModels = createItemViewModelList(newItems)
                val failedDecryptionItemViewModels = collectFailedDecryptionItemViewModels(newItemViewModels)
                val updatedItemViewModels = newItemViewModels
                    .subtract(failedDecryptionItemViewModels)
                    .sortedBy { it.created }

                withContext(Dispatchers.Main) {
                    L.d("ItemsChangedObserver", "onChanged(): Updated size of itemViewModels = ${updatedItemViewModels.size}")
                    itemViewModels.value = updatedItemViewModels
                }
            }
        }

        private suspend fun createItemViewModelList(newItems: List<Item>?): List<ItemViewModel> {
            val oldItemViewModels = itemViewModels.value
            val newItemViewModels = newItems
                ?.filter { !it.deleted }
                ?.mapNotNull { item ->
                    val itemAuthorization = userManager.findItemAuthorizationForItem(item)?.takeIf { !it.deleted }

                    if (itemAuthorization != null) { // TODO: Does not always work when create new item
                        oldItemViewModels
                            ?.find { it.id == item.id }
                            ?.takeIf { it.item == item && it.itemAuthorization == itemAuthorization }
                            ?: ItemViewModel(item, itemAuthorization, userManager)
                    } else {
                        L.w("ItemsChangedObserver", "createItemViewModelList(): The item authorization of item ${item.id} was not found - skip item!")
                        null
                    }
                }
                ?: emptyList()

            return newItemViewModels
        }

        private suspend fun collectFailedDecryptionItemViewModels(itemViewModels: List<ItemViewModel>): List<ItemViewModel> {
            val itemEncryptionSecretKey = itemEncryptionSecretKey ?: throw IllegalStateException("The item encryption key is null despite item decryption was started!")

            return itemViewModels
                .filter { it.itemData == null }
                .map {
                    it to async {
                        it.decryptSensibleData(itemEncryptionSecretKey)
                    }
                }
                .mapNotNull {
                    val itemViewModel = it.first
                    val itemDecryptSensibleDataResult = it.second.await()

                    when (itemDecryptSensibleDataResult) {
                        is Success -> {
                            L.d("ItemsChangedObserver", "collectFailedDecryptionItemViewModels(): The item ${itemViewModel.id} was decrypted successfully!")
                            null
                        }
                        is Failure -> {
                            L.w("ItemsChangedObserver", "collectFailedDecryptionItemViewModels(): The item ${itemViewModel.id} could not be decrypted!", itemDecryptSensibleDataResult.throwable)
                            itemViewModel
                        }
                    }
                }
        }
    }

    companion object {
        const val BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME = "MasterPasswordEncryptionKey"
    }
}
