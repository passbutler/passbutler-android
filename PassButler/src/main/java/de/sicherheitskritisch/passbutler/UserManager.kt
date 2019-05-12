package de.sicherheitskritisch.passbutler

import android.annotation.SuppressLint
import android.arch.lifecycle.MutableLiveData
import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.UnitConverterFactory
import de.sicherheitskritisch.passbutler.base.clear
import de.sicherheitskritisch.passbutler.crypto.EncryptionAlgorithm
import de.sicherheitskritisch.passbutler.crypto.KeyDerivation
import de.sicherheitskritisch.passbutler.crypto.ProtectedValue
import de.sicherheitskritisch.passbutler.crypto.RandomGenerator
import de.sicherheitskritisch.passbutler.database.PassButlerRepository
import de.sicherheitskritisch.passbutler.database.Synchronization
import de.sicherheitskritisch.passbutler.database.models.CryptographicKey
import de.sicherheitskritisch.passbutler.database.models.KeyDerivationInformation
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserConverterFactory
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import de.sicherheitskritisch.passbutler.database.models.UserWebservice
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.Retrofit
import java.time.Instant
import java.util.*

class UserManager(applicationContext: Context, private val localRepository: PassButlerRepository) {

    val loggedInUser = MutableLiveData<LoggedInUserResult?>()

    val isLocalUser
        get() = sharedPreferences.getString(SERIALIZATION_KEY_LOGGED_IN_SERVERURL, null) == null

    private var remoteWebservice: UserWebservice? = null

    private val sharedPreferences by lazy {
        applicationContext.getSharedPreferences("UserManager", MODE_PRIVATE)
    }

    @Throws(LoginFailedException::class)
    suspend fun loginUser(userName: String, password: String, serverUrl: String) {
        // TODO: Proper server login and user creation
        initializeRemoteWebservice(serverUrl)
    }

    private fun initializeRemoteWebservice(serverUrl: String) {
        val retrofitBuilder = Retrofit.Builder()
            .baseUrl(serverUrl)
            .addConverterFactory(UnitConverterFactory())
            .addConverterFactory(UserConverterFactory())
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .build()

        remoteWebservice = retrofitBuilder.create(UserWebservice::class.java)
    }

