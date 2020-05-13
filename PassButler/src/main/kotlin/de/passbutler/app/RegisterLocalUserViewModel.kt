package de.passbutler.app

import de.passbutler.app.base.viewmodels.CoroutineScopedViewModel
import de.passbutler.common.UserManagerUninitializedException
import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result

class RegisterLocalUserViewModel : CoroutineScopedViewModel() {

    lateinit var rootViewModel: RootViewModel

    suspend fun registerLocalUser(serverUrlString: String, masterPassword: String): Result<Unit> {
        val userManager = rootViewModel.userManager ?: throw UserManagerUninitializedException
        val loggedInUserViewModel = rootViewModel.loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException

        // Check the given master password locally to avoid the auth webservice is initialized with non-working authentication
        val masterPasswordTestResult = loggedInUserViewModel.decryptMasterEncryptionKey(masterPassword)

        return if (masterPasswordTestResult is Failure) {
            masterPasswordTestResult
        } else {
            userManager.registerLocalUser(serverUrlString, masterPassword)
        }
    }
}