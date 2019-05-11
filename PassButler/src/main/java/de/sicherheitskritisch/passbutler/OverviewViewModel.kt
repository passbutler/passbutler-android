package de.sicherheitskritisch.passbutler

import android.app.Application
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OverviewViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    val synchronizeDataRequestSendingViewModel = DefaultRequestSendingViewModel()

    private var synchronizeDataCoroutineJob: Job? = null

    private val userManager
        get() = getApplication<AbstractPassButlerApplication>().userManager

    fun synchronizeData() {
        synchronizeDataCoroutineJob?.cancel()
        synchronizeDataCoroutineJob = launch(Dispatchers.IO) {
            synchronizeDataRequestSendingViewModel.isLoading.postValue(true)

            try {
                userManager.synchronizeUsers()
                synchronizeDataRequestSendingViewModel.requestFinishedSuccessfully.emit()
            } catch (exception: Exception) {
                L.w("OverviewViewModel", "synchronizeData(): The synchronization failed with exception!", exception)
                synchronizeDataRequestSendingViewModel.requestError.postValue(exception)
            } finally {
                synchronizeDataRequestSendingViewModel.isLoading.postValue(false)
            }
        }
    }

    fun logoutUser() {
        // TODO: If `logoutUser()` takes longer than `OverviewViewModel` is cleared, the job is canceled
        // We do not care for logout job, fire and forget here
        launch {
            userManager.logoutUser()
        }
    }
}
