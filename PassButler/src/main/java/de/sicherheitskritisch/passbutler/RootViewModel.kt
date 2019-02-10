package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import de.sicherheitskritisch.passbutler.common.User
import de.sicherheitskritisch.passbutler.common.observeForever

class RootViewModel : ViewModel() {

    internal val rootScreenState = MediatorLiveData<RootScreenState>()

    private val storedUser
        get() = UserManager.storedUser

    private val storedUserObserver = Observer<User?> { newValue ->
        val isLoggedIn = (newValue != null)
        rootScreenState.value = when (isLoggedIn) {
            true -> RootScreenState.LoggedIn(true)
            false -> RootScreenState.LoggedOut
        }
    }

    init {
        storedUser.observeForever(true, storedUserObserver)
    }

    sealed class RootScreenState {
        class LoggedIn(val isUnlocked: Boolean) : RootScreenState()
        object LoggedOut : RootScreenState()
    }
}
