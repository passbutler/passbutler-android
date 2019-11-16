package de.sicherheitskritisch.passbutler

import android.app.Application
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel

class OverviewViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    lateinit var rootViewModel: RootViewModel

    val loggedInUserViewModel
        get() = rootViewModel.loggedInUserViewModel

    suspend fun synchronizeData(): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw IllegalStateException("The logged in user viewmodel is null!")
        return loggedInUserViewModel.synchronizeData()
    }

    suspend fun logoutUser(): Result<Unit> {
        return rootViewModel.logoutUser()
    }
}
