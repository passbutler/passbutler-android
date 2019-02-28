package de.sicherheitskritisch.passbutler

import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeViewModel
import de.sicherheitskritisch.passbutler.common.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.common.L
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OverviewViewModel(internal var rootViewModel: RootViewModel? = null) : CoroutineScopeViewModel() {

    val userViewModel
        get() = rootViewModel?.loggedInUserViewModel

    val synchronizeDataRequestSendingViewModel = DefaultRequestSendingViewModel()

    private var synchronizeDataJob: Job? = null

    fun synchronizeData() {
        // Cancels previous job, until the new job is started
        synchronizeDataJob?.cancel()
        synchronizeDataJob = launch {
            synchronizeDataRequestSendingViewModel.isLoading.postValue(true)

            L.d("OverviewViewModel", "synchronizeData(): CR")

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

    fun logoutUser() {
        UserManager.logoutUser()
    }

    // TODO: This is not called on fragment destruction
    override fun onCleared() {
        super.onCleared()
    }
}