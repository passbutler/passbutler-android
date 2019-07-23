package de.sicherheitskritisch.passbutler

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.lifecycle.MutableLiveData
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.byteSize
import de.sicherheitskritisch.passbutler.base.clear
import de.sicherheitskritisch.passbutler.crypto.Derivation
import de.sicherheitskritisch.passbutler.crypto.EncryptionAlgorithm
import de.sicherheitskritisch.passbutler.crypto.MASTER_KEY_BIT_LENGTH
import de.sicherheitskritisch.passbutler.crypto.MASTER_KEY_ITERATION_COUNT
import de.sicherheitskritisch.passbutler.crypto.ProtectedValue
import de.sicherheitskritisch.passbutler.crypto.RandomGenerator
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import de.sicherheitskritisch.passbutler.database.AuthWebservice
import de.sicherheitskritisch.passbutler.database.Differentiation
import de.sicherheitskritisch.passbutler.database.LocalRepository
import de.sicherheitskritisch.passbutler.database.Synchronization
import de.sicherheitskritisch.passbutler.database.UserWebservice
import de.sicherheitskritisch.passbutler.database.models.ItemKey
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.*

class UserManager(applicationContext: Context, private val localRepository: LocalRepository) {

    val loggedInUser = MutableLiveData<LoggedInUserResult?>()

    val serverUrl: String?
        get() = sharedPreferences.getString(SERIALIZATION_KEY_LOGGED_IN_SERVERURL, null)

    val isLocalUser
        get() = serverUrl == null

    private var authWebservice: AuthWebservice? = null
    private var userWebservice: UserWebservice? = null

    private val sharedPreferences by lazy {
        applicationContext.getSharedPreferences("UserManager", MODE_PRIVATE)
    }

    @Throws(LoginFailedException::class)
    suspend fun loginRemoteUser(serverUrl: String, userName: String, masterPassword: String) {
        try {
            val masterPasswordAuthenticationHash = Derivation.deriveAuthenticationHash(userName, masterPassword)
            authWebservice = AuthWebservice.create(serverUrl, userName, masterPasswordAuthenticationHash)

            val getTokenRequest = authWebservice?.getTokenAsync()
            val getTokenResponse = getTokenRequest?.await()
            val authToken = getTokenResponse?.body()

            if (getTokenResponse?.isSuccessful != true || authToken == null) {
                throw GetAuthTokenFailedException("The auth token could not be get (HTTP status code ${getTokenResponse?.code()}): ${getTokenResponse?.errorBody()?.string()}")
            }

            userWebservice = UserWebservice.create(serverUrl, authToken.token)

            val getUserDetailsRequest = userWebservice?.getUserDetailsAsync(userName)
            val getUserDetailsResponse = getUserDetailsRequest?.await()
            val remoteUser = getUserDetailsResponse?.body()

            if (getUserDetailsResponse?.isSuccessful != true || remoteUser == null) {
                throw GetUserDetailsFailedException("The user details could not be get (HTTP status code ${getUserDetailsResponse?.code()}): ${getUserDetailsResponse?.errorBody()?.string()}")
            }

            createUser(remoteUser)
            persistPreferences(remoteUser.username, serverUrl)

            val loggedInUserResult = LoggedInUserResult(remoteUser, masterPassword = masterPassword)
            loggedInUser.postValue(loggedInUserResult)

            // TODO: Persist token + exp + preferences

        } catch (exception: Exception) {
            throw LoginFailedException("The remote login failed with an exception!", exception)
        }
    }

    @Throws(LoginFailedException::class)
    suspend fun loginLocalUser(userName: String, masterPassword: String) {
        var masterKey: ByteArray? = null
        var masterEncryptionKey: ByteArray? = null

        try {
            val masterKeySalt = RandomGenerator.generateRandomBytes(MASTER_KEY_BIT_LENGTH.byteSize)
            val masterKeyIterationCount = MASTER_KEY_ITERATION_COUNT
            val masterKeyDerivationInformation = KeyDerivationInformation(masterKeySalt, masterKeyIterationCount)
            masterKey = Derivation.deriveMasterKey(masterPassword, masterKeyDerivationInformation)

            masterEncryptionKey = EncryptionAlgorithm.Symmetric.AES256GCM.generateEncryptionKey()
            val serializableMasterEncryptionKey = CryptographicKey(masterEncryptionKey)
            val protectedMasterEncryptionKey = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterKey, serializableMasterEncryptionKey)

            val userSettings = UserSettings()
            val protectedUserSettings = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterEncryptionKey, userSettings)

