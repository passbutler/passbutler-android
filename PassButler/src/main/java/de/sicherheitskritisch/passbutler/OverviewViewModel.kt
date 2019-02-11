package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.ViewModel

class OverviewViewModel(internal var rootViewModel: RootViewModel? = null) : ViewModel() {

    internal val userViewModel
        get() = rootViewModel?.loggedInUserViewModel

    internal fun logoutUser() {
        UserManager.logoutUser()
    }
}