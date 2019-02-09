package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MutableLiveData
import android.arch.persistence.room.Room
import de.sicherheitskritisch.passbutler.common.AsyncCallback
import de.sicherheitskritisch.passbutler.common.PassDatabase
import de.sicherheitskritisch.passbutler.common.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.time.Instant
import java.util.*
import kotlin.coroutines.CoroutineContext

private const val DEMOMODE_USERNAME = "demouser@sicherheitskritisch.de"

object UserManager : CoroutineScope {

    val storedUser = MutableLiveData<User?>()

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

    fun restoreUser(): User? {
        return roomDatabase.userDao().findByUsername(DEMOMODE_USERNAME)
    }

    fun persistUser(user: User) {
        roomDatabase.userDao().insert(user)
    }

    fun removeUser(user: User) {
        roomDatabase.userDao().delete(user)
    }

    fun loginUser(userName: String, password: String, serverUrl: String, asyncCallback: AsyncCallback<Unit, Exception>) {
        // TODO: Use coroutines
        Timer().schedule(object : TimerTask() {
            override fun run() {
                val now = Date.from(Instant.now())
                persistUser(User(DEMOMODE_USERNAME, now, now))

                asyncCallback.onSuccess()
            }
        }, 1000)
    }

    fun loginDemoUser(asyncCallback: AsyncCallback<Unit, Exception>) {
        // TODO: Use coroutines
        Timer().schedule(object : TimerTask() {
            override fun run() {
                val now = Date.from(Instant.now())
                val newUser = User(DEMOMODE_USERNAME, now, now)
                persistUser(newUser)

                storedUser.postValue(newUser)

                asyncCallback.onSuccess()
            }
        }, 1000)
    }

    fun logoutUser() {
        // TODO: Remove user
        storedUser.postValue(null)
    }
}