package de.sicherheitskritisch.passbutler

import android.app.Application
import android.net.Uri
import de.sicherheitskritisch.passbutler.base.NonNullMutableLiveData
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel

class LoginViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    lateinit var rootViewModel: RootViewModel

    val isLocalLogin = NonNullMutableLiveData(false)

    suspend fun loginUser(serverUrlString: String?, username: String, masterPassword: String): Result<Unit> {
        val userManager = rootViewModel.userManager

        return when (serverUrlString) {
            null -> userManager.loginLocalUser(username, masterPassword)
            else -> {
                val serverUrl = Uri.parse(serverUrlString)
                userManager.loginRemoteUser(username, masterPassword, serverUrl)
            }
        }
    }
}