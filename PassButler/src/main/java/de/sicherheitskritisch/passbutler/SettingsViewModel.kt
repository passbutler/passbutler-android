package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.ViewModel

class SettingsViewModel(internal var rootViewModel: RootViewModel? = null) : ViewModel() {

    internal val userViewModel
        get() = rootViewModel?.loggedInUserViewModel
}