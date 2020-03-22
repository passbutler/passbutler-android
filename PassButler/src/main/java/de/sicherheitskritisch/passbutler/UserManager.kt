package de.sicherheitskritisch.passbutler

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import de.sicherheitskritisch.passbutler.base.Failure
import de.sicherheitskritisch.passbutler.base.OptionalValueGetterLiveData
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.Success
import de.sicherheitskritisch.passbutler.base.byteSize
import de.sicherheitskritisch.passbutler.base.clear
import de.sicherheitskritisch.passbutler.base.resultOrThrowException
import de.sicherheitskritisch.passbutler.crypto.Derivation
import de.sicherheitskritisch.passbutler.crypto.EncryptionAlgorithm
import de.sicherheitskritisch.passbutler.crypto.MASTER_KEY_BIT_LENGTH
import de.sicherheitskritisch.passbutler.crypto.MASTER_KEY_ITERATION_COUNT
import de.sicherheitskritisch.passbutler.crypto.RandomGenerator
import de.sicherheitskritisch.passbutler.crypto.models.AuthToken
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.EncryptedValue
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import de.sicherheitskritisch.passbutler.crypto.models.ProtectedValue
import de.sicherheitskritisch.passbutler.database.AuthWebservice
import de.sicherheitskritisch.passbutler.database.Differentiation
import de.sicherheitskritisch.passbutler.database.LocalRepository
import de.sicherheitskritisch.passbutler.database.SynchronizationTask
import de.sicherheitskritisch.passbutler.database.UserWebservice
import de.sicherheitskritisch.passbutler.database.models.Item
import de.sicherheitskritisch.passbutler.database.models.ItemAuthorization
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import de.sicherheitskritisch.passbutler.database.remoteChangedItems
import de.sicherheitskritisch.passbutler.database.requestItemAuthorizationList
import de.sicherheitskritisch.passbutler.database.requestItemList
import de.sicherheitskritisch.passbutler.database.requestPublicUserList
import de.sicherheitskritisch.passbutler.database.requestUser
import de.sicherheitskritisch.passbutler.database.updateItemAuthorizationList
import de.sicherheitskritisch.passbutler.database.updateItemList
import de.sicherheitskritisch.passbutler.database.updateUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger
import java.net.SocketTimeoutException
import java.util.*

class UserManager(private val applicationContext: Context, private val localRepository: LocalRepository) {

    val userType = OptionalValueGetterLiveData {
        loggedInStateStorage?.userType
    }

    val encryptedMasterPassword = OptionalValueGetterLiveData {
        loggedInStateStorage?.encryptedMasterPassword
    }

    val loggedInUserResult = MutableLiveData<LoggedInUserResult?>()

    val webservicesInitialized
        get() = authWebservice != null && userWebservice != null

    private var loggedInStateStorage: LoggedInStateStorage? = null
    private var loggedInUser: User? = null

    private var authWebservice: AuthWebservice? = null
    private var userWebservice: UserWebservice? = null

    suspend fun loginRemoteUser(username: String, masterPassword: String, serverUrl: Uri): Result<Unit> {
        return try {
            val createdLoggedInStateStorage = createLoggedInStateStorage().apply {
                reset()

                userType = UserType.Remote(username, serverUrl, null, null)
                persist()
            }

            loggedInStateStorage = createdLoggedInStateStorage

            val createdAuthWebservice = createAuthWebservice(serverUrl, username, masterPassword)
            authWebservice = createdAuthWebservice
            userWebservice = createUserWebservice(serverUrl, createdAuthWebservice, this)

            val newUser = userWebservice.requestUser().resultOrThrowException()
            localRepository.insertUser(newUser)

            loggedInUser = newUser
            loggedInUserResult.postValue(LoggedInUserResult.PerformedLogin(newUser, masterPassword))

            Success(Unit)
        } catch (exception: Exception) {
            Logger.warn("The user could not be logged in - reset logged-in user to avoid corrupt state")
            resetLoggedInUser()

            Failure(exception)
        }
    }

