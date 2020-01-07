package de.sicherheitskritisch.passbutler

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import de.sicherheitskritisch.passbutler.base.Failure
import de.sicherheitskritisch.passbutler.base.L
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
import de.sicherheitskritisch.passbutler.crypto.models.isExpired
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
import de.sicherheitskritisch.passbutler.database.requestAuthToken
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
import kotlinx.coroutines.withContext
import java.util.*

class UserManager(applicationContext: Context, private val localRepository: LocalRepository) {

    val loggedInUserResult = MutableLiveData<LoggedInUserResult?>()

    val loggedInStateStorage by lazy {
        val sharedPreferences = applicationContext.getSharedPreferences("UserManager", MODE_PRIVATE)
        LoggedInStateStorage(sharedPreferences)
    }

    val webservicesInitialized
        get() = authWebservice != null && userWebservice != null

    private var loggedInUser: User? = null

    private var authWebservice: AuthWebservice? = null
    private var userWebservice: UserWebservice? = null

    suspend fun loginRemoteUser(username: String, masterPassword: String, serverUrl: Uri): Result<Unit> {
        return try {
            val masterPasswordAuthenticationHash = Derivation.deriveLocalAuthenticationHash(username, masterPassword)
            authWebservice = AuthWebservice.create(serverUrl, username, masterPasswordAuthenticationHash)

            val authToken = authWebservice.requestAuthToken().resultOrThrowException()
            userWebservice = UserWebservice.create(serverUrl, authToken.token)

            val remoteUser = userWebservice.requestUser().resultOrThrowException()
            localRepository.insertUser(remoteUser)

            loggedInStateStorage.reset()
            loggedInStateStorage.userType = UserType.Server(remoteUser.username, serverUrl, authToken, null)
            loggedInStateStorage.persist()

            loggedInUser = remoteUser
            loggedInUserResult.postValue(LoggedInUserResult.PerformedLogin(remoteUser, masterPassword))

            Success(Unit)
        } catch (exception: Exception) {
            L.w("UserManager", "loginRemoteUser(): The user could not be logged in - reset logged-in user to avoid corrupt state!")
            resetLoggedInUser()

            Failure(exception)
        }
    }

    suspend fun loginLocalUser(username: String, masterPassword: String): Result<Unit> {
        var masterKey: ByteArray? = null
        var masterEncryptionKey: ByteArray? = null

        return try {
            val serverMasterPasswordAuthenticationHash = deriveServerMasterPasswordAuthenticationHash(username, masterPassword)

            val masterKeyDerivationInformation = createMasterKeyDerivationInformation()

            masterKey = withContext(Dispatchers.Default) {
                Derivation.deriveMasterKey(masterPassword, masterKeyDerivationInformation)
            }

            masterEncryptionKey = withContext(Dispatchers.IO) {
                EncryptionAlgorithm.Symmetric.AES256GCM.generateEncryptionKey()
            }

            val serializableMasterEncryptionKey = CryptographicKey(masterEncryptionKey)
            val protectedMasterEncryptionKey = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterKey, serializableMasterEncryptionKey)

            val (itemEncryptionPublicKey, protectedItemEncryptionSecretKey) = generateItemEncryptionKeyPair(masterEncryptionKey)
            val protectedUserSettings = createUserSettings(masterEncryptionKey)
            val currentDate = Date()

            val localUser = User(
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

            L.d("UserManager", "localuser = $localUser")

            localRepository.insertUser(localUser)

            loggedInStateStorage.reset()
            loggedInStateStorage.userType = UserType.Local(localUser.username)
            loggedInStateStorage.persist()

            loggedInUser = localUser
            loggedInUserResult.postValue(LoggedInUserResult.PerformedLogin(localUser, masterPassword))

            Success(Unit)
        } catch (exception: Exception) {
            L.w("UserManager", "loginLocalUser(): The user could not be logged in - reset logged-in user to avoid corrupt state!")
            resetLoggedInUser()

            Failure(exception)
        } finally {
            // Always active clear all sensible data before returning method
            masterKey?.clear()
            masterEncryptionKey?.clear()
        }
    }

