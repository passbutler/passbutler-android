package de.sicherheitskritisch.passbutler.base

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import de.sicherheitskritisch.passbutler.ui.BaseFragment
import de.sicherheitskritisch.passbutler.ui.showError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

interface RequestSendingViewModel {
    val isLoading: NonNullMutableLiveData<Boolean>
    val requestError: MutableLiveData<Throwable?>
    val requestFinishedSuccessfully: SignalEmitter
}

open class DefaultRequestSendingViewModel : ViewModel(), RequestSendingViewModel {
    override val isLoading = NonNullMutableLiveData(false)
    override val requestError = MutableLiveData<Throwable?>()
    override val requestFinishedSuccessfully = SignalEmitter()
}

fun CoroutineScope.createRequestSendingJob(requestSendingViewModel: RequestSendingViewModel, block: suspend () -> Unit): Job {
    return launch {
        requestSendingViewModel.isLoading.postValue(true)

        try {
            block.invoke()

            requestSendingViewModel.isLoading.postValue(false)
            requestSendingViewModel.requestFinishedSuccessfully.emit()
        } catch (e: Exception) {
            L.w("SettingsViewModel", "createRequestSendingJob(): The operation failed with exception!", e)
            requestSendingViewModel.isLoading.postValue(false)
            requestSendingViewModel.requestError.postValue(e)
        }
    }
}

open class RequestSendingViewHandler(private val requestSendingViewModel: RequestSendingViewModel) {

    private val isLoadingObserver = Observer<Boolean> { newValue ->
        onIsLoadingChanged(newValue)
    }

    private val requestErrorObserver = Observer<Throwable?> { newValue ->
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

    open fun onRequestErrorChanged(requestError: Throwable) {
        // Override if desired
    }

    open fun onRequestFinishedSuccessfully() {
        // Override if desired
    }
}

abstract class DefaultRequestSendingViewHandler<T : BaseFragment>(
    requestSendingViewModel: RequestSendingViewModel,
    private val fragmentWeakReference: WeakReference<T>
) : RequestSendingViewHandler(requestSendingViewModel) {

    protected val fragment
        get() = fragmentWeakReference.get()

    protected val resources
        get() = fragment?.resources

    override fun onIsLoadingChanged(isLoading: Boolean) {
        if (isLoading) {
            fragment?.showProgress()
        } else {
            fragment?.hideProgress()
        }
    }

    override fun onRequestErrorChanged(requestError: Throwable) {
        val errorMessageResourceId = requestErrorMessageResourceId(requestError)

        resources?.getString(errorMessageResourceId)?.let { errorMessage ->
            fragment?.showError(errorMessage)
        }
    }

    abstract fun requestErrorMessageResourceId(requestError: Throwable): Int
}
