package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MutableLiveData
import android.content.Context.MODE_PRIVATE
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import de.sicherheitskritisch.passbutler.common.L
import de.sicherheitskritisch.passbutler.common.Synchronization
import de.sicherheitskritisch.passbutler.common.readTextFileContents
import de.sicherheitskritisch.passbutler.database.PassButlerRepository
import de.sicherheitskritisch.passbutler.models.User
import de.sicherheitskritisch.passbutler.models.UserConverterFactory
import de.sicherheitskritisch.passbutler.models.UserWebservice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.CoroutineContext

object UserManager : CoroutineScope {

    internal val loggedInUser = MutableLiveData<User?>()

    internal val isDemoMode
        get() = sharedPreferences.getBoolean(SERIALIZATION_KEY_DEMOMODE, false)

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    private val coroutineJob = SupervisorJob()

    private val localRepository by lazy {
        // TODO: Do not hold it here, it handles not only users
        PassButlerRepository(applicationContext)
    }

    private val remoteWebservice: UserWebservice by lazy {
        // TODO: Use server url given by user
        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.0.0.20:5000")
            .addConverterFactory(UserConverterFactory())
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .build()

        retrofit.create(UserWebservice::class.java)
    }

    private val sharedPreferences by lazy {
        applicationContext.getSharedPreferences("UserManager", MODE_PRIVATE)
    }

    private val applicationContext
        get() = AbstractPassButlerApplication.applicationContext

    suspend fun loginUser(userName: String, password: String, serverUrl: String) {
        // TODO: Connect to server
        // TODO: Authenticate with given credentials
        // TODO: If successful, store server url and credentials
        // TODO: Load users list and find user

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

    suspend fun loginDemoUser() {
        // Add an artificial delay for login progress simulation
        delay(1000)

        try {
            val assetsDirectory = AbstractPassButlerApplication.applicationContext.assets
            BufferedReader(InputStreamReader(assetsDirectory.open("demomode_users.json"))).use { responseReader ->
                val demoModeUsersFileContents = responseReader.readTextFileContents()
                val demoModeUsers = JSONArray(demoModeUsersFileContents)
                val demoModeUserJsonObject = demoModeUsers.getJSONObject(0)

                User.deserialize(demoModeUserJsonObject)?.let { deserializedUser ->
                    storeUser(deserializedUser, true)
                } ?: run {
                    throw LoginFailedException("The demo mode users file could not be deserialized!")
                }
            }
        } catch (exception: Exception) {
            throw LoginFailedException("The demo mode users file could not be read!", exception)
        }
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
        launch {
            localRepository.updateUser(user)
        }
    }

    suspend fun synchronizeUsers() {
        // TODO: Remove artificial delay:
        delay(1000)

        // TODO: Could be done async/independently parallel
        val localUserList = fetchLocalUserList()
        val remoteUserList = fetchRemoteUserList()

        val newLocalUserItemList = Synchronization.collectNewUserItems(localUserList, remoteUserList)
        L.d("UserManager", "synchronizeUsers(): New user items for local database: $newLocalUserItemList")
        addNewUsersToLocalDatabase(newLocalUserItemList)

        val newRemoteUserItemList = Synchronization.collectNewUserItems(remoteUserList, localUserList)
        L.d("UserManager", "synchronizeUsers(): New user items for remote database: $newRemoteUserItemList")
        addNewUsersToRemoteDatabase(newRemoteUserItemList)

        L.d("UserManager", "synchronizeUsers(): Finished")
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

    private suspend fun addNewUsersToLocalDatabase(newLocalUserItemList: List<User>) {
        localRepository.insertUser(*newLocalUserItemList.toTypedArray())
    }

    private suspend fun addNewUsersToRemoteDatabase(newRemoteUserItemList: List<User>) {
        val remoteUsersListRequest = remoteWebservice.addUsersAsync(newRemoteUserItemList)
        remoteUsersListRequest.await()
    }

    private suspend fun storeUser(user: User, isDemoMode: Boolean) {
        localRepository.insertUser(user)

        sharedPreferences.edit().also {
            it.putString(SERIALIZATION_KEY_LOGGED_IN_USERNAME, user.username)
            it.putBoolean(SERIALIZATION_KEY_DEMOMODE, isDemoMode)
        }.apply()

        loggedInUser.postValue(user)
    }
}

private const val SERIALIZATION_KEY_LOGGED_IN_USERNAME = "loggedInUsername"
private const val SERIALIZATION_KEY_DEMOMODE = "isDemoMode"

class LoginFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
class UserSynchronizationException(message: String) : Exception(message)