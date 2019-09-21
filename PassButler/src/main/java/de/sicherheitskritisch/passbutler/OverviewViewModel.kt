package de.sicherheitskritisch.passbutler

import android.app.Application
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.createRequestSendingJob
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class OverviewViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    var rootViewModel: RootViewModel? = null
    var loggedInUserViewModel: UserViewModel? = null

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
        synchronizeDataCoroutineJob = createRequestSendingJob(synchronizeDataRequestSendingViewModel) {
            loggedInUserViewModel?.synchronizeData()
        }
    }

    fun logoutUser() {
        logoutCoroutineJob?.cancel()
        logoutCoroutineJob = createRequestSendingJob(logoutRequestSendingViewModel) {
            userManager.logoutUser()

            // Some artificial delay to look flow more natural
            delay(1000)
        }
    }
}
