package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.ViewModel

class UserAccountViewModel(private val userAccount: UserAccount) : ViewModel() {

    val serverUrl
        get() = userAccount.serverUrl

    val username
        get() = userAccount.username

}