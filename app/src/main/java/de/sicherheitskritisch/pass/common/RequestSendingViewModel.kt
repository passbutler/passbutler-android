package de.sicherheitskritisch.pass.common

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel

open class RequestSendingViewModel : ViewModel() {
    val isLoading = MutableLiveData<Boolean>().also { it.value = false }
    val requestError = MutableLiveData<Exception>()
    val requestFinishedSuccessfully = SignalEmitter()
}