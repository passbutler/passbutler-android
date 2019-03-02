package de.sicherheitskritisch.passbutler

import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeViewModel
import de.sicherheitskritisch.passbutler.common.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.common.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OverviewViewModel : CoroutineScopeViewModel() {

    override val coroutineDispatcher = Dispatchers.IO

    internal val synchronizeDataRequestSendingViewModel = DefaultRequestSendingViewModel()

    private var synchronizeDataJob: Job? = null

    internal fun synchronizeData() {
        // Cancels previous job, until the new job is started to prevent multiple refresh
        synchronizeDataJob?.cancel()
        synchronizeDataJob = launch {
            synchronizeDataRequestSendingViewModel.isLoading.postValue(true)

            try {
                UserManager.synchronizeUsers()
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
            UserManager.logoutUser()
        }
    }
}
