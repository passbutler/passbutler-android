package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MutableLiveData
import android.arch.persistence.room.Room
import de.sicherheitskritisch.passbutler.common.AsyncCallback
import de.sicherheitskritisch.passbutler.common.PassDatabase
import de.sicherheitskritisch.passbutler.common.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.CoroutineContext

// TODO: Put to assets
private const val DEMOMODE_USERS_RESPONSE = """
[
    {
        "username": "demouser@sicherheitskritisch.de",
        "lockTimeout": 2,
        "lastModified": 123,
        "created": 123
    }
]
"""

object UserManager : CoroutineScope {

    internal val loggedInUser = MutableLiveData<User?>()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    private val coroutineJob = Job()

    private val roomDatabase by lazy {
        val applicationContext = AbstractPassButlerApplication.applicationContext

        // TODO: Remove `fallbackToDestructiveMigration()` for production
        Room.databaseBuilder(applicationContext, PassDatabase::class.java, "PassButlerDatabase")
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .build()
    }

    fun restoreLoggedInUser() {
        val restoredLoggedInUser = roomDatabase.userDao().findLoggedInUser()
        loggedInUser.value = restoredLoggedInUser
    }

    fun loginUser(userName: String, password: String, serverUrl: String, asyncCallback: AsyncCallback<Unit, Exception>) {
        launch {
            // TODO: Connect to server
            // TODO: Authenticate with given credentials
            // TODO: If successful, store server url and credentials
            // TODO: Load users list and find user

            val userJsonObject = JSONObject()

            User.deserialize(userJsonObject)?.let { realUser ->
                // Mark user model as logged-in user and persist it
                realUser.isLoggedIn = true
                persistUser(realUser)

                asyncCallback.onSuccess()
            } ?: run {
                asyncCallback.onFailure(LoginFailedException())
            }
        }
    }

    fun loginDemoUser(asyncCallback: AsyncCallback<Unit, Exception>) {
        launch {
            // Add an artificial delay for login progress simulation
            delay(1000)

            val demoModeUsers = JSONArray(DEMOMODE_USERS_RESPONSE)
            val demoModeUserJsonObject = demoModeUsers.getJSONObject(0)

            User.deserialize(demoModeUserJsonObject)?.let { demoUser ->
                // Mark user model as logged-in user and persist it
                demoUser.isLoggedIn = true
                persistUser(demoUser)

                asyncCallback.onSuccess()
            } ?: run {
                asyncCallback.onFailure(DemoModeLoginFailedException())
            }
        }
    }

    fun logoutUser() {
        roomDatabase.clearAllTables()
        loggedInUser.value = null
    }

    private fun persistUser(user: User) {
        roomDatabase.userDao().insert(user)
        loggedInUser.postValue(user)
    }
}

class LoginFailedException : Exception()
class DemoModeLoginFailedException : Exception()