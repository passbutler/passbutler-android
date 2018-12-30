package de.sicherheitskritisch.pass

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel

class RootViewModel : ViewModel() {

    val isLoggedIn = MutableLiveData<Boolean>().also { it.value = false }
    val isUnlocked = MutableLiveData<Boolean>().also { it.value = false }

    init {
        // TODO: initial values
    }
}
