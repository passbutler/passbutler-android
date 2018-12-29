package de.sicherheitskritisch.pass

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel

class LoginViewModel : ViewModel() {

    val isLoading = MutableLiveData<Boolean>().also { it.value = false }

    internal fun login(username: String, password: String) {
        isLoading.value = !(isLoading.value ?: false)
    }
}