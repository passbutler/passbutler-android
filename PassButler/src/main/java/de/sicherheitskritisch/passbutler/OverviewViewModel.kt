package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.ViewModel

class OverviewViewModel(private val rootViewModel: RootViewModel) : ViewModel() {

    internal val userAccountViewModel
        get() = rootViewModel.userAccountViewModel

    internal fun logoutUser() {
        rootViewModel.logoutUser()
    }
}