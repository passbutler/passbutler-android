package de.sicherheitskritisch.pass

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import android.support.annotation.MainThread
import de.sicherheitskritisch.pass.common.AsyncCallback
import java.util.*

class RootViewModel : ViewModel() {

    val rootScreenState = MutableLiveData<RootScreenState>()

    val userAccountViewModel = MutableLiveData<UserAccountViewModel>()

    private val userAccountViewModelObserver = Observer<UserAccountViewModel> {
        val isLoggedIn = (userAccountViewModel.value != null)

        rootScreenState.value = when {
            isLoggedIn -> RootScreenState.LoggedIn(isUnlocked = true)
            else -> RootScreenState.LoggedOut()
        }
    }

    init {
        userAccountViewModel.observeForever(userAccountViewModelObserver)

        // TODO: try to build UserAccountViewModel from storage
        userAccountViewModel.value = null
    }

    override fun onCleared() {
        userAccountViewModel.removeObserver(userAccountViewModelObserver)
    }

    @MainThread
    fun loginUser(serverUrl: String, username: String, password: String, asyncCallback: AsyncCallback<Unit, Exception>) {
        // TODO: Remove mocking
        Timer().schedule(object : TimerTask() {
            override fun run() {
                userAccountViewModel.postValue(UserAccountViewModel(UserAccount(serverUrl, username)))
                asyncCallback.onSuccess()
            }
        }, 2000)
    }

    @MainThread
    fun loginDemoUser(asyncCallback: AsyncCallback<Unit, Exception>) {
        // TODO: Use mocked data from json
        Timer().schedule(object : TimerTask() {
            override fun run() {
                userAccountViewModel.postValue(UserAccountViewModel(UserAccount("localhost", "demo@sicherheitskritisch.de")))
                asyncCallback.onSuccess()
            }
        }, 1500)
    }

    @MainThread
    fun logoutUser() {
        // TODO: Delete stored data from user
        userAccountViewModel.value = null
    }

    sealed class RootScreenState {
        class LoggedIn(val isUnlocked: Boolean) : RootScreenState()
        class LoggedOut : RootScreenState()
    }
}
