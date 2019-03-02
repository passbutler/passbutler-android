package de.sicherheitskritisch.passbutler

import android.app.Application
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import de.sicherheitskritisch.passbutler.common.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.common.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OverviewViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    override val coroutineDispatcher = Dispatchers.IO

    internal val synchronizeDataRequestSendingViewModel = DefaultRequestSendingViewModel()

    private var synchronizeDataJob: Job? = null

    private val userManager
        get() = getApplication<AbstractPassButlerApplication>().userManager

    internal fun synchronizeData() {
        // Cancels previous job, until the new job is started to prevent multiple refresh
        synchronizeDataJob?.cancel()
        synchronizeDataJob = launch {
            synchronizeDataRequestSendingViewModel.isLoading.postValue(true)

            try {
                userManager.synchronizeUsers()
                synchronizeDataRequestSendingViewModel.requestFinishedSuccessfully.emit()
            } catch (exception: Exception) {
                L.w("UserManager", "synchronizeData(): The synchronization failed with exception!", exception)
                synchronizeDataRequestSendingViewModel.requestError.postValue(exception)
            } finally {
                synchronizeDataRequestSendingViewModel.isLoading.postValue(false)
            }
        }
    }

    internal fun logoutUser() {
        // We do not care for logout job, fire and forget here
        launch {
            userManager.logoutUser()
        }
    }
}