    suspend fun loginLocalUser(username: String, masterPassword: String): Result<Unit> {
        var masterKey: ByteArray? = null
        var masterEncryptionKey: ByteArray? = null

        return try {
            loggedInStateStorage = createLoggedInStateStorage().apply {
                reset()

                userType = UserType.Local(username)
                persist()
            }

            val serverMasterPasswordAuthenticationHash = deriveServerMasterPasswordAuthenticationHash(username, masterPassword)
            val masterKeyDerivationInformation = createMasterKeyDerivationInformation()

            masterKey = Derivation.deriveMasterKey(masterPassword, masterKeyDerivationInformation).resultOrThrowException()
            masterEncryptionKey = EncryptionAlgorithm.Symmetric.AES256GCM.generateEncryptionKey().resultOrThrowException()

            val serializableMasterEncryptionKey = CryptographicKey(masterEncryptionKey)
            val protectedMasterEncryptionKey = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterKey, serializableMasterEncryptionKey).resultOrThrowException()

            val (itemEncryptionPublicKey, protectedItemEncryptionSecretKey) = generateItemEncryptionKeyPair(masterEncryptionKey)
            val protectedUserSettings = createUserSettings(masterEncryptionKey)
            val currentDate = Date()

            val newUser = User(
                username,
                serverMasterPasswordAuthenticationHash,
                masterKeyDerivationInformation,
                protectedMasterEncryptionKey,
                itemEncryptionPublicKey,
                protectedItemEncryptionSecretKey,
                protectedUserSettings,
                false,
                currentDate,
                currentDate
            )

            localRepository.insertUser(newUser)

            loggedInUser = newUser
            loggedInUserResult.postValue(LoggedInUserResult.PerformedLogin(newUser, masterPassword))

            Success(Unit)
        } catch (exception: Exception) {
            Logger.warn("The user could not be logged in - reset logged-in user to avoid corrupt state")
            resetLoggedInUser()

            Failure(exception)
        } finally {
            // Always active clear all sensible data before returning method
            masterKey?.clear()
            masterEncryptionKey?.clear()
        }
    }

    @Throws(Exception::class)
    private suspend fun deriveServerMasterPasswordAuthenticationHash(username: String, masterPassword: String): String {
        val masterPasswordAuthenticationHash = Derivation.deriveLocalAuthenticationHash(username, masterPassword).resultOrThrowException()
        val serverMasterPasswordAuthenticationHash = Derivation.deriveServerAuthenticationHash(masterPasswordAuthenticationHash).resultOrThrowException()
        return serverMasterPasswordAuthenticationHash
    }

    private suspend fun createMasterKeyDerivationInformation(): KeyDerivationInformation {
        val masterKeySalt = RandomGenerator.generateRandomBytes(MASTER_KEY_BIT_LENGTH.byteSize)
        val masterKeyIterationCount = MASTER_KEY_ITERATION_COUNT
        val masterKeyDerivationInformation = KeyDerivationInformation(masterKeySalt, masterKeyIterationCount)

        return masterKeyDerivationInformation
    }

    @Throws(Exception::class)
    private suspend fun generateItemEncryptionKeyPair(masterEncryptionKey: ByteArray): Pair<CryptographicKey, ProtectedValue<CryptographicKey>> {
        val itemEncryptionKeyPair = EncryptionAlgorithm.Asymmetric.RSA2048OAEP.generateKeyPair().resultOrThrowException()

        val serializableItemEncryptionPublicKey = CryptographicKey(itemEncryptionKeyPair.public.encoded)

        val serializableItemEncryptionSecretKey = CryptographicKey(itemEncryptionKeyPair.private.encoded)
        val protectedItemEncryptionSecretKey = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterEncryptionKey, serializableItemEncryptionSecretKey).resultOrThrowException()

        return Pair(serializableItemEncryptionPublicKey, protectedItemEncryptionSecretKey)
    }

    @Throws(Exception::class)
    private suspend fun createUserSettings(masterEncryptionKey: ByteArray): ProtectedValue<UserSettings> {
        val userSettings = UserSettings()
        val protectedUserSettings = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterEncryptionKey, userSettings).resultOrThrowException()

        return protectedUserSettings
    }

    suspend fun restoreLoggedInUser() {
        if (loggedInUser == null) {
            Logger.debug("Try to restore logged-in user")

            val restoredLoggedInStateStorage = createLoggedInStateStorage().apply {
                restore()
            }

            val restoredLoggedInUser = restoredLoggedInStateStorage.userType?.username?.let { loggedInUsername ->
                localRepository.findUser(loggedInUsername)
            }

            if (restoredLoggedInUser != null) {
                loggedInStateStorage = restoredLoggedInStateStorage
                loggedInUser = restoredLoggedInUser

                loggedInUserResult.postValue(LoggedInUserResult.RestoredLogin(restoredLoggedInUser))
            } else {
                loggedInUserResult.postValue(null)
            }
        } else {
            Logger.debug("Restore is not needed because already restored")
        }
    }

    private suspend fun createLoggedInStateStorage(): LoggedInStateStorage {
        return withContext(Dispatchers.IO) {
            val sharedPreferences = applicationContext.getSharedPreferences("UserManager", MODE_PRIVATE)
            LoggedInStateStorage(sharedPreferences, this@UserManager)
        }
    }

    suspend fun restoreWebservices(masterPassword: String) {
        Logger.debug("Restore webservices")

        try {
            val loggedInStateStorage = loggedInStateStorage ?: throw IllegalStateException("The LoggedInStateStorage is not initialized!")
            val remoteUserType = loggedInStateStorage.userType as? UserType.Remote ?: throw IllegalStateException("The logged-in user type is not remote!")
            val serverUrl = remoteUserType.serverUrl
            val username = remoteUserType.username

            val createdAuthWebservice = authWebservice ?: createAuthWebservice(serverUrl, username, masterPassword)
            val createdUserWebservice = userWebservice ?: createUserWebservice(serverUrl, createdAuthWebservice, this)

            // If everything worked, apply to fields
            this.authWebservice = createdAuthWebservice
            this.userWebservice = createdUserWebservice
        } catch (exception: Exception) {
            Logger.warn(exception, "The webservices could not be restored")
        }
    }

    @Throws(Exception::class)
    private suspend fun createAuthWebservice(serverUrl: Uri, username: String, masterPassword: String): AuthWebservice {
        val masterPasswordAuthenticationHash = Derivation.deriveLocalAuthenticationHash(username, masterPassword).resultOrThrowException()
        val authWebservice = AuthWebservice.create(serverUrl, username, masterPasswordAuthenticationHash)
        return authWebservice
    }

    private suspend fun createUserWebservice(serverUrl: Uri, authWebservice: AuthWebservice, userManager: UserManager): UserWebservice {
        val userWebservice = UserWebservice.create(serverUrl, authWebservice, userManager)
        return userWebservice
    }

    suspend fun reinitializeAuthWebservice(masterPassword: String) {
        authWebservice = null
        userWebservice = null
        restoreWebservices(masterPassword)
    }

    suspend fun updateAuthToken(authToken: AuthToken?) {
        val loggedInStateStorage = loggedInStateStorage ?: throw IllegalStateException("The LoggedInStateStorage is not initialized!")
        require(loggedInStateStorage.userType is UserType.Remote) { "The logged-in user type is not remote!" }

        loggedInStateStorage.userType?.asRemoteOrNull()?.authToken = authToken
        loggedInStateStorage.persist()
    }

    suspend fun updateEncryptedMasterPassword(encryptedMasterPassword: EncryptedValue?) {
        val loggedInStateStorage = loggedInStateStorage ?: throw IllegalStateException("The LoggedInStateStorage is not initialized!")

        loggedInStateStorage.encryptedMasterPassword = encryptedMasterPassword
        loggedInStateStorage.persist()
    }

    suspend fun updateUser(user: User) {
        Logger.debug("user = $user")

        loggedInUser = user
        localRepository.updateUser(user)
    }

    suspend fun itemsObservable(): LiveData<List<Item>> {
        return localRepository.itemsObservable()
    }

    suspend fun createItem(item: Item) {
        localRepository.insertItem(item)
    }

    suspend fun updateItem(item: Item) {
        localRepository.updateItem(item)
    }

    suspend fun itemAuthorizationsObservable(): LiveData<List<ItemAuthorization>> {
        return localRepository.itemAuthorizationsObservable()
    }

    suspend fun findItemAuthorizationForItem(item: Item): List<ItemAuthorization> {
        return localRepository.findItemAuthorizationForItem(item)
    }

    suspend fun createItemAuthorization(itemAuthorization: ItemAuthorization) {
        localRepository.insertItemAuthorization(itemAuthorization)
    }

    suspend fun updateItemAuthorization(itemAuthorization: ItemAuthorization) {
        localRepository.updateItemAuthorization(itemAuthorization)
    }

    suspend fun synchronize(): Result<Unit> {
        Logger.debug("Synchronize")

        return withContext(Dispatchers.IO) {
            val synchronizeResults = mutableListOf<Result<Differentiation.Result<*>>>()

            // Execute each task synchronously
            for (task in createSynchronizationTasks()) {
                val synchronizeTaskName = task.javaClass.simpleName

                Logger.debug("Starting task '$synchronizeTaskName'")
                val result = task.synchronize()

                val printableResult = when (result) {
                    is Success -> "${result.javaClass.simpleName} (${result.result})"
                    is Failure -> result.javaClass.simpleName
                }
                Logger.debug("Finished task '$synchronizeTaskName' with result: $printableResult")

                synchronizeResults.add(result)

                // Do not stop if a task failed (otherwise later tasks may never synced if prior task failed) - except for timeout
                if ((result as? Failure)?.throwable is SocketTimeoutException) {
                    Logger.debug("Skip all other tasks because '$synchronizeTaskName' failed with timeout")
                    break
                }
            }

            val firstFailedTask = synchronizeResults.filterIsInstance(Failure::class.java).firstOrNull()

            if (firstFailedTask != null) {
                Failure(firstFailedTask.throwable)
            } else {
                loggedInStateStorage?.userType?.asRemoteOrNull()?.lastSuccessfulSync = Date()
                loggedInStateStorage?.persist()

                Success(Unit)
            }
        }
    }

    private fun createSynchronizationTasks(): List<SynchronizationTask> {
        val userWebservice = userWebservice ?: throw IllegalStateException("The user webservice is not initialized!")
        val loggedInUser = loggedInUser ?: throw IllegalStateException("The logged-in user is not initialized!")

        return listOf(
            UsersSynchronizationTask(localRepository, userWebservice, loggedInUser),
            UserDetailsSynchronizationTask(localRepository, userWebservice, loggedInUser),
            ItemsSynchronizationTask(localRepository, userWebservice, loggedInUser.username),
            ItemAuthorizationsSynchronizationTask(localRepository, userWebservice, loggedInUser.username)
        )
    }

    suspend fun logoutUser() {
        Logger.debug("Logout user")

        resetLoggedInUser()
        loggedInUserResult.postValue(null)
    }

    private suspend fun resetLoggedInUser() {
        Logger.debug("Reset all data of user")

        authWebservice = null
        userWebservice = null

        loggedInUser = null

        localRepository.reset()
        loggedInStateStorage?.reset()
    }
}

