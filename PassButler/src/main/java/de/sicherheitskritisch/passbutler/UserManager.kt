package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MutableLiveData
import android.arch.persistence.room.Room
import android.content.Context.MODE_PRIVATE
import de.sicherheitskritisch.passbutler.common.AsyncCallback
import de.sicherheitskritisch.passbutler.common.L
import de.sicherheitskritisch.passbutler.common.PassDatabase
import de.sicherheitskritisch.passbutler.models.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlin.coroutines.CoroutineContext

object UserManager : CoroutineScope {

    internal val loggedInUser = MutableLiveData<User?>()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    private val coroutineJob = Job()

    private val roomDatabase by lazy {
        // TODO: Remove `fallbackToDestructiveMigration()` for production
        Room.databaseBuilder(applicationContext, PassDatabase::class.java, "PassButlerDatabase")
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .build()
    }

    private val sharedPreferences by lazy {
        applicationContext.getSharedPreferences("UserManager", MODE_PRIVATE)
    }

    private val applicationContext
        get() = AbstractPassButlerApplication.applicationContext

    fun userList(): List<User> {
        return roomDatabase.userDao().findAll()
    }

    fun restoreLoggedInUser() {
        val restoredLoggedInUser = sharedPreferences.getString(SERIALIZATION_KEY_LOGGED_IN_USERNAME, null)?.let { loggedInUsername ->
            roomDatabase.userDao().findUser(loggedInUsername)
        }
        loggedInUser.value = restoredLoggedInUser
    }

    fun loginUser(userName: String, password: String, serverUrl: String, asyncCallback: AsyncCallback<Unit, Exception>) {
        launch(context = Dispatchers.IO) {
            // TODO: Connect to server
            // TODO: Authenticate with given credentials
            // TODO: If successful, store server url and credentials
            // TODO: Load users list and find user

            val userJsonObject = JSONObject()

            User.deserialize(userJsonObject)?.let { deserializedUser ->
                storeUser(deserializedUser)
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
                    val demoModeUsersFileContents = responseReader.readLines().joinToString("\n")
                    val demoModeUsers = JSONArray(demoModeUsersFileContents)
                    val demoModeUserJsonObject = demoModeUsers.getJSONObject(0)

                    User.deserialize(demoModeUserJsonObject)?.let { deserializedUser ->
                        storeUser(deserializedUser)
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

    fun updateUser(user: User) {
        roomDatabase.userDao().update(user)
    }

    fun logoutUser() {
        roomDatabase.clearAllTables()
        sharedPreferences.edit().clear().apply()
        loggedInUser.value = null
    }

    private fun storeUser(user: User) {
        roomDatabase.userDao().insert(user)
        sharedPreferences.edit().putString(SERIALIZATION_KEY_LOGGED_IN_USERNAME, user.username).apply()
        loggedInUser.postValue(user)
    }
}

private const val SERIALIZATION_KEY_LOGGED_IN_USERNAME = "loggedInUsername"

class LoginFailedException : Exception()
class DemoModeLoginFailedException : Exception()