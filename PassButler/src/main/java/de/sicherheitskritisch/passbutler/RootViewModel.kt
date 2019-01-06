package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.support.annotation.MainThread
import de.sicherheitskritisch.passbutler.common.AsyncCallback
import java.util.*

class RootViewModel : ViewModel() {

    val rootScreenState = MediatorLiveData<RootScreenState>()
    val userAccountViewModel = MutableLiveData<UserAccountViewModel?>()

    init {
        rootScreenState.addSource(userAccountViewModel) {
            val isLoggedIn = (userAccountViewModel.value != null)
            rootScreenState.value = when (isLoggedIn) {
                true -> RootScreenState.LoggedIn(isUnlocked = true)
                false -> RootScreenState.LoggedOut
            }
        }

        // TODO: try to build UserAccountViewModel from storage
        userAccountViewModel.value = null
    }

    @MainThread
    fun loginUser(serverUrl: String, username: String, password: String, asyncCallback: AsyncCallback<Unit, Exception>) {
        // TODO: Remove mocking
        Timer().schedule(object : TimerTask() {
            override fun run() {
                asyncCallback.onFailure(Exception())
            }
        }, 1000)
    }

    @MainThread
    fun loginDemoUser(asyncCallback: AsyncCallback<Unit, Exception>) {
        // TODO: Use mocked data from json
        Timer().schedule(object : TimerTask() {
            override fun run() {
                userAccountViewModel.postValue(UserAccountViewModel(UserAccount("localhost", "demo@sicherheitskritisch.de")))
                asyncCallback.onSuccess()
            }
        }, 1000)
    }

    @MainThread
    fun logoutUser() {
        // TODO: Delete stored data from user
        userAccountViewModel.value = null
    }

    sealed class RootScreenState {
        class LoggedIn(val isUnlocked: Boolean) : RootScreenState()
        object LoggedOut : RootScreenState()
    }
}