class LoggedInStateStorage(private val sharedPreferences: SharedPreferences, private val userManager: UserManager) {

    var userType: UserType? = null
        set(value) {
            field = value

            runBlocking(Dispatchers.Main) {
                userManager.userType.notifyChange()
            }
        }

    var encryptedMasterPassword: EncryptedValue? = null
        set(value) {
            field = value

            runBlocking(Dispatchers.Main) {
                userManager.encryptedMasterPassword.notifyChange()
            }
        }

    suspend fun restore() {
        withContext(Dispatchers.IO) {
            val username = sharedPreferences.getString(SHARED_PREFERENCES_KEY_USERNAME, null)
            val serverUrl = sharedPreferences.getString(SHARED_PREFERENCES_KEY_SERVERURL, null)?.let { Uri.parse(it) }
            val authToken = sharedPreferences.getString(SHARED_PREFERENCES_KEY_AUTH_TOKEN, null)?.let { AuthToken.Deserializer.deserializeOrNull(it) }
            val lastSuccessfulSync = sharedPreferences.getLong(SHARED_PREFERENCES_KEY_LAST_SUCCESSFUL_SYNC, 0).takeIf { it > 0 }?.let { Date(it) }

            userType = when {
                (username != null && serverUrl != null && authToken != null) -> UserType.Remote(username, serverUrl, authToken, lastSuccessfulSync)
                (username != null) -> UserType.Local(username)
                else -> null
            }

            encryptedMasterPassword = sharedPreferences.getString(SHARED_PREFERENCES_KEY_ENCRYPTED_MASTER_PASSWORD, null)?.let { EncryptedValue.Deserializer.deserializeOrNull(it) }
        }
    }

