package de.sicherheitskritisch.passbutler

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {

    var loggedInUserViewModel: UserViewModel? = null

    val lockTimeout: MutableLiveData<Int>?
        get() = loggedInUserViewModel?.lockTimeoutSetting
}