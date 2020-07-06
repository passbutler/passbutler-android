package de.passbutler.app

import de.passbutler.common.base.Result
import kotlinx.coroutines.delay

class OverviewViewModel : UserViewModelUsingViewModel() {

    suspend fun synchronizeData(): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        return loggedInUserViewModel.synchronizeData()
    }

    suspend fun logoutUser(): Result<Unit> {
        // Some artificial delay to look flow more natural
        delay(500)

        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        return loggedInUserViewModel.logout()
    }
}
