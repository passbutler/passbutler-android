package de.passbutler.app

import de.passbutler.common.LoggedInUserViewModelUninitializedException
import de.passbutler.common.base.Result

class OverviewViewModel : UserViewModelUsingViewModel() {
    suspend fun synchronizeData(): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        return loggedInUserViewModel.synchronizeData()
    }
}
