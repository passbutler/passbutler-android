package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.ViewModel

class UserAccountViewModel(private val userAccount: UserAccount/*, private val persistenceDelegate: PersistenceDelegate*/) : ViewModel() {

    val serverUrl
        get() = userAccount.serverUrl

    val username
        get() = userAccount.username

    fun changeSomeSetting() {
        // Change setting, than call request persist
        // persistenceDelegate.persist()
    }

}