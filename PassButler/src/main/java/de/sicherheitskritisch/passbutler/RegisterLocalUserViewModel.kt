package de.sicherheitskritisch.passbutler

import android.app.Application
import android.net.Uri
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.Success
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel

class RegisterLocalUserViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    lateinit var rootViewModel: RootViewModel

    suspend fun registerLocalUser(serverUrlString: String, masterPassword: String): Result<Unit> {
        val userManager = rootViewModel.userManager
        val serverUrl = Uri.parse(serverUrlString)
        return userManager.registerLocalUser(serverUrl, masterPassword)
    }
}