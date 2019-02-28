package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MutableLiveData
import android.arch.persistence.room.Room
import android.content.Context.MODE_PRIVATE
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import de.sicherheitskritisch.passbutler.common.AsyncCallback
import de.sicherheitskritisch.passbutler.common.L
import de.sicherheitskritisch.passbutler.common.PassDatabase
import de.sicherheitskritisch.passbutler.common.Synchronization
import de.sicherheitskritisch.passbutler.common.readTextFileContents
import de.sicherheitskritisch.passbutler.models.User
import de.sicherheitskritisch.passbutler.models.UserConverterFactory
import de.sicherheitskritisch.passbutler.models.UserWebservice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlin.coroutines.CoroutineContext

object UserManager : CoroutineScope {

    internal val loggedInUser = MutableLiveData<User?>()

    internal val isDemoMode
        get() = sharedPreferences.getBoolean(SERIALIZATION_KEY_DEMOMODE, false)

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    private val coroutineJob = Job()

    private val localDatabase by lazy {
        Room.databaseBuilder(applicationContext, PassDatabase::class.java, "PassButlerDatabase").build()
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

    fun loginUser(userName: String, password: String, serverUrl: String, asyncCallback: AsyncCallback<Unit, Exception>) {
        launch(context = Dispatchers.IO) {
            // TODO: Connect to server
            // TODO: Authenticate with given credentials
            // TODO: If successful, store server url and credentials
            // TODO: Load users list and find user

            val userJsonObject = JSONObject()

            User.deserialize(userJsonObject)?.let { deserializedUser ->
                storeUser(deserializedUser, false)
                asyncCallback.onSuccess()
            } ?: run {
                asyncCallback.onFailure(LoginFailedException())
            }
        }
    }

    fun loginDemoUser(asyncCallback: AsyncCallback<Unit, Exception>) {
        launch(context = Dispatchers.IO) {
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
                        asyncCallback.onSuccess()
                    } ?: run {
                        asyncCallback.onFailure(DemoModeLoginFailedException())
                    }
                }
            } catch (e: IOException) {
                L.w("UserManager", "loginDemoUser(): The demo mode users file could not be read!", e)
                asyncCallback.onFailure(DemoModeLoginFailedException())
            }
        }
    }

    fun logoutUser() {
        launch(context = Dispatchers.IO) {
            localDatabase.clearAllTables()
            sharedPreferences.edit().clear().apply()
            loggedInUser.postValue(null)
        }
    }

    fun restoreLoggedInUser() {
        launch(context = Dispatchers.IO) {
            val restoredLoggedInUser = sharedPreferences.getString(SERIALIZATION_KEY_LOGGED_IN_USERNAME, null)?.let { loggedInUsername ->
                localDatabase.userDao().findUser(loggedInUsername)
            }
            loggedInUser.postValue(restoredLoggedInUser)
        }
    }

    fun updateUser(user: User) {
        launch(context = Dispatchers.IO) {
            localDatabase.userDao().update(user)
        }
    }

    suspend fun synchronizeUsers() {
        withContext(Dispatchers.IO) {

            // TODO: remove
            delay(1000)

            try {
                val remoteUserList = fetchRemoteUserList() ?: throw UserSynchronizationException("The remote user list is null - can't process with synchronization!")
                val localUserList = localDatabase.userDao().findAll()

                val newLocalUserItemList = Synchronization.collectNewUserItems(localUserList, remoteUserList)
                L.d("UserManager", "synchronizeUsers(): The following new users will be added to local database: $newLocalUserItemList")

                if (!addNewUsersToLocalDatabase(newLocalUserItemList)) {
                    throw UserSynchronizationException("The new users item could not be added to local database - can't process with synchronization!")
                }

                val newRemoteUserItemList = Synchronization.collectNewUserItems(remoteUserList, localUserList)
                L.d("UserManager", "synchronizeUsers(): The following new users will be added to remote database: $newRemoteUserItemList")

                if (!addNewUsersToRemoteDatabase(newRemoteUserItemList)) {
                    throw UserSynchronizationException("The new users item could not be added to remote database - can't process with synchronization!")
                }

                L.d("UserManager", "synchronizeUsers(): Finished")

            } catch (e: UserSynchronizationException) {
                L.d("UserManager", "synchronizeUsers(): ${e.message}")
            }
        }
    }

    private suspend fun fetchRemoteUserList(): List<User>? {
        return try {
            val remoteUsersListRequest = remoteWebservice.getUsersAsync()
            val response = remoteUsersListRequest.await()
            response.body()
        } catch (e: Exception) {
            L.w("UserManager", "fetchRemoteUserList(): The remote user list could not be fetched!", e)
            null
        }
    }

    private suspend fun addNewUsersToLocalDatabase(newLocalUserItemList: List<User>): Boolean {
        withContext(Dispatchers.IO) {
            localDatabase.userDao().insert(*newLocalUserItemList.toTypedArray())
        }
        return true
    }

    private suspend fun addNewUsersToRemoteDatabase(newRemoteUserItemList: List<User>): Boolean {
        return try {
            val remoteUsersListRequest = remoteWebservice.addUsersAsync(newRemoteUserItemList)
            remoteUsersListRequest.await()
            true
        } catch (e: Exception) {
            L.w("UserManager", "addNewUsersToRemoteDatabase(): The new remote user items could not be added!", e)
            false
        }
    }

    private fun storeUser(user: User, isDemoMode: Boolean) {
        launch(context = Dispatchers.IO) {
            localDatabase.userDao().insert(user)

            sharedPreferences.edit().also {
                it.putString(SERIALIZATION_KEY_LOGGED_IN_USERNAME, user.username)
                it.putBoolean(SERIALIZATION_KEY_DEMOMODE, isDemoMode)
            }.apply()

            loggedInUser.postValue(user)
        }
    }
}

private const val SERIALIZATION_KEY_LOGGED_IN_USERNAME = "loggedInUsername"
private const val SERIALIZATION_KEY_DEMOMODE = "isDemoMode"

class LoginFailedException : Exception()
class DemoModeLoginFailedException : Exception()

class UserSynchronizationException(message: String) : Exception(message)