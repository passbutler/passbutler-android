package de.sicherheitskritisch.passbutler.common

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel

interface RequestSendingViewModel {
    val isLoading: MutableLiveData<Boolean>
    val requestError: MutableLiveData<Exception>
    val requestFinishedSuccessfully: SignalEmitter
}

open class DefaultRequestSendingViewModel : ViewModel(), RequestSendingViewModel {
    override val isLoading = MutableLiveData<Boolean>().also { it.value = false }
    override val requestError = MutableLiveData<Exception>()
    override val requestFinishedSuccessfully = SignalEmitter()
}

abstract class RequestSendingViewHandler(private val requestSendingViewModel: RequestSendingViewModel) {

    private val isLoadingObserver = Observer<Boolean> { newValue ->
        newValue?.let {
            onIsLoadingChanged(it)
        }
    }

    private val requestErrorObserver = Observer<Exception> { newValue ->
        newValue?.let {
            onRequestErrorChanged(it)
        }
    }

    private val requestFinishedSuccessfullySignal = signal {
        onRequestFinishedSuccessfully()
    }

    fun registerObservers() {
        requestSendingViewModel.isLoading.observeForever(isLoadingObserver)
        requestSendingViewModel.requestError.observeForever(requestErrorObserver)
        requestSendingViewModel.requestFinishedSuccessfully.addSignal(requestFinishedSuccessfullySignal)
    }

    fun unregisterObservers() {
        requestSendingViewModel.isLoading.removeObserver(isLoadingObserver)
        requestSendingViewModel.requestError.removeObserver(requestErrorObserver)
        requestSendingViewModel.requestFinishedSuccessfully.removeSignal(requestFinishedSuccessfullySignal)
    }

    open fun onIsLoadingChanged(isLoading: Boolean) {
        // Override if desired
    }

    open fun onRequestErrorChanged(requestError: Exception) {
        // Override if desired
    }

    open fun onRequestFinishedSuccessfully() {
        // Override if desired
    }
}