    suspend fun persist() {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().apply {
                putString(SHARED_PREFERENCES_KEY_USERNAME, userType?.username)
                putString(SHARED_PREFERENCES_KEY_SERVERURL, userType?.asRemoteOrNull()?.serverUrl?.toString())
                putString(SHARED_PREFERENCES_KEY_AUTH_TOKEN, userType?.asRemoteOrNull()?.authToken?.serialize()?.toString())
                putLong(SHARED_PREFERENCES_KEY_LAST_SUCCESSFUL_SYNC, userType?.asRemoteOrNull()?.lastSuccessfulSync?.time ?: 0)
                putString(SHARED_PREFERENCES_KEY_ENCRYPTED_MASTER_PASSWORD, encryptedMasterPassword?.serialize()?.toString())
            }.commit()
        }
    }

    suspend fun reset() {
        userType = null
        encryptedMasterPassword = null
        persist()
    }

    companion object {
        private const val SHARED_PREFERENCES_KEY_USERNAME = "username"
        private const val SHARED_PREFERENCES_KEY_SERVERURL = "serverUrl"
        private const val SHARED_PREFERENCES_KEY_AUTH_TOKEN = "authToken"
        private const val SHARED_PREFERENCES_KEY_LAST_SUCCESSFUL_SYNC = "lastSuccessfulSync"
        private const val SHARED_PREFERENCES_KEY_ENCRYPTED_MASTER_PASSWORD = "encryptedMasterPassword"
    }
}