    private suspend fun deriveServerMasterPasswordAuthenticationHash(username: String, masterPassword: String): String {
        return withContext(Dispatchers.Default) {
            val masterPasswordAuthenticationHash = Derivation.deriveLocalAuthenticationHash(username, masterPassword)
            val serverMasterPasswordAuthenticationHash = Derivation.deriveServerAuthenticationHash(masterPasswordAuthenticationHash)

            serverMasterPasswordAuthenticationHash
        }
    }

    private suspend fun createMasterKeyDerivationInformation(): KeyDerivationInformation {
        return withContext(Dispatchers.IO) {
            val masterKeySalt = RandomGenerator.generateRandomBytes(MASTER_KEY_BIT_LENGTH.byteSize)
            val masterKeyIterationCount = MASTER_KEY_ITERATION_COUNT
            val masterKeyDerivationInformation = KeyDerivationInformation(masterKeySalt, masterKeyIterationCount)

            masterKeyDerivationInformation
        }
    }

    private suspend fun generateItemEncryptionKeyPair(masterEncryptionKey: ByteArray): Pair<CryptographicKey, ProtectedValue<CryptographicKey>> {
        return withContext(Dispatchers.Default) {
            val itemEncryptionKeyPair = EncryptionAlgorithm.Asymmetric.RSA2048OAEP.generateKeyPair()

            val serializableItemEncryptionPublicKey = CryptographicKey(itemEncryptionKeyPair.public.encoded)

            val serializableItemEncryptionSecretKey = CryptographicKey(itemEncryptionKeyPair.private.encoded)
            val protectedItemEncryptionSecretKey = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterEncryptionKey, serializableItemEncryptionSecretKey)

            Pair(serializableItemEncryptionPublicKey, protectedItemEncryptionSecretKey)
        }
    }

    private suspend fun createUserSettings(masterEncryptionKey: ByteArray): ProtectedValue<UserSettings> {
        return withContext(Dispatchers.Default) {
            val userSettings = UserSettings()
            val protectedUserSettings = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterEncryptionKey, userSettings)
            protectedUserSettings
        }
    }

    suspend fun restoreLoggedInUser() {
        if (loggedInUser == null) {
            L.d("UserManager", "restoreLoggedInUser(): Try to restore logged-in user")

            // Restore logged-in state storage first to be able to access its data
            loggedInStateStorage.restore()

            val restoredLoggedInUser = loggedInStateStorage.userType?.username?.let { loggedInUsername ->
                localRepository.findUser(loggedInUsername)
            }

            loggedInUser = restoredLoggedInUser

            if (restoredLoggedInUser != null) {
                loggedInUserResult.postValue(LoggedInUserResult.RestoredLogin(restoredLoggedInUser))
            } else {
                loggedInUserResult.postValue(null)
            }
        } else {
            L.d("UserManager", "restoreLoggedInUser(): Not needed because already restored")
        }
    }

    suspend fun restoreWebservices(masterPassword: String) {
        L.d("UserManager", "restoreWebservices()")

        val userType = loggedInStateStorage.userType as? UserType.Server ?: throw IllegalStateException("The user is a local kind despite it was tried to restore webservices!")

        try {
            if (authWebservice == null) {
                initializeAuthWebservice(masterPassword)
            }

            var authToken = userType.authToken
            var authTokenHasChanged = false

            if (authToken.isExpired) {
                authToken = authWebservice.requestAuthToken().resultOrThrowException()
                authTokenHasChanged = true

                // Update and persist token
                userType.authToken = authToken
                loggedInStateStorage.persist()
            }

            if (userWebservice == null || authTokenHasChanged) {
                userWebservice = UserWebservice.create(userType.serverUrl, authToken.token)
            }
        } catch (exception: Exception) {
            L.w("UserManager", "restoreWebservices(): The webservices could not be restored!", exception)
        }
    }

    suspend fun initializeAuthWebservice(masterPassword: String) {
        L.d("UserManager", "initializeAuthWebservice()")

        val userType = loggedInStateStorage.userType as? UserType.Server ?: throw IllegalStateException("The user is a local kind despite it was tried to initialize auth webservice!")
        val username = userType.username
        val serverUrl = userType.serverUrl

        val masterPasswordAuthenticationHash = withContext(Dispatchers.Default) {
            Derivation.deriveLocalAuthenticationHash(username, masterPassword)
        }

        authWebservice = AuthWebservice.create(serverUrl, username, masterPasswordAuthenticationHash)
    }

    suspend fun updateUser(user: User) {
        L.d("UserManager", "updateUser(): user = $user")

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
        L.d("UserManager", "synchronize()")

        val userType = loggedInStateStorage.userType as? UserType.Server ?: throw IllegalStateException("The logged-in user type is local!")

        return withContext(Dispatchers.IO) {
            // Execute tasks synchronously, do not stop if any task failed (otherwise other tasks are never synced if first task failed)
            val synchronizeResults = createSynchronizationTasks().map {
                val synchronizeTaskName = it.javaClass.simpleName

                L.d("UserManager", "synchronize(): Starting '$synchronizeTaskName'")
                val result = it.synchronize()

                val printableResult = when (result) {
                    is Success -> "${result.javaClass.simpleName} (${result.result})"
                    is Failure -> result.javaClass.simpleName
                }
                L.d("UserManager", "synchronize(): Finished '$synchronizeTaskName' with result: $printableResult")

                result
            }

            val firstFailedTask = synchronizeResults.filterIsInstance(Failure::class.java).firstOrNull()

            if (firstFailedTask != null) {
                Failure(firstFailedTask.throwable)
            } else {
                userType.lastSuccessfulSync = Date()
                loggedInStateStorage.persist()

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
        L.d("UserManager", "logoutUser()")

        resetLoggedInUser()
        loggedInUserResult.postValue(null)
    }

    private suspend fun resetLoggedInUser() {
        L.d("UserManager", "resetLoggedInUser()")

        authWebservice = null
        userWebservice = null

        loggedInUser = null

        localRepository.reset()
        loggedInStateStorage.reset()
    }
}

class LoggedInStateStorage(private val sharedPreferences: SharedPreferences) {

    var userType: UserType? = null
    var encryptedMasterPassword: EncryptedValue? = null

    suspend fun restore() {
        withContext(Dispatchers.IO) {
            val username = sharedPreferences.getString(SHARED_PREFERENCES_KEY_USERNAME, null)
            val serverUrl = sharedPreferences.getString(SHARED_PREFERENCES_KEY_SERVERURL, null)?.let { Uri.parse(it) }
            val authToken = sharedPreferences.getString(SHARED_PREFERENCES_KEY_AUTH_TOKEN, null)?.let { AuthToken.Deserializer.deserializeOrNull(it) }
            val lastSuccessfulSync = sharedPreferences.getLong(SHARED_PREFERENCES_KEY_LAST_SUCCESSFUL_SYNC, 0).takeIf { it > 0 }?.let { Date(it) }

            userType = when {
                (username != null && serverUrl != null && authToken != null) -> UserType.Server(username, serverUrl, authToken, lastSuccessfulSync)
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
                putString(SHARED_PREFERENCES_KEY_SERVERURL, (userType as? UserType.Server)?.serverUrl?.toString())
                putString(SHARED_PREFERENCES_KEY_AUTH_TOKEN, (userType as? UserType.Server)?.authToken?.serialize()?.toString())
                putLong(SHARED_PREFERENCES_KEY_LAST_SUCCESSFUL_SYNC, (userType as? UserType.Server)?.lastSuccessfulSync?.time ?: 0)
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
    class Server(username: String, val serverUrl: Uri, var authToken: AuthToken, var lastSuccessfulSync: Date?) : UserType(username)
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
                        L.d("UserDetailsSynchronizationTask", "synchronize(): Update local user because remote user was lastly modified")
                        localRepository.updateUser(remoteUser)
                    }
                    differentiationResult.modifiedItemsForRemote.isNotEmpty() -> {
                        L.d("UserDetailsSynchronizationTask", "synchronize(): Update remote user because local user was lastly modified")
                        userWebservice.updateUser(localUser)
                    }
                    else -> {
                        L.d("UserDetailsSynchronizationTask", "synchronize(): No update needed because local and remote user are equal")
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