package de.passbutler.app

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import de.passbutler.app.base.NonNullMutableLiveData
import de.passbutler.app.base.NonNullValueGetterLiveData
import de.passbutler.app.base.viewmodels.ManualCancelledCoroutineScopeViewModel
import de.passbutler.app.crypto.Biometrics
import de.passbutler.common.UserManager
import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.addSignal
import de.passbutler.common.base.clear
import de.passbutler.common.base.resultOrThrowException
import de.passbutler.common.base.signal
import de.passbutler.common.crypto.Derivation
import de.passbutler.common.crypto.models.CryptographicKey
import de.passbutler.common.crypto.models.EncryptedValue
import de.passbutler.common.crypto.models.KeyDerivationInformation
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.database.models.Item
import de.passbutler.common.database.models.User
import de.passbutler.common.database.models.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger
import java.util.*
import javax.crypto.Cipher

/**
 * This viewmodel is held by `RootViewModel` end contains the business logic for logged-in user.
 * The method `cancelJobs()` is manually called by `RootViewModel`.
 */
class UserViewModel private constructor(
    private val userManager: UserManager,
    private val user: User,
    private var masterPasswordAuthenticationHash: String,
    private val masterKeyDerivationInformation: KeyDerivationInformation,
    private val protectedMasterEncryptionKey: ProtectedValue<CryptographicKey>,
    val itemEncryptionPublicKey: CryptographicKey,
    private val protectedItemEncryptionSecretKey: ProtectedValue<CryptographicKey>,
    private val protectedSettings: ProtectedValue<UserSettings>,
    masterPassword: String?
) : ManualCancelledCoroutineScopeViewModel() {

    val localRepository
        get() = userManager.localRepository

    val loggedInStateStorage
        get() = userManager.loggedInStateStorage

    val userType
        get() = loggedInStateStorage.value?.userType

    val encryptedMasterPassword
        get() = loggedInStateStorage.value?.encryptedMasterPassword

    val lastSuccessfulSyncDate
        get() = loggedInStateStorage.value?.lastSuccessfulSyncDate

    val webservices
        get() = userManager.webservices

    val id: String
        get() = user.id

    val username = NonNullMutableLiveData(user.username)

    val itemViewModels = NonNullMutableLiveData<List<ItemViewModel>>(emptyList())

    val automaticLockTimeout = MutableLiveData<Int?>()
    val hidePasswordsEnabled = MutableLiveData<Boolean?>()

    val biometricUnlockAvailable = NonNullValueGetterLiveData {
        Biometrics.isHardwareCapable && Biometrics.isKeyguardSecure && Biometrics.hasEnrolledBiometrics
    }

    val biometricUnlockEnabled = NonNullValueGetterLiveData {
        biometricUnlockAvailable.value && encryptedMasterPassword != null
    }

    private val updateItemViewModelsSignal = signal {
        updateItemViewModels()
    }

    private var itemViewModelsUpdateJob: Job? = null

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
        Logger.debug("Create new UserViewModel ($this)")

        // If the master password was supplied (only on login), directly unlock resources
        if (masterPassword != null) {
            launch {
                val decryptSensibleDataResult = decryptSensibleData(masterPassword)

                if (decryptSensibleDataResult is Failure) {
                    Logger.warn(decryptSensibleDataResult.throwable, "The initial unlock of the resources after login failed - logout user because of unusable state")
                    logout()
                }
            }
        }
    }

    fun createNewItemEditingViewModel(): ItemEditingViewModel {
        val itemModel = ItemEditingViewModel.ItemModel.New
        return ItemEditingViewModel(itemModel, this, localRepository)
    }

    suspend fun decryptSensibleData(masterPassword: String): Result<Unit> {
        Logger.debug("Decrypt sensible data")

        return try {
            masterEncryptionKey = decryptMasterEncryptionKey(masterPassword).resultOrThrowException().also { masterEncryptionKey ->
                itemEncryptionSecretKey = protectedItemEncryptionSecretKey.decrypt(masterEncryptionKey, CryptographicKey.Deserializer).resultOrThrowException().key

                registerUpdateItemViewModelsSignal()

                val decryptedSettings = protectedSettings.decrypt(masterEncryptionKey, UserSettings.Deserializer).resultOrThrowException()
                registerUserSettingObservers(decryptedSettings)
            }

            Success(Unit)
        } catch (exception: Exception) {
            Logger.warn("The sensible data could not be decrypted - clear sensible data to avoid corrupt state")
            clearSensibleData()

            Failure(exception)
        }
    }

    suspend fun decryptMasterEncryptionKey(masterPassword: String): Result<ByteArray> {
        var masterKey: ByteArray? = null

        return try {
            masterKey = Derivation.deriveMasterKey(masterPassword, masterKeyDerivationInformation).resultOrThrowException()

            val decryptedMasterEncryptionKey = protectedMasterEncryptionKey.decrypt(masterKey, CryptographicKey.Deserializer).resultOrThrowException()
            Success(decryptedMasterEncryptionKey.key)
        } catch (exception: Exception) {
            // Wrap the thrown exception to be able to determine if this call failed (used to show concrete error string in UI)
            val wrappedException = DecryptMasterEncryptionKeyFailedException(exception)
            Failure(wrappedException)
        } finally {
            masterKey?.clear()
        }
    }

    private fun registerUpdateItemViewModelsSignal() {
        userManager.itemsOrItemAuthorizationsChanged.addSignal(updateItemViewModelsSignal, true)
    }

    private suspend fun registerUserSettingObservers(decryptedSettings: UserSettings) {
        withContext(Dispatchers.Main) {
            automaticLockTimeout.value = decryptedSettings.automaticLockTimeout
            hidePasswordsEnabled.value = decryptedSettings.hidePasswords

            // Register observers after field initialisations to avoid initial observer calls (but actually `LiveData` notifies observer nevertheless)
            automaticLockTimeout.observeForever(automaticLockTimeoutChangedObserver)
            hidePasswordsEnabled.observeForever(hidePasswordsEnabledChangedObserver)
        }
    }

    suspend fun clearSensibleData() {
        Logger.debug("Clear sensible data")

        // Be sure all observers that uses crypto resources cleared afterwards are unregistered first
        unregisterUpdateItemViewModelsSignal()
        unregisterUserSettingObservers()

        masterEncryptionKey?.clear()
        masterEncryptionKey = null

        itemEncryptionSecretKey?.clear()
        itemEncryptionSecretKey = null

        itemViewModels.value.forEach {
            it.clearSensibleData()
        }
    }

    private fun unregisterUpdateItemViewModelsSignal() {
        userManager.itemsOrItemAuthorizationsChanged.removeSignal(updateItemViewModelsSignal)

        // Finally cancel the (possible) running "update item viewmodels" job
        itemViewModelsUpdateJob?.cancel()
    }

    private suspend fun unregisterUserSettingObservers() {
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
        Logger.debug("Update master password")

        val masterEncryptionKey = masterEncryptionKey ?: throw IllegalStateException("The master encryption key is null despite it was tried to update the master password!")
        var newMasterKey: ByteArray? = null

        return try {
            val newLocalMasterPasswordAuthenticationHash = Derivation.deriveLocalAuthenticationHash(username.value, newMasterPassword).resultOrThrowException()
            val newServerMasterPasswordAuthenticationHash = Derivation.deriveServerAuthenticationHash(newLocalMasterPasswordAuthenticationHash).resultOrThrowException()
            masterPasswordAuthenticationHash = newServerMasterPasswordAuthenticationHash

            // TODO: Sync to remote first, only if it worked update locally because otherwise authentication will fail!
            userManager.reinitializeAuthWebservice(newMasterPassword)

            newMasterKey = Derivation.deriveMasterKey(newMasterPassword, masterKeyDerivationInformation).resultOrThrowException()
            protectedMasterEncryptionKey.update(newMasterKey, CryptographicKey(masterEncryptionKey)).resultOrThrowException()

            val user = createModel()
            localRepository.updateUser(user)

            // After all mandatory changes, try to disable biometric unlock because master password re-encryption would require complex flow with biometric authentication UI
            val disableBiometricUnlockResult = disableBiometricUnlock()

            if (disableBiometricUnlockResult is Failure) {
                Logger.warn(disableBiometricUnlockResult.throwable, "The biometric unlock could not be disabled")
            }

            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
        } finally {
            newMasterKey?.clear()
        }
    }

    suspend fun enableBiometricUnlock(initializedSetupBiometricUnlockCipher: Cipher, masterPassword: String): Result<Unit> {
        Logger.debug("Enable biometric unlock")

        return try {
            // Test if master password is correct via thrown exception
            decryptMasterEncryptionKey(masterPassword).resultOrThrowException()

            val encryptedMasterPasswordInitializationVector = initializedSetupBiometricUnlockCipher.iv
            val encryptedMasterPasswordValue = Biometrics.encryptData(initializedSetupBiometricUnlockCipher, masterPassword.toByteArray()).resultOrThrowException()

            val encryptedMasterPassword = EncryptedValue(encryptedMasterPasswordInitializationVector, encryptedMasterPasswordValue)
            userManager.updateLoggedInStateStorage {
                this.encryptedMasterPassword = encryptedMasterPassword
            }

            withContext(Dispatchers.Main) {
                biometricUnlockEnabled.notifyChange()
            }

            Success(Unit)
        } catch (exception: Exception) {
            Logger.warn("The biometric unlock could not be enabled - disable biometric unlock to avoid corrupt state")
            disableBiometricUnlock()

            Failure(exception)
        }
    }

    suspend fun disableBiometricUnlock(): Result<Unit> {
        Logger.debug("Disable biometric unlock")

        return try {
            Biometrics.removeKey(BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME).resultOrThrowException()

            userManager.updateLoggedInStateStorage {
                encryptedMasterPassword = null
            }

            withContext(Dispatchers.Main) {
                biometricUnlockEnabled.notifyChange()
            }

            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    suspend fun logout(): Result<Unit> {
        userManager.logoutUser()
        return Success(Unit)
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
                    protectedSettings.update(masterEncryptionKey, settings).resultOrThrowException()

                    val user = createModel()
                    localRepository.updateUser(user)
                } catch (exception: Exception) {
                    Logger.warn(exception, "The user settings could not be updated")
                }
            }
        }
    }

    private fun createModel(): User {
        // Only update fields that are allowed to modify (server reject changes on non-allowed field anyway)
        return user.copy(
            username = username.value,
            masterPasswordAuthenticationHash = masterPasswordAuthenticationHash,
            masterKeyDerivationInformation = masterKeyDerivationInformation,
            masterEncryptionKey = protectedMasterEncryptionKey,
            settings = protectedSettings,
            modified = Date()
        )
    }

    private fun updateItemViewModels() {
        itemViewModelsUpdateJob?.cancel()
        itemViewModelsUpdateJob = launch {
            val newItems = localRepository.findAllItems()
            val updatedItemViewModels = createItemViewModels(newItems)
            val decryptedItemViewModels = decryptItemViewModels(updatedItemViewModels)

            withContext(Dispatchers.Main) {
                Logger.debug("Update item viewmodels: itemViewModels.size = ${decryptedItemViewModels.size}")
                itemViewModels.value = decryptedItemViewModels
            }
        }
    }

    private suspend fun createItemViewModels(newItems: List<Item>): List<ItemViewModel> {
        val existingItemViewModels = itemViewModels.value
        Logger.debug("Create item viewmodels: newItems.size = ${newItems.size}, existingItemViewModels.size = ${existingItemViewModels.size}")

        // Load list once instead query user for every item later
        val usersIdUsernameMapping = localRepository.findAllUsers().associate {
            it.id to it.username
        }

        val newItemViewModels = newItems
            .mapNotNull { item ->
                // Check if the user has a non-deleted item authorization to access the item
                val itemAuthorization = localRepository.findItemAuthorizationForItem(item).firstOrNull {
                    it.userId == id && !it.deleted
                }

                if (itemAuthorization != null) {
                    existingItemViewModels
                        .find {
                            // Try to find an existing (already decrypted) item viewmodel to avoid decrypting again
                            it.id == item.id
                        }
                        ?.takeIf {
                            // Only take existing item viewmodel if model of item and item authorization is the same
                            it.item == item && it.itemAuthorization == itemAuthorization
                        }
                        ?: run {
                            val itemOwnerUserId = item.userId
                            val itemOwnerUsername = usersIdUsernameMapping[itemOwnerUserId]

                            if (itemOwnerUsername != null) {
                                Logger.debug("Create new viewmodel for item '${item.id}' because recycling was not possible")

                                // No existing item viewmodel was found, thus a new must be created for item
                                ItemViewModel(item, itemAuthorization, itemOwnerUsername, this, localRepository)
                            } else {
                                Logger.warn("The owner username could not be mapped for user id = $itemOwnerUserId!")
                                null
                            }
                        }
                } else {
                    Logger.debug("A non-deleted item authorization of user for item '${item.id}' was not found - skip item")
                    null
                }
            }
            .sortedBy { it.created }

        return newItemViewModels
    }

    private suspend fun decryptItemViewModels(itemViewModels: List<ItemViewModel>): List<ItemViewModel> {
        val itemEncryptionSecretKey = itemEncryptionSecretKey ?: throw IllegalStateException("The item encryption key is null despite item decryption was started!")

        return itemViewModels
            .map { itemViewModel ->
                // Start parallel decryption
                itemViewModel to async {
                    // Only decrypt if not already decrypted
                    if (itemViewModel.itemData == null) {
                        val itemDecryptionResult = itemViewModel.decryptSensibleData(itemEncryptionSecretKey)

                        when (itemDecryptionResult) {
                            is Success -> Logger.debug("The item viewmodel '${itemViewModel.id}' was decrypted successfully")
                            is Failure -> Logger.warn(itemDecryptionResult.throwable, "The item viewmodel '${itemViewModel.id}' could not be decrypted")
                        }

                        itemDecryptionResult
                    } else {
                        Logger.debug("The item viewmodel '${itemViewModel.id}' is already decrypted")
                        Success(Unit)
                    }
                }
            }
            .mapNotNull {
                // Await results afterwards
                val itemViewModel = it.first
                val itemDecryptionResult = it.second.await()

                when (itemDecryptionResult) {
                    is Success -> itemViewModel
                    is Failure -> null
                }
            }
    }

    companion object {
        const val BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME = "MasterPasswordEncryptionKey"
    }
}

class DecryptMasterEncryptionKeyFailedException(cause: Exception? = null) : Exception(cause)