            val localUser = if (protectedMasterEncryptionKey != null && protectedUserSettings != null) {
                val currentDate = currentDate

                User(
                    userName,
                    masterKeyDerivationInformation,
                    protectedMasterEncryptionKey,
                    protectedUserSettings,
                    false,
                    currentDate,
                    currentDate
                )
            } else {
                throw UserCreationFailedException("The local user could not be created because protected values creation failed!")
            }

            createUser(localUser)
            persistPreferences(localUser.username, null)

            val loggedInUserResult = LoggedInUserResult(localUser, masterPassword = masterPassword)
            loggedInUser.postValue(loggedInUserResult)

        } catch (exception: Exception) {
            throw LoginFailedException("The local login failed with an exception!", exception)
        } finally {
            // Always active clear all sensible data before returning method
            masterKey?.clear()
            masterEncryptionKey?.clear()
        }
    }

    private suspend fun createUser(user: User) {
        localRepository.insertUser(user)
    }

    private suspend fun persistPreferences(loggedInUsername: String, serverUrl: String?) {
        withContext(Dispatchers.IO) {
            // Use blocking `commit()` because we are in suspending function
            sharedPreferences.edit().also {
                it.putString(SERIALIZATION_KEY_LOGGED_IN_USERNAME, loggedInUsername)
                it.putString(SERIALIZATION_KEY_LOGGED_IN_SERVERURL, serverUrl)
            }.commit()
        }
    }

    suspend fun logoutUser() {
        L.d("UserManager", "logoutUser()")

        authWebservice = null
        userWebservice = null

        localRepository.reset()
        sharedPreferences.edit().clear().apply()
        loggedInUser.postValue(null)
    }

    suspend fun restoreLoggedInUser() {
        L.d("UserManager", "restoreLoggedInUser()")

        val restoredLoggedInUser = sharedPreferences.getString(SERIALIZATION_KEY_LOGGED_IN_USERNAME, null)?.let { loggedInUsername ->
            localRepository.findUser(loggedInUsername)
        }

        val loggedInUserResult = restoredLoggedInUser?.let { LoggedInUserResult(it, masterPassword = null) }
        loggedInUser.postValue(loggedInUserResult)
    }

    suspend fun updateUser(user: User) {
        L.d("UserManager", "updateUser(): user = $user")

        user.modified = currentDate
        localRepository.updateUser(user)
    }

    suspend fun createItemKey(itemKey: ItemKey) {
        // TODO: Be sure the username is correctly set + set modified + created?
        L.d("UserManager", "createItemKey(): itemKey = $itemKey")
        localRepository.insertItemKey(itemKey)
    }

    @Throws(Synchronization.SynchronizationFailedException::class)
    suspend fun synchronizeUsers() {
        val remoteWebservice = userWebservice

        if (remoteWebservice != null) {
            UserSynchronization(localRepository, remoteWebservice).synchronize()
        } else {
            throw Synchronization.SynchronizationFailedException("The remote webservice is null - the synchronization can't be started!")
        }
    }

    class GetAuthTokenFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
    class GetUserDetailsFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
    class UserCreationFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
    class LoginFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
}

// TODO: Only sync down users
private class UserSynchronization(private val localRepository: LocalRepository, private var userWebservice: UserWebservice) : Synchronization {

    @Throws(Synchronization.SynchronizationFailedException::class)
    override suspend fun synchronize() = coroutineScope {
        // Start both operations parallel because they are independent from each other
        val localUserListDeferred = async { fetchLocalUserList() }
        val remoteUserListDeferred = async { fetchRemoteUserList() }

        val localUserList = localUserListDeferred.await()
        val remoteUserList = remoteUserListDeferred.await()

        val newLocalUsers = Differentiation.collectNewItems(localUserList, remoteUserList)
        L.d("UserSynchronization", "synchronize(): New user items for local database: ${newLocalUsers.buildShortUserList()}")

        if (newLocalUsers.isNotEmpty()) {
            addNewUsersToLocalDatabase(newLocalUsers)
        }

        val newRemoteUsers = Differentiation.collectNewItems(remoteUserList, localUserList)
        L.d("UserSynchronization", "synchronize(): New user items for remote database: ${newRemoteUsers.buildShortUserList()}")

        if (newRemoteUsers.isNotEmpty()) {
            addNewUsersToRemoteDatabase(newRemoteUsers)
        }

        // Build up both complete lists to avoid query/fetch again
        val mergedLocalUserList = localUserList + newLocalUsers
        val mergedRemoteUserList = remoteUserList + newRemoteUsers

        val modifiedLocalUsers = Differentiation.collectModifiedUserItems(mergedLocalUserList, mergedRemoteUserList)
        L.d("UserSynchronization", "synchronize(): Modified user items for local database: ${modifiedLocalUsers.buildShortUserList()}")

        if (modifiedLocalUsers.isNotEmpty()) {
            updateModifiedUsersToLocalDatabase(modifiedLocalUsers)
        }

        val modifiedRemoteUsers = Differentiation.collectModifiedUserItems(mergedRemoteUserList, mergedLocalUserList)
        L.d("UserSynchronization", "synchronize(): Modified user items for remote database: ${modifiedRemoteUsers.buildShortUserList()}")

        if (modifiedRemoteUsers.isNotEmpty()) {
            updateModifiedUsersToRemoteDatabase(modifiedRemoteUsers)
        }

        L.d("UserSynchronization", "synchronize(): Finished successfully")
    }

    @Throws(Synchronization.SynchronizationFailedException::class)
    private suspend fun fetchLocalUserList(): List<User> {
        val localUsersList = localRepository.findAllUsers().takeIf { it.isNotEmpty() }
        return localUsersList ?: throw Synchronization.SynchronizationFailedException("The local user list is null - can't process with synchronization!")
    }

    @Throws(Synchronization.SynchronizationFailedException::class)
    private suspend fun fetchRemoteUserList(): List<User> {
        val remoteUsersListRequest = userWebservice.getUsersAsync()
        val response = remoteUsersListRequest.await()
        return response.body() ?: throw Synchronization.SynchronizationFailedException("The remote user list is null - can't process with synchronization!")
    }

    @Throws(Synchronization.SynchronizationFailedException::class)
    private suspend fun addNewUsersToLocalDatabase(newUsers: List<User>) {
        localRepository.insertUser(*newUsers.toTypedArray())
    }

    @Throws(Synchronization.SynchronizationFailedException::class)
    private suspend fun addNewUsersToRemoteDatabase(newUsers: List<User>) {
        val remoteUsersListRequest = userWebservice.addUsersAsync(newUsers)
        val requestDeferred = remoteUsersListRequest.await()

        if (!requestDeferred.isSuccessful) {
            throw Synchronization.SynchronizationFailedException("The users could not be added to remote database (HTTP error code ${requestDeferred.code()})!")
        }
    }

    @Throws(Synchronization.SynchronizationFailedException::class)
    private suspend fun updateModifiedUsersToLocalDatabase(modifiedUsers: List<User>) {
        localRepository.updateUser(*modifiedUsers.toTypedArray())
    }

    @Throws(Synchronization.SynchronizationFailedException::class)
    private suspend fun updateModifiedUsersToRemoteDatabase(modifiedUsers: List<User>) {
        val remoteUsersListRequest = userWebservice.updateUsersAsync(modifiedUsers)
        val requestDeferred = remoteUsersListRequest.await()

        if (!requestDeferred.isSuccessful) {
            throw Synchronization.SynchronizationFailedException("The users could not be updated on remote database (HTTP error code ${requestDeferred.code()})!")
        }
    }

    private fun List<User>.buildShortUserList(): List<String> {
        return this.map { "'${it.username}' (${it.modified})" }
    }
}

private const val SERIALIZATION_KEY_LOGGED_IN_USERNAME = "loggedInUsername"
private const val SERIALIZATION_KEY_LOGGED_IN_SERVERURL = "loggedInServerUrl"

data class LoggedInUserResult(val user: User, val masterPassword: String?)

private val currentDate
    get() = Date.from(Instant.now())