sealed class UserType(val username: String) {
    class Local(username: String) : UserType(username)
    class Remote(username: String, val serverUrl: Uri, var authToken: AuthToken?, var lastSuccessfulSync: Date?) : UserType(username)
}

fun UserType.asRemoteOrNull(): UserType.Remote? {
    return this as? UserType.Remote
}

sealed class LoggedInUserResult(val newLoggedInUser: User) {
    class PerformedLogin(newLoggedInUser: User, val masterPassword: String) : LoggedInUserResult(newLoggedInUser)
    class RestoredLogin(newLoggedInUser: User) : LoggedInUserResult(newLoggedInUser)
}

private class UsersSynchronizationTask(
    private val localRepository: LocalRepository,
    private var userWebservice: UserWebservice,
    private val loggedInUser: User
) : SynchronizationTask {
    override suspend fun synchronize(): Result<Differentiation.Result<User>> {
        return try {
            coroutineScope {
                val localUsersDeferred = async {
                    val localUserList = localRepository.findAllUsers()
                    localUserList.takeIf { it.isNotEmpty() } ?: throw IllegalStateException("The local user list is null - can't process with synchronization!")
                }

                val remoteUsersDeferred = async {
                    userWebservice.requestPublicUserList().resultOrThrowException()
                }

                // Only update the other users, not the logged-in user
                val localUsers = localUsersDeferred.await().excludeLoggedInUsername()
                val remoteUsers = remoteUsersDeferred.await().excludeLoggedInUsername()
                val differentiationResult = Differentiation.collectChanges(localUsers, remoteUsers)

                // Update local database
                localRepository.insertUser(*differentiationResult.newItemsForLocal.toTypedArray())
                localRepository.updateUser(*differentiationResult.modifiedItemsForLocal.toTypedArray())

                Success(differentiationResult)
            }
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    private fun List<User>.excludeLoggedInUsername(): List<User> {
        val loggedInUsername = loggedInUser.username
        return filterNot { it.username == loggedInUsername }
    }
}

private class UserDetailsSynchronizationTask(
    private val localRepository: LocalRepository,
    private var userWebservice: UserWebservice,
    private val loggedInUser: User
) : SynchronizationTask {
    override suspend fun synchronize(): Result<Differentiation.Result<User>> {
        return try {
            coroutineScope {
                val localUser = loggedInUser
                val remoteUser = userWebservice.requestUser().resultOrThrowException()
                val differentiationResult = Differentiation.collectChanges(listOf(localUser), listOf(remoteUser))

                when {
                    differentiationResult.modifiedItemsForLocal.isNotEmpty() -> {
                        Logger.debug("Update local user because remote user was lastly modified")
                        localRepository.updateUser(remoteUser)
                    }
                    differentiationResult.modifiedItemsForRemote.isNotEmpty() -> {
                        Logger.debug("Update remote user because local user was lastly modified")
                        userWebservice.updateUser(localUser)
                    }
                    else -> {
                        Logger.debug("No update needed because local and remote user are equal")
                    }
                }

                Success(differentiationResult)
            }
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

private class ItemsSynchronizationTask(
    private val localRepository: LocalRepository,
    private var userWebservice: UserWebservice,
    private val loggedInUserName: String
) : SynchronizationTask {
    override suspend fun synchronize(): Result<Differentiation.Result<Item>> {
        return try {
            coroutineScope {
                val localItemsDeferred = async { localRepository.findAllItems() }
                val remoteItemsDeferred = async { userWebservice.requestItemList().resultOrThrowException() }

                val localItems = localItemsDeferred.await()
                val remoteItems = remoteItemsDeferred.await()
                val differentiationResult = Differentiation.collectChanges(localItems, remoteItems)

                // Update local database
                localRepository.insertItem(*differentiationResult.newItemsForLocal.toTypedArray())
                localRepository.updateItem(*differentiationResult.modifiedItemsForLocal.toTypedArray())

                val remoteChangedItems = differentiationResult.remoteChangedItems.filter { item ->
                    // Only update items where the user has a non-deleted, non-readonly item authorization
                    localRepository.findItemAuthorizationForItem(item).any { it.userId == loggedInUserName && !it.readOnly && !it.deleted }
                }

                // Update remote webservice if necessary
                if (remoteChangedItems.isNotEmpty()) {
                    userWebservice.updateItemList(remoteChangedItems).resultOrThrowException()
                }

                Success(differentiationResult)
            }
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

private class ItemAuthorizationsSynchronizationTask(
    private val localRepository: LocalRepository,
    private var userWebservice: UserWebservice,
    private val loggedInUserName: String
) : SynchronizationTask {
    override suspend fun synchronize(): Result<Differentiation.Result<ItemAuthorization>> {
        return try {
            coroutineScope {
                val localItemAuthorizationsDeferred = async { localRepository.findAllItemAuthorizations() }
                val remoteItemAuthorizationsDeferred = async { userWebservice.requestItemAuthorizationList().resultOrThrowException() }

                val localItemAuthorizations = localItemAuthorizationsDeferred.await()
                val remoteItemAuthorizations = remoteItemAuthorizationsDeferred.await()
                val differentiationResult = Differentiation.collectChanges(localItemAuthorizations, remoteItemAuthorizations)

                // Update local database
                localRepository.insertItemAuthorization(*differentiationResult.newItemsForLocal.toTypedArray())
                localRepository.updateItemAuthorization(*differentiationResult.modifiedItemsForLocal.toTypedArray())

                val remoteChangedItemAuthorizations = differentiationResult.remoteChangedItems.filter { itemAuthorization ->
                    // Only update item authorizations where the user is the creator of the item
                    localRepository.findItem(itemAuthorization.itemId)?.userId == loggedInUserName
                }

                // Update remote webservice if necessary
                if (remoteChangedItemAuthorizations.isNotEmpty()) {
                    userWebservice.updateItemAuthorizationList(remoteChangedItemAuthorizations).resultOrThrowException()
                }

                Success(differentiationResult)
            }
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}