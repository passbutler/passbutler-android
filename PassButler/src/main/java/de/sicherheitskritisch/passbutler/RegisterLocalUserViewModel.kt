package de.sicherheitskritisch.passbutler

import android.app.Application
import android.net.Uri
import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import java.net.URI

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
            val serverUrl = URI.create(serverUrlString)
            userManager.registerLocalUser(serverUrl, masterPassword)
        }
    }
}