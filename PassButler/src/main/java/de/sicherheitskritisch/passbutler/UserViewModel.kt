package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import de.sicherheitskritisch.passbutler.common.User

class UserViewModel(user: User): ViewModel() {

    internal val username = MutableLiveData<String?>()

    init {
        username.value = user.username
    }
}