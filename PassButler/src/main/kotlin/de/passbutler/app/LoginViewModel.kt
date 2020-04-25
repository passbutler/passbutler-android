package de.passbutler.app

import android.app.Application
import de.passbutler.app.base.NonNullMutableLiveData
import de.passbutler.app.base.viewmodels.CoroutineScopeAndroidViewModel
import de.passbutler.common.base.Result

class LoginViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    lateinit var rootViewModel: RootViewModel

    val isLocalLogin = NonNullMutableLiveData(false)

    suspend fun loginUser(serverUrlString: String?, username: String, masterPassword: String): Result<Unit> {
        val userManager = rootViewModel.userManager

        return when (serverUrlString) {
            null -> userManager.loginLocalUser(username, masterPassword)
            else -> userManager.loginRemoteUser(username, masterPassword, serverUrlString)
        }
    }
}