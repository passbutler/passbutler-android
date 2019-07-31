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
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import de.sicherheitskritisch.passbutler.database.models.isExpired
import de.sicherheitskritisch.passbutler.database.requestAuthToken
import de.sicherheitskritisch.passbutler.database.requestPublicUserList
import de.sicherheitskritisch.passbutler.database.requestUser
import de.sicherheitskritisch.passbutler.database.updateUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.*

class UserManager(applicationContext: Context, private val localRepository: LocalRepository) {

    val loggedInUserResult = MutableLiveData<LoggedInUserResult?>()

    // TODO: More elegant solution - sealed class for `LoggedInState`?
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
            val masterPasswordAuthenticationHash = Derivation.deriveLocalAuthenticationHash(username, masterPassword)
            authWebservice = AuthWebservice.create(serverUrl, username, masterPasswordAuthenticationHash)

            val authToken = authWebservice.requestAuthToken()
            userWebservice = UserWebservice.create(serverUrl, authToken.token)

            val remoteUser = userWebservice.requestUser(username)
            localRepository.insertUser(remoteUser)

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

    @Throws(LoginFailedException::class)
    suspend fun loginLocalUser(username: String, masterPassword: String) {
        var masterKey: ByteArray? = null
        var masterEncryptionKey: ByteArray? = null

        try {
            val masterPasswordAuthenticationHash = Derivation.deriveLocalAuthenticationHash(username, masterPassword)
            val serverMasterPasswordAuthenticationHash = Derivation.deriveServerAuthenticationHash(masterPasswordAuthenticationHash)

            val masterKeySalt = RandomGenerator.generateRandomBytes(MASTER_KEY_BIT_LENGTH.byteSize)
            val masterKeyIterationCount = MASTER_KEY_ITERATION_COUNT
            val masterKeyDerivationInformation = KeyDerivationInformation(masterKeySalt, masterKeyIterationCount)
            masterKey = Derivation.deriveMasterKey(masterPassword, masterKeyDerivationInformation)

            masterEncryptionKey = EncryptionAlgorithm.Symmetric.AES256GCM.generateEncryptionKey()
            val serializableMasterEncryptionKey = CryptographicKey(masterEncryptionKey)
            val protectedMasterEncryptionKey = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterKey, serializableMasterEncryptionKey)

            // TODO: Generate real values
            val itemEncryptionPublicKey = CryptographicKey(ByteArray(0))
            val itemEncryptionSecretKey = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterEncryptionKey, CryptographicKey(ByteArray(0)))

            val userSettings = UserSettings()
            val protectedUserSettings = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterEncryptionKey, userSettings)

            val localUser = if (protectedMasterEncryptionKey != null && protectedUserSettings != null) {
                val currentDate = Date()

                User(
                    username,
                    serverMasterPasswordAuthenticationHash,
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

            localRepository.insertUser(localUser)

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

    @Throws(IllegalStateException::class)
    suspend fun restoreWebservices(masterPassword: String) {
        L.d("UserManager", "restoreWebservices()")

        val serverUrl = loggedInStateStorage.serverUrl ?: throw IllegalStateException("The server URL is null despite it was tried to restore webservices!")

        try {
            if (authWebservice == null) {
                initializeAuthWebservice(masterPassword)
            }

            var authTokenHasChanged = false
            var authToken = loggedInStateStorage.authToken

            if (authToken == null || authToken.isExpired) {
                authToken = authWebservice.requestAuthToken()
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

    @Throws(IllegalStateException::class)
    fun initializeAuthWebservice(masterPassword: String) {
        L.d("UserManager", "initializeAuthWebservice()")

        val username = loggedInStateStorage.username ?: throw IllegalStateException("The username is null despite it was tried to initialize auth webservice!")
        val serverUrl = loggedInStateStorage.serverUrl ?: throw IllegalStateException("The server URL is null despite it was tried to initialize auth webservice!")

        val masterPasswordAuthenticationHash = Derivation.deriveLocalAuthenticationHash(username, masterPassword)
        authWebservice = AuthWebservice.create(serverUrl, username, masterPasswordAuthenticationHash)
    }

    suspend fun updateUser(user: User) {
        L.d("UserManager", "updateUser(): user = $user")

        try {
            localRepository.updateUser(user)

            if (!isLocalUser) {
                userWebservice.updateUser(user)
            }
        } catch (e: Exception) {
            L.w("UserManager", "updateUser(): The user could not be updated!", e)
        }
    }

    @Throws(IllegalStateException::class, Synchronization.SynchronizationFailedException::class)
    suspend fun synchronizeUsers() {
        val userWebservice = userWebservice ?: throw IllegalStateException("The user webservice is null despite it was tried to synchronize user!")
        val loggedInUser = loggedInUserResult.value?.user ?: throw IllegalStateException("The logged-in user is null despite it was tried to synchronize user!")

        val userSynchronization = UserSynchronization(localRepository, userWebservice, loggedInUser)
        userSynchronization.synchronize()
    }

    class UserCreationFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
    class LoginFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
}

data class LoggedInUserResult(val user: User, val masterPassword: String?)

private class LoggedInStateStorage(private val sharedPreferences: SharedPreferences) {
    var username: String? = null
    var serverUrl: Uri? = null
    var authToken: AuthToken? = null

    suspend fun restore() {
        withContext(Dispatchers.IO) {
            username = sharedPreferences.getString(SHARED_PREFERENCES_KEY_USERNAME, null)
            serverUrl = sharedPreferences.getString(SHARED_PREFERENCES_KEY_SERVERURL, null)?.let { Uri.parse(it) }
            authToken = sharedPreferences.getString(SHARED_PREFERENCES_KEY_AUTH_TOKEN, null)?.let { AuthToken.Deserializer.deserializeOrNull(it) }
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

private class UserSynchronization(private val localRepository: LocalRepository, private var userWebservice: UserWebservice, private val loggedInUser: User) : Synchronization {

    @Throws(Synchronization.SynchronizationFailedException::class)
    override suspend fun synchronize() = coroutineScope {
        try {
            L.d("UserSynchronization", "synchronize(): Started")

            synchronizePublicUsersList(this)
            synchronizeLoggedInUser()

            L.d("UserSynchronization", "synchronize(): Finished successfully")
        } catch (e: Exception) {
            throw Synchronization.SynchronizationFailedException(e)
        }
    }

    @Throws(SynchronizePublicUsersListFailedException::class)
    private suspend fun synchronizePublicUsersList(coroutineScope: CoroutineScope) {
        try {
            // Start both operations parallel because they are independent from each other
            val localUserListDeferred = coroutineScope.async {
                val localUserList = localRepository.findAllUsers()
                localUserList.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("The local user list is null - can't process with synchronization!")
            }

            val remoteUserListDeferred = coroutineScope.async {
                userWebservice.requestPublicUserList()
            }

            // Only update the public users, not the logged-in user in this step
            var localUserList = localUserListDeferred.await().filterNot { it.username == loggedInUser.username }
            val remoteUserList = remoteUserListDeferred.await().filterNot { it.username == loggedInUser.username }

            val newLocalUsers = Differentiation.collectNewItems(localUserList, remoteUserList)
            L.d("UserSynchronization", "synchronizePublicUsersList(): New user items for local database: ${newLocalUsers.buildShortUserList()}")

            if (newLocalUsers.isNotEmpty()) {
                createNewUsersInLocalDatabase(newLocalUsers)
            }

            // Merge list to avoid query updated local user list again
            localUserList = localUserList + newLocalUsers

            val modifiedLocalUsers = Differentiation.collectModifiedItems(localUserList, remoteUserList)
            L.d("UserSynchronization", "synchronizePublicUsersList(): Modified user items for local database: ${modifiedLocalUsers.buildShortUserList()}")

            if (modifiedLocalUsers.isNotEmpty()) {
                updateModifiedUsersToLocalDatabase(modifiedLocalUsers)
            }
        } catch (e: Exception) {
            throw SynchronizePublicUsersListFailedException(e)
        }
    }

    private suspend fun createNewUsersInLocalDatabase(newUsers: List<User>) {
        localRepository.insertUser(*newUsers.toTypedArray())
    }

    private suspend fun updateModifiedUsersToLocalDatabase(modifiedUsers: List<User>) {
        localRepository.updateUser(*modifiedUsers.toTypedArray())
    }

    @Throws(SynchronizeLoggedInUserFailedException::class)
    private suspend fun synchronizeLoggedInUser() {
        try {
            val localLoggedInUser = loggedInUser
            val remoteLoggedInUser = userWebservice.requestUser(loggedInUser.username)

            when {
                localLoggedInUser.modified > remoteLoggedInUser.modified -> {
                    L.d("UserSynchronization", "synchronizeLoggedInUser(): Update remote user because local user was lastly modified")
                    userWebservice.updateUser(localLoggedInUser)
                }
                localLoggedInUser.modified < remoteLoggedInUser.modified -> {
                    L.d("UserSynchronization", "synchronizeLoggedInUser(): Update local user because remote user was lastly modified")
                    localRepository.updateUser(remoteLoggedInUser)
                }
            }
        } catch (e: Exception) {
            throw SynchronizeLoggedInUserFailedException(e)
        }
    }

    private fun List<User>.buildShortUserList(): List<String> {
        return this.map { "'${it.username}' (${it.modified})" }
    }

    class SynchronizePublicUsersListFailedException(cause: Throwable? = null) : Synchronization.SynchronizationFailedException(cause)
    class SynchronizeLoggedInUserFailedException(cause: Throwable? = null) : Synchronization.SynchronizationFailedException(cause)
}
