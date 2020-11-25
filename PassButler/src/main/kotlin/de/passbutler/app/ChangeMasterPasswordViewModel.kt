package de.passbutler.app

import de.passbutler.common.LoggedInUserViewModelUninitializedException
import de.passbutler.common.base.Result

class ChangeMasterPasswordViewModel : UserViewModelUsingViewModel() {
    suspend fun changeMasterPassword(oldMasterPassword: String, newMasterPassword: String): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        return loggedInUserViewModel.updateMasterPassword(oldMasterPassword, newMasterPassword)
    }
}