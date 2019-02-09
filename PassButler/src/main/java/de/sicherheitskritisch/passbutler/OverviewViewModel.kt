package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.ViewModel

class OverviewViewModel() : ViewModel() {

    internal val storedUser
        get() = UserManager.storedUser

    internal fun logoutUser() {
        UserManager.logoutUser()
    }
}