    @Throws(LoginFailedException::class)
    suspend fun loginLocalUser(userName: String, password: String) {
        var masterKey: ByteArray? = null
        var masterEncryptionKey: ByteArray? = null

        try {
            val masterKeySalt = RandomGenerator.generateRandomBytes(32)
            val masterKeyIterationCount = 100_000
            val masterKeyDerivationInformation = KeyDerivationInformation(masterKeySalt, masterKeyIterationCount)
            masterKey = KeyDerivation.deriveAES256KeyFromPassword(password, masterKeySalt, masterKeyIterationCount)

            masterEncryptionKey = EncryptionAlgorithm.AES256GCM.generateEncryptionKey()
            val serializableMasterEncryptionKey = CryptographicKey(masterEncryptionKey)
            val protectedMasterEncryptionKey = ProtectedValue.create(EncryptionAlgorithm.AES256GCM, masterKey, serializableMasterEncryptionKey)

            val userSettings = UserSettings()
            val protectedUserSettings = ProtectedValue.create(EncryptionAlgorithm.AES256GCM, masterEncryptionKey, userSettings)

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

            val loggedInUserResult = LoggedInUserResult(localUser, masterPassword = password)
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

    @SuppressLint("ApplySharedPref")
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

        shutdownRemoteWebservice()

        localRepository.reset()
        sharedPreferences.edit().clear().apply()
        loggedInUser.postValue(null)
    }

    private fun shutdownRemoteWebservice() {
        remoteWebservice = null
    }

    suspend fun restoreLoggedInUser() {
        L.d("UserManager", "restoreLoggedInUser()")

        val restoredLoggedInUser = sharedPreferences.getString(SERIALIZATION_KEY_LOGGED_IN_USERNAME, null)?.let { loggedInUsername ->
            localRepository.findUser(loggedInUsername)
        }

        val loggedInUserResult = restoredLoggedInUser?.let {
            LoggedInUserResult(it, masterPassword = null)
        }

        loggedInUser.postValue(loggedInUserResult)
    }

    suspend fun updateUser(user: User) {
        L.d("UserManager", "updateUser(): user = $user")

        user.modified = currentDate
        localRepository.updateUser(user)

        // TODO: Trigger sync?
    }

    suspend fun synchronizeUsers() = coroutineScope {
        // Start both operations parallel because they are independent from each other
        val localUserListDeferred = async { fetchLocalUserList() }
        val remoteUserListDeferred = async { fetchRemoteUserList() }

        val localUserList = localUserListDeferred.await()
        val remoteUserList = remoteUserListDeferred.await()

        val newLocalUsers = Synchronization.collectNewItems(localUserList, remoteUserList)
        L.d("UserManager", "synchronizeUsers(): New user items for local database: ${newLocalUsers.buildShortUserList()}")

        if (newLocalUsers.isNotEmpty()) {
            addNewUsersToLocalDatabase(newLocalUsers)
        }

        val newRemoteUsers = Synchronization.collectNewItems(remoteUserList, localUserList)
        L.d("UserManager", "synchronizeUsers(): New user items for remote database: ${newRemoteUsers.buildShortUserList()}")

        if (newRemoteUsers.isNotEmpty()) {
            addNewUsersToRemoteDatabase(newRemoteUsers)
        }

        // Build up both complete lists to avoid query/fetch again
        val mergedLocalUserList = localUserList + newLocalUsers
        val mergedRemoteUserList = remoteUserList + newRemoteUsers

        val modifiedLocalUsers = Synchronization.collectModifiedUserItems(mergedLocalUserList, mergedRemoteUserList)
        L.d("UserManager", "synchronizeUsers(): Modified user items for local database: ${modifiedLocalUsers.buildShortUserList()}")

        if (modifiedLocalUsers.isNotEmpty()) {
            updateModifiedUsersToLocalDatabase(modifiedLocalUsers)
        }

        val modifiedRemoteUsers = Synchronization.collectModifiedUserItems(mergedRemoteUserList, mergedLocalUserList)
        L.d("UserManager", "synchronizeUsers(): Modified user items for remote database: ${modifiedRemoteUsers.buildShortUserList()}")

        if (modifiedRemoteUsers.isNotEmpty()) {
            updateModifiedUsersToRemoteDatabase(modifiedRemoteUsers)
        }

        L.d("UserManager", "synchronizeUsers(): Finished successfully")
    }

    private suspend fun fetchLocalUserList(): List<User> {
        val localUsersList = localRepository.findAllUsers().takeIf { it.isNotEmpty() }
        return localUsersList ?: throw UserSynchronizationException("The local user list is null - can't process with synchronization!")
    }

    private suspend fun fetchRemoteUserList(): List<User> {
        val remoteUsersListRequest = remoteWebservice.getUsersAsync()
        val response = remoteUsersListRequest.await()
        return response.body() ?: throw UserSynchronizationException("The remote user list is null - can't process with synchronization!")
    }

    private suspend fun addNewUsersToLocalDatabase(newUsers: List<User>) {
        localRepository.insertUser(*newUsers.toTypedArray())
    }

    private suspend fun addNewUsersToRemoteDatabase(newUsers: List<User>) {
        val remoteUsersListRequest = remoteWebservice.addUsersAsync(newUsers)
        val requestDeferred = remoteUsersListRequest.await()

        if (!requestDeferred.isSuccessful) {
            throw UserSynchronizationException("The users could not be added to remote database (HTTP error code ${requestDeferred.code()})!")
        }
    }

    private suspend fun updateModifiedUsersToLocalDatabase(modifiedUsers: List<User>) {
        localRepository.updateUser(*modifiedUsers.toTypedArray())
    }

    private suspend fun updateModifiedUsersToRemoteDatabase(modifiedUsers: List<User>) {
        val remoteUsersListRequest = remoteWebservice.updateUsersAsync(modifiedUsers)
        val requestDeferred = remoteUsersListRequest.await()

        if (!requestDeferred.isSuccessful) {
            throw UserSynchronizationException("The users could not be updated on remote database (HTTP error code ${requestDeferred.code()})!")
        }
    }

    class UserCreationFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
    class LoginFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
    class UserSynchronizationException(message: String) : Exception(message)
}

private const val SERIALIZATION_KEY_LOGGED_IN_USERNAME = "loggedInUsername"
private const val SERIALIZATION_KEY_LOGGED_IN_SERVERURL = "loggedInServerUrl"

data class LoggedInUserResult(val user: User, val masterPassword: String?)

private val currentDate
    get() = Date.from(Instant.now())

private fun List<User>.buildShortUserList(): List<String> {
    return this.map { "'${it.username}' (${it.modified})" }
}
