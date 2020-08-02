package de.passbutler.app

import androidx.lifecycle.ViewModel
import de.passbutler.app.base.AbstractPassButlerApplication
import de.passbutler.common.base.Result

class LoginViewModel : ViewModel() {
    suspend fun loginUser(serverUrlString: String?, username: String, masterPassword: String): Result<Unit> {
        val userManager = AbstractPassButlerApplication.userManager

        return when (serverUrlString) {
            null -> userManager.loginLocalUser(username, masterPassword)
            else -> userManager.loginRemoteUser(username, masterPassword, serverUrlString)
        }
    }
}