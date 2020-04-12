package de.sicherheitskritisch.passbutler

import android.app.Application
import android.net.Uri
import de.sicherheitskritisch.passbutler.base.Failure
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel

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
            val serverUrl = Uri.parse(serverUrlString)
            userManager.registerLocalUser(serverUrl, masterPassword)
        }
    }
}