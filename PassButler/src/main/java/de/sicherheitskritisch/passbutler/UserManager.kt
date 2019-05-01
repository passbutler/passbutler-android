package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MutableLiveData
import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.UnitConverterFactory
import de.sicherheitskritisch.passbutler.crypto.Algorithm
import de.sicherheitskritisch.passbutler.crypto.ProtectedValue
import de.sicherheitskritisch.passbutler.crypto.clear
import de.sicherheitskritisch.passbutler.database.PassButlerRepository
import de.sicherheitskritisch.passbutler.database.Synchronization
import de.sicherheitskritisch.passbutler.database.models.User
import de.sicherheitskritisch.passbutler.database.models.UserConverterFactory
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import de.sicherheitskritisch.passbutler.database.models.UserWebservice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Retrofit
import java.time.Instant
import java.util.*
import kotlin.coroutines.CoroutineContext

class UserManager(applicationContext: Context, private val localRepository: PassButlerRepository) : CoroutineScope {

    internal val loggedInUser = MutableLiveData<User?>()

    internal val isLocalUser
        get() = sharedPreferences.getBoolean(SERIALIZATION_KEY_IS_LOCAL_USER, false)

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    private val coroutineJob = SupervisorJob()

    // TODO: Pass object via constructor instead creating here + use server url from preferences
    private val remoteWebservice: UserWebservice by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://172.16.0.125:5000")
            .addConverterFactory(UnitConverterFactory())
            .addConverterFactory(UserConverterFactory())
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .build()

        retrofit.create(UserWebservice::class.java)
    }

    private val sharedPreferences by lazy {
        applicationContext.getSharedPreferences("UserManager", MODE_PRIVATE)
    }

    private val currentDate
        get() = Date.from(Instant.now())

    suspend fun loginUser(userName: String, password: String, serverUrl: String) {
        /*
         * TODO:
         * - Connect to server
         * - Authenticate with given credentials
         * - If successful, store server url and credentials
         * - Load users list and find user
         */
        try {
            val userJsonObject = JSONObject()

            User.deserialize(userJsonObject)?.let { deserializedUser ->
                storeUser(deserializedUser, false)
            } ?: run {
                throw LoginFailedException("The given user could not be deserialized!")
            }
        } catch (exception: Exception) {
            throw LoginFailedException("The login failed with an exception!", exception)
        }
    }

    suspend fun loginLocalUser() {
        // Add an artificial delay for login progress simulation
        delay(1000)

        // TODO: Use proper key
        val userSettingsEncryptionKey = ByteArray(0)
        val defaultUserSettings = UserSettings()
        val localUser = ProtectedValue.create(Algorithm.AES256GCM, userSettingsEncryptionKey, defaultUserSettings)?.let { protectedUserSettings ->
            val currentDate = currentDate

            User(
                username = "local@passbutler.app",
                settings = protectedUserSettings,
                deleted = false,
                modified = currentDate,
                created = currentDate
            )
        } ?: run {
            throw LoginFailedException("The local user could not be created because settings creation failed!")
        }

        userSettingsEncryptionKey.clear()
        storeUser(localUser, true)
    }

    suspend fun logoutUser() {
        localRepository.reset()
        sharedPreferences.edit().clear().apply()
        loggedInUser.postValue(null)
    }

    fun restoreLoggedInUser() {
        launch {
            val restoredLoggedInUser = sharedPreferences.getString(SERIALIZATION_KEY_LOGGED_IN_USERNAME, null)?.let { loggedInUsername ->
                localRepository.findUser(loggedInUsername)
            }
            loggedInUser.postValue(restoredLoggedInUser)
        }
    }

    fun updateUser(user: User) {
        L.d("UserManager", "updateUser(): user = $user")

        launch {
            user.modified = currentDate
            localRepository.updateUser(user)

            // TODO: Trigger sync?
        }
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

    private suspend fun storeUser(user: User, isLocalUser: Boolean) {
        localRepository.insertUser(user)

        sharedPreferences.edit().also {
            it.putString(SERIALIZATION_KEY_LOGGED_IN_USERNAME, user.username)
            it.putBoolean(SERIALIZATION_KEY_IS_LOCAL_USER, isLocalUser)
        }.apply()

        loggedInUser.postValue(user)
    }
}

private const val SERIALIZATION_KEY_LOGGED_IN_USERNAME = "loggedInUsername"
private const val SERIALIZATION_KEY_IS_LOCAL_USER = "isLocalUser"

class LoginFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
class UserSynchronizationException(message: String) : Exception(message)

private fun List<User>.buildShortUserList(): List<String> {
    return this.map { "'${it.username}' (${it.modified})" }
}
