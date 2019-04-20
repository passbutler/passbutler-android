package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {

    var loggedInUserViewModel: UserViewModel? = null

    val lockTimeout: MutableLiveData<Int>?
        get() = loggedInUserViewModel?.lockTimeout
}