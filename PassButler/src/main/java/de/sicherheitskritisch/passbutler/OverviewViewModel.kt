package de.sicherheitskritisch.passbutler

import android.app.Application
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import kotlinx.coroutines.delay

class OverviewViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    var loggedInUserViewModel: UserViewModel? = null

    val isSynchronizationVisible
        get() = loggedInUserViewModel?.isServerUserType ?: false

    val isSynchronizationPossible
        get() = loggedInUserViewModel?.isSynchronizationPossible ?: false

    suspend fun synchronizeData(): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw IllegalStateException("The logged-in user viewmodel is null!")
        return loggedInUserViewModel.synchronizeData()
    }

    suspend fun logoutUser(): Result<Unit> {
        // Some artificial delay to look flow more natural
        delay(500)

        val loggedInUserViewModel = loggedInUserViewModel ?: throw IllegalStateException("The logged-in user viewmodel is null!")
        return loggedInUserViewModel.logout()
    }
}
