package de.sicherheitskritisch.passbutler

import android.app.Application
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.createRequestSendingJob
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import kotlinx.coroutines.Job

class OverviewViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    lateinit var rootViewModel: RootViewModel

    val loggedInUserViewModel
        get() = rootViewModel.loggedInUserViewModel

    val synchronizeDataRequestSendingViewModel = DefaultRequestSendingViewModel()

    private var synchronizeDataCoroutineJob: Job? = null

    fun synchronizeData() {
        synchronizeDataCoroutineJob?.cancel()
        synchronizeDataCoroutineJob = createRequestSendingJob(synchronizeDataRequestSendingViewModel) {
            loggedInUserViewModel?.synchronizeData()
        }
    }
}
