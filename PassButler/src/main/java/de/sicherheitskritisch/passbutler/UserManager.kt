package de.sicherheitskritisch.passbutler

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
import de.sicherheitskritisch.passbutler.database.Synchronization
import de.sicherheitskritisch.passbutler.database.UserWebservice
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserSettings
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

    val loggedInStateStorage by lazy {
        val sharedPreferences = applicationContext.getSharedPreferences("UserManager", MODE_PRIVATE)
        LoggedInStateStorage(sharedPreferences)
    }

    private var authWebservice: AuthWebservice? = null
    private var userWebservice: UserWebservice? = null

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
            loggedInStateStorage.userType = UserType.Server(remoteUser.username, serverUrl, authToken)
            loggedInStateStorage.persist()

            loggedInUserResult.postValue(LoggedInUserResult.PerformedLogin(remoteUser, masterPassword))
        } catch (exception: Exception) {
            // TODO: Rollback every state
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
            val currentDate = Date()

            val localUser = User(
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

            localRepository.insertUser(localUser)

            loggedInStateStorage.reset()
            loggedInStateStorage.userType = UserType.Local(localUser.username)
            loggedInStateStorage.persist()

            loggedInUserResult.postValue(LoggedInUserResult.PerformedLogin(localUser, masterPassword))
        } catch (exception: Exception) {
            // TODO: Rollback every state
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

        // Restore logged-in state storage first to be able to access its data
        loggedInStateStorage.restore()

        val restoredLoggedInUser = loggedInStateStorage.userType?.username?.let { loggedInUsername ->
            localRepository.findUser(loggedInUsername)
        }

        if (restoredLoggedInUser != null) {
            loggedInUserResult.postValue(LoggedInUserResult.RestoredLogin(restoredLoggedInUser))
        } else {
            loggedInUserResult.postValue(null)
        }
    }

    suspend fun restoreWebservices(masterPassword: String) {
        when (val userType = loggedInStateStorage.userType) {
            is UserType.Server -> {
                L.d("UserManager", "restoreWebservices(): Restore the remote webservices")

                try {
                    if (authWebservice == null) {
                        initializeAuthWebservice(masterPassword)
                    }

                    var authToken = userType.authToken
                    var authTokenHasChanged = false

                    if (authToken.isExpired) {
                        authToken = authWebservice.requestAuthToken()
                        authTokenHasChanged = true

                        // Update and persist token
                        userType.authToken = authToken
                        loggedInStateStorage.persist()
                    }

                    if (userWebservice == null || authTokenHasChanged) {
                        userWebservice = UserWebservice.create(userType.serverUrl, authToken.token)
                    }
                } catch (e: Exception) {
                    L.w("UserManager", "restoreWebservices(): The webservices could not be restored!", e)
                }
            }
            is UserType.Local -> {
                L.d("UserManager", "restoreWebservices(): The user type is local, no need to restore the remote webservices")
            }
            null -> {
                L.e("UserManager", "restoreWebservices(): The user type is null - this is an illegal state!")
            }
        }
    }

    @Throws(IllegalStateException::class)
    fun initializeAuthWebservice(masterPassword: String) {
        L.d("UserManager", "initializeAuthWebservice()")

        val userType = loggedInStateStorage.userType as? UserType.Server ?: throw IllegalStateException("The user is a local kind despite it was tried to initialize auth webservice!")

        val username = userType.username
        val serverUrl = userType.serverUrl

        val masterPasswordAuthenticationHash = Derivation.deriveLocalAuthenticationHash(username, masterPassword)
        authWebservice = AuthWebservice.create(serverUrl, username, masterPasswordAuthenticationHash)
    }

    suspend fun updateUser(user: User) {
        L.d("UserManager", "updateUser(): user = $user")

        localRepository.updateUser(user)

        if (loggedInStateStorage.userType is UserType.Server) {
            try {
                userWebservice.updateUser(user)
            } catch (e: Exception) {
                L.w("UserManager", "updateUser(): The user could not be updated on webservice!", e)
            }
        }
    }

    @Throws(Synchronization.SynchronizationFailedException::class)
    suspend fun synchronizeUsers(loggedInUser: User) {
        val userWebservice = userWebservice ?: throw Synchronization.SynchronizationFailedException("The user webservice is not initialized!")
        val userSynchronization = UserSynchronization(localRepository, userWebservice, loggedInUser)
        userSynchronization.synchronize()
    }

    class LoginFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
}

class LoggedInStateStorage(private val sharedPreferences: SharedPreferences) {

    var userType: UserType? = null
    var encryptedMasterPassword: EncryptedValue? = null

    suspend fun restore() {
        withContext(Dispatchers.IO) {
            val username = sharedPreferences.getString(SHARED_PREFERENCES_KEY_USERNAME, null)
            val serverUrl = sharedPreferences.getString(SHARED_PREFERENCES_KEY_SERVERURL, null)?.let { Uri.parse(it) }
            val authToken = sharedPreferences.getString(SHARED_PREFERENCES_KEY_AUTH_TOKEN, null)?.let { AuthToken.Deserializer.deserializeOrNull(it) }

            userType = when {
                (username != null && serverUrl != null && authToken != null) -> UserType.Server(username, serverUrl, authToken)
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
        private const val SHARED_PREFERENCES_KEY_ENCRYPTED_MASTER_PASSWORD = "encryptedMasterPassword"
    }
}

sealed class UserType(val username: String) {
    class Local(username: String) : UserType(username)
    class Server(username: String, val serverUrl: Uri, var authToken: AuthToken) : UserType(username)
}

sealed class LoggedInUserResult(val newLoggedInUser: User) {
    class PerformedLogin(newLoggedInUser: User, val masterPassword: String) : LoggedInUserResult(newLoggedInUser)
    class RestoredLogin(newLoggedInUser: User) : LoggedInUserResult(newLoggedInUser)
}

private class UserSynchronization(private val localRepository: LocalRepository, private var userWebservice: UserWebservice, private val loggedInUser: User) : Synchronization {

    @Throws(Synchronization.SynchronizationFailedException::class)
    override suspend fun synchronize() {
        try {
            coroutineScope {
                L.d("UserSynchronization", "synchronize(): Started")
                synchronizePublicUsersList(this)
                synchronizeLoggedInUser()
                L.d("UserSynchronization", "synchronize(): Finished successfully")
            }
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

            val loggedInUsername = loggedInUser.username

            // Only update the public users, not the logged-in user in this step
            var localUserList = localUserListDeferred.await().filterNot { it.username == loggedInUsername }
            val remoteUserList = remoteUserListDeferred.await().filterNot { it.username == loggedInUsername }

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
                else -> {
                    L.d("UserSynchronization", "synchronizeLoggedInUser(): No update needed because local and remote user are equal")
                }
            }
        } catch (e: Exception) {
            throw SynchronizeLoggedInUserFailedException(e)
        }
    }

    private fun List<User>.buildShortUserList(): List<String> {
        return this.map { "'${it.username}' (${it.modified})" }
    }

    class SynchronizePublicUsersListFailedException(cause: Throwable) : Synchronization.SynchronizationFailedException(cause)
    class SynchronizeLoggedInUserFailedException(cause: Throwable) : Synchronization.SynchronizationFailedException(cause)
}
