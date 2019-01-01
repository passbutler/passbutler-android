package de.sicherheitskritisch.pass

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel

class OverviewViewModel(private val rootViewModel: RootViewModel) : ViewModel() {

    // TODO: remove set
    val username = MutableLiveData<String>().also { it.value = "Test" }

    init {
        // TODO: not observable
        // rootViewModel.userAccountViewModel?.username
    }

    internal fun logoutUser() {
        rootViewModel.logoutUser()
    }
}