package de.sicherheitskritisch.passbutler

import android.app.Application
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OverviewViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    val userType
        get() = userManager.loggedInStateStorage.userType

    val synchronizeDataRequestSendingViewModel = DefaultRequestSendingViewModel()
    val logoutRequestSendingViewModel = DefaultRequestSendingViewModel()

    private var synchronizeDataCoroutineJob: Job? = null
    private var logoutCoroutineJob: Job? = null

    private val userManager
        get() = getApplication<AbstractPassButlerApplication>().userManager

    fun synchronizeData() {
        synchronizeDataCoroutineJob?.cancel()
        synchronizeDataCoroutineJob = launch {
            synchronizeDataRequestSendingViewModel.isLoading.postValue(true)

            try {
                userManager.synchronizeUsers()

                synchronizeDataRequestSendingViewModel.isLoading.postValue(false)
                synchronizeDataRequestSendingViewModel.requestFinishedSuccessfully.emit()
            } catch (exception: Exception) {
                L.w("OverviewViewModel", "synchronizeData(): The synchronization failed with exception!", exception)
                synchronizeDataRequestSendingViewModel.isLoading.postValue(false)
                synchronizeDataRequestSendingViewModel.requestError.postValue(exception)
            }
        }
    }

    fun logoutUser() {
        logoutCoroutineJob?.cancel()
        logoutCoroutineJob = launch {
            logoutRequestSendingViewModel.isLoading.postValue(true)

            try {
                userManager.logoutUser()

                // Some artificial delay to look flow more natural
                delay(1000)

                logoutRequestSendingViewModel.isLoading.postValue(false)
                logoutRequestSendingViewModel.requestFinishedSuccessfully.emit()
            } catch (exception: Exception) {
                L.w("OverviewViewModel", "logoutUser(): The logout failed with exception!", exception)
                logoutRequestSendingViewModel.isLoading.postValue(false)
                logoutRequestSendingViewModel.requestError.postValue(exception)
            }
        }
    }
}
