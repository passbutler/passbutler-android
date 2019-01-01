package de.sicherheitskritisch.pass

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import de.sicherheitskritisch.pass.common.AsyncCallback
import java.util.*

class RootViewModel : ViewModel() {

    val rootScreenState = MutableLiveData<RootScreenState>()

    var userAccountViewModel: UserAccountViewModel? = null
        private set(value) {
            if (value != field) {
                field = value
                initializeValuesFromUserAccountViewModel()
            }
        }

    private fun initializeValuesFromUserAccountViewModel() {
        val isLoggedIn = (userAccountViewModel != null)
        val newRootScreenState = when {
            isLoggedIn -> RootScreenState.LoggedIn(isUnlocked = true)
            else -> RootScreenState.LoggedOut()
        }

        rootScreenState.postValue(newRootScreenState)
    }

    init {
        // TODO: try to build UserAccountViewModel from storage
    }

    fun loginUser(serverUrl: String, username: String, password: String, asyncCallback: AsyncCallback<Unit, Exception>) {
        // TODO: Remove mocking
        Timer().schedule(object : TimerTask() {
            override fun run() {
                userAccountViewModel = UserAccountViewModel(UserAccount(serverUrl, username))
                asyncCallback.onSuccess()
            }
        }, 2000)
    }

    fun loginDemoUser(asyncCallback: AsyncCallback<Unit, Exception>) {
        // TODO: Use mocked data from json
        Timer().schedule(object : TimerTask() {
            override fun run() {
                userAccountViewModel = UserAccountViewModel(UserAccount("localhost", "demo@sicherheitskritisch.de"))
                asyncCallback.onSuccess()
            }
        }, 2000)
    }

    fun logoutUser() {
        // TODO: Delete stored data from user
        userAccountViewModel = null
    }

    sealed class RootScreenState {
        class LoggedIn(val isUnlocked: Boolean) : RootScreenState()
        class LoggedOut : RootScreenState()
    }
}
