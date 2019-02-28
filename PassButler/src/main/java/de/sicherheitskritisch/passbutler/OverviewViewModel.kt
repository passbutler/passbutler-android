package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.ViewModel
import de.sicherheitskritisch.passbutler.common.DefaultRequestSendingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class OverviewViewModel(internal var rootViewModel: RootViewModel? = null) : ViewModel(), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + coroutineJob

    private val coroutineJob = Job()

    internal val userViewModel
        get() = rootViewModel?.loggedInUserViewModel

    internal val synchronizeDataRequestSendingViewModel = DefaultRequestSendingViewModel()

    internal fun synchronizeData() {
        synchronizeDataRequestSendingViewModel.isLoading.value = true

        launch(context = Dispatchers.Default) {
            UserManager.synchronizeUsers()
            synchronizeDataRequestSendingViewModel.isLoading.postValue(false)

            // TODO: Set error
        }
    }

    internal fun logoutUser() {
        UserManager.logoutUser()
    }
}