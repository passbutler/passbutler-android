package de.passbutler.app

import de.passbutler.app.base.NonNullMutableLiveData
import de.passbutler.app.base.viewmodels.CoroutineScopedViewModel
import de.passbutler.common.base.Result

class LoginViewModel : CoroutineScopedViewModel() {

    lateinit var rootViewModel: RootViewModel

    val isLocalLogin = NonNullMutableLiveData(false)

    suspend fun loginUser(serverUrlString: String?, username: String, masterPassword: String): Result<Unit> {
        val userManager = rootViewModel.userManager ?: throw UserManagerUninitializedException

        return when (serverUrlString) {
            null -> userManager.loginLocalUser(username, masterPassword)
            else -> userManager.loginRemoteUser(username, masterPassword, serverUrlString)
        }
    }
}