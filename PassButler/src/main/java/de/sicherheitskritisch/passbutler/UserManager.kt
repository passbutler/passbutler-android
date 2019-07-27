package de.sicherheitskritisch.passbutler

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.net.Uri
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
import de.sicherheitskritisch.passbutler.database.models.AuthToken
import de.sicherheitskritisch.passbutler.database.models.ItemKey
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import de.sicherheitskritisch.passbutler.database.models.isExpired
import de.sicherheitskritisch.passbutler.database.technicalErrorDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.util.*

class UserManager(applicationContext: Context, private val localRepository: LocalRepository) {

    val loggedInUserResult = MutableLiveData<LoggedInUserResult?>()

    val isLocalUser
        get() = loggedInStateStorage.serverUrl == null

    private var authWebservice: AuthWebservice? = null
    private var userWebservice: UserWebservice? = null

    private val loggedInStateStorage by lazy {
        val sharedPreferences = applicationContext.getSharedPreferences("UserManager", MODE_PRIVATE)
        LoggedInStateStorage(sharedPreferences)
    }

    @Throws(LoginFailedException::class)
    suspend fun loginRemoteUser(username: String, masterPassword: String, serverUrl: Uri) {
        try {
            val masterPasswordAuthenticationHash = Derivation.deriveAuthenticationHash(username, masterPassword)
            authWebservice = AuthWebservice.create(serverUrl, username, masterPasswordAuthenticationHash)

            val authToken = requestNewAuthToken()
            userWebservice = UserWebservice.create(serverUrl, authToken.token)

            val remoteUser = requestUserDetails(username)
            createUser(remoteUser)

            loggedInStateStorage.reset()

            loggedInStateStorage.username = remoteUser.username
            loggedInStateStorage.serverUrl = serverUrl
            loggedInStateStorage.authToken = authToken
            loggedInStateStorage.persist()

            val loggedInUserResult = LoggedInUserResult(remoteUser, masterPassword = masterPassword)
            this.loggedInUserResult.postValue(loggedInUserResult)
        } catch (exception: Exception) {
            throw LoginFailedException("The remote login failed with an exception!", exception)
        }
    }

    @Throws(Exception::class)
    private suspend fun requestNewAuthToken(): AuthToken {
        val getTokenRequest = authWebservice?.getTokenAsync()
        val getTokenResponse = getTokenRequest?.await()
        val authToken = getTokenResponse?.body()

        if (getTokenResponse?.isSuccessful != true || authToken == null) {
            throw GetAuthTokenFailedException("The auth token could not be get ${getTokenResponse.technicalErrorDescription}")
        }

        return authToken
    }

    @Throws(Exception::class)
    private suspend fun requestUserDetails(username: String): User {
        val getUserDetailsRequest = userWebservice?.getUserDetailsAsync(username)
        val getUserDetailsResponse = getUserDetailsRequest?.await()
        val user = getUserDetailsResponse?.body()

        if (getUserDetailsResponse?.isSuccessful != true || user == null) {
            throw GetUserDetailsFailedException("The user details could not be get ${getUserDetailsResponse.technicalErrorDescription}")
        }

        return user
    }

    @Throws(LoginFailedException::class)
    suspend fun loginLocalUser(username: String, masterPassword: String) {
        var masterKey: ByteArray? = null
        var masterEncryptionKey: ByteArray? = null

        try {
            // TODO: Generate real value
            val masterPasswordAuthenticationHash = ""

            val masterKeySalt = RandomGenerator.generateRandomBytes(MASTER_KEY_BIT_LENGTH.byteSize)
            val masterKeyIterationCount = MASTER_KEY_ITERATION_COUNT
            val masterKeyDerivationInformation = KeyDerivationInformation(masterKeySalt, masterKeyIterationCount)
            masterKey = Derivation.deriveMasterKey(masterPassword, masterKeyDerivationInformation)

            masterEncryptionKey = EncryptionAlgorithm.Symmetric.AES256GCM.generateEncryptionKey()
            val serializableMasterEncryptionKey = CryptographicKey(masterEncryptionKey)
            val protectedMasterEncryptionKey = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterKey, serializableMasterEncryptionKey)

            // TODO: Generate real value
            val itemEncryptionPublicKey = CryptographicKey(ByteArray(0))
            val itemEncryptionSecretKey = null

            val userSettings = UserSettings()
            val protectedUserSettings = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterEncryptionKey, userSettings)

            val localUser = if (protectedMasterEncryptionKey != null && protectedUserSettings != null) {
                val currentDate = currentDate

                User(
                    username,
                    masterPasswordAuthenticationHash,
                    masterKeyDerivationInformation,
                    protectedMasterEncryptionKey,
                    itemEncryptionPublicKey,
                    itemEncryptionSecretKey,
                    protectedUserSettings,
                    false,
                    currentDate,
                    currentDate
                )
            } else {
                throw UserCreationFailedException("The local user could not be created because protected values creation failed!")
            }

            createUser(localUser)

            loggedInStateStorage.reset()

            loggedInStateStorage.username = localUser.username
            loggedInStateStorage.persist()

            val loggedInUserResult = LoggedInUserResult(localUser, masterPassword = masterPassword)
            this.loggedInUserResult.postValue(loggedInUserResult)
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

