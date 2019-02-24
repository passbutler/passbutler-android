package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MutableLiveData
import android.arch.persistence.room.Room
import android.content.Context.MODE_PRIVATE
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import de.sicherheitskritisch.passbutler.common.AsyncCallback
import de.sicherheitskritisch.passbutler.common.L
import de.sicherheitskritisch.passbutler.common.PassDatabase
import de.sicherheitskritisch.passbutler.common.readTextFileContents
import de.sicherheitskritisch.passbutler.models.ResponseUserConverterFactory
import de.sicherheitskritisch.passbutler.models.User
import de.sicherheitskritisch.passbutler.models.UserWebservice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlin.coroutines.CoroutineContext

object UserManager : CoroutineScope {

    internal val loggedInUser = MutableLiveData<User?>()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    private val coroutineJob = Job()

    private val localDatabase by lazy {
        Room.databaseBuilder(applicationContext, PassDatabase::class.java, "PassButlerDatabase").build()
    }

    val remoteWebservice: UserWebservice by lazy {
        // TODO: Use server url given by user
        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.0.0.20:5000")
            .addConverterFactory(ResponseUserConverterFactory())
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
                    val demoModeUsersFileContents = responseReader.readTextFileContents()
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

    fun restoreLoggedInUser() {
        launch(context = Dispatchers.IO) {
            val restoredLoggedInUser = sharedPreferences.getString(SERIALIZATION_KEY_LOGGED_IN_USERNAME, null)?.let { loggedInUsername ->
                localDatabase.userDao().findUser(loggedInUsername)
            }
            loggedInUser.postValue(restoredLoggedInUser)
        }
    }

    fun userList(): List<User> {
        return localDatabase.userDao().findAll()
    }

    fun updateUser(user: User) {
        launch(context = Dispatchers.IO) {
            localDatabase.userDao().update(user)
        }
    }

    fun logoutUser() {
        launch(context = Dispatchers.IO) {
            localDatabase.clearAllTables()
            sharedPreferences.edit().clear().apply()
            loggedInUser.postValue(null)
        }
    }

    private fun storeUser(user: User) {
        launch(context = Dispatchers.IO) {
            localDatabase.userDao().insert(user)
            sharedPreferences.edit().putString(SERIALIZATION_KEY_LOGGED_IN_USERNAME, user.username).apply()
            loggedInUser.postValue(user)
        }
    }
}

private const val SERIALIZATION_KEY_LOGGED_IN_USERNAME = "loggedInUsername"

class LoginFailedException : Exception()
class DemoModeLoginFailedException : Exception()