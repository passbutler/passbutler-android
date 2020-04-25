package de.passbutler.app

import android.app.Application
import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.passbutler.app.base.viewmodels.CoroutineScopeAndroidViewModel

class RegisterLocalUserViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    lateinit var rootViewModel: RootViewModel

    suspend fun registerLocalUser(serverUrlString: String, masterPassword: String): Result<Unit> {
        val userManager = rootViewModel.userManager
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