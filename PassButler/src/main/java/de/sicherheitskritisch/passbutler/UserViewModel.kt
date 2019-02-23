package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import de.sicherheitskritisch.passbutler.models.User

class UserViewModel(private val user: User) : ViewModel() {

    val username = MutableLiveData<String>()
    val lockTimeout = MutableLiveData<Int>()

    private val lockTimeoutObserver = Observer<Int> {
        if (it != null) {
            user.lockTimeout = it
            UserManager.updateUser(user)
        }
    }

    init {
        username.value = user.username
        lockTimeout.value = user.lockTimeout

        registerObservers()
    }

    private fun registerObservers() {
        lockTimeout.observeForever(lockTimeoutObserver)
    }
}