    suspend fun logoutUser() {
        L.d("UserManager", "logoutUser()")

        authWebservice = null
        userWebservice = null

        localRepository.reset()
        loggedInStateStorage.reset()
        loggedInUserResult.postValue(null)
    }

    suspend fun restoreLoggedInUser() {
        L.d("UserManager", "restoreLoggedInUser()")

        // Restore logged in state storage first
        loggedInStateStorage.restore()

        val restoredLoggedInUser = loggedInStateStorage.username?.let { loggedInUsername ->
            localRepository.findUser(loggedInUsername)
        }

        val loggedInUserResult = restoredLoggedInUser?.let { LoggedInUserResult(it, masterPassword = null) }
        this.loggedInUserResult.postValue(loggedInUserResult)
    }

    suspend fun restoreWebservices(masterPassword: String) {
        L.d("UserManager", "restoreWebservices()")

        val username = loggedInStateStorage.username
        val serverUrl = loggedInStateStorage.serverUrl

        if (username != null && serverUrl != null) {
            try {
                // TODO: If the user changes the master password, the auth webservice needs to be re-initialized
                if (authWebservice == null) {
                    val masterPasswordAuthenticationHash = Derivation.deriveAuthenticationHash(username, masterPassword)
                    authWebservice = AuthWebservice.create(serverUrl, username, masterPasswordAuthenticationHash)
                }

                var authTokenHasChanged = false
                var authToken = loggedInStateStorage.authToken

                if (authToken == null || authToken.isExpired) {
                    authToken = requestNewAuthToken()
                    authTokenHasChanged = true

                    loggedInStateStorage.authToken = authToken
                    loggedInStateStorage.persist()
                }

                if (userWebservice == null || authTokenHasChanged) {
                    userWebservice = UserWebservice.create(serverUrl, authToken.token)
                }
            } catch (e: Exception) {
                L.w("UserManager", "restoreWebservices(): The webservices could not be restored!", e)
            }
        }
    }

    suspend fun updateUser(user: User) {
        L.d("UserManager", "updateUser(): user = $user")

        try {
            // First update in local database
            user.modified = currentDate
            localRepository.updateUser(user)

            // Than update on remote database
            val setUserDetailsRequest = userWebservice?.setUserDetailsAsync(user.username, user)
            val setUserDetailsResponse = setUserDetailsRequest?.await()

            if (setUserDetailsResponse?.isSuccessful != true) {
                throw SetUserDetailsFailedException("The user details could not be set ${setUserDetailsResponse.technicalErrorDescription})")
            }
        } catch (e: Exception) {
            L.w("UserManager", "updateUser(): The user could not be updated!", e)
        }
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
    class SetUserDetailsFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
    class UserCreationFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
    class LoginFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
}

data class LoggedInUserResult(val user: User, val masterPassword: String?)

private class LoggedInStateStorage(private val sharedPreferences: SharedPreferences) {
    var username: String? = null
    var serverUrl: Uri? = null
    var authToken: AuthToken? = null

    suspend fun restore() {
        try {
            withContext(Dispatchers.IO) {
                username = sharedPreferences.getString(SHARED_PREFERENCES_KEY_USERNAME, null)
                serverUrl = sharedPreferences.getString(SHARED_PREFERENCES_KEY_SERVERURL, null)?.let { Uri.parse(it) }
                authToken = sharedPreferences.getString(SHARED_PREFERENCES_KEY_AUTH_TOKEN, null)?.let { AuthToken.deserialize(JSONObject(it)) }
            }
        } catch (e: Exception) {
            L.w("LoggedInStateStorage", "The logged-in-state storage could not be restored!", e)
        }
    }

    // Use blocking `commit()` because we are in suspending function
    @SuppressLint("ApplySharedPref")
    suspend fun persist() {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().apply {
                putString(SHARED_PREFERENCES_KEY_USERNAME, username)
                putString(SHARED_PREFERENCES_KEY_SERVERURL, serverUrl?.toString())
                putString(SHARED_PREFERENCES_KEY_AUTH_TOKEN, authToken?.serialize()?.toString())
            }.commit()
        }
    }

    suspend fun reset() {
        username = null
        serverUrl = null
        authToken = null

        persist()
    }

    companion object {
        private const val SHARED_PREFERENCES_KEY_USERNAME = "username"
        private const val SHARED_PREFERENCES_KEY_SERVERURL = "serverUrl"
        private const val SHARED_PREFERENCES_KEY_AUTH_TOKEN = "authToken"
    }
}

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

        val modifiedLocalUsers = Differentiation.collectModifiedItems(mergedLocalUserList, mergedRemoteUserList)
        L.d("UserSynchronization", "synchronize(): Modified user items for local database: ${modifiedLocalUsers.buildShortUserList()}")

        if (modifiedLocalUsers.isNotEmpty()) {
            updateModifiedUsersToLocalDatabase(modifiedLocalUsers)
        }

        val modifiedRemoteUsers = Differentiation.collectModifiedItems(mergedRemoteUserList, mergedLocalUserList)
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

private val currentDate
    get() = Date.from(Instant.now())
