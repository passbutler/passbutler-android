package de.sicherheitskritisch.passbutler

import android.app.Application
import android.net.Uri
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.NonNullMutableLiveData
import de.sicherheitskritisch.passbutler.base.createRequestSendingJob
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import kotlinx.coroutines.Job

class LoginViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    val isLocalLogin = NonNullMutableLiveData(false)

    val loginRequestSendingViewModel = DefaultRequestSendingViewModel()

    private val userManager
        get() = getApplication<AbstractPassButlerApplication>().userManager

    private var loginCoroutineJob: Job? = null

    fun loginUser(serverUrlString: String, username: String, masterPassword: String) {
        loginCoroutineJob?.cancel()
        loginCoroutineJob = createRequestSendingJob(loginRequestSendingViewModel) {
            if (isLocalLogin.value) {
                userManager.loginLocalUser(username, masterPassword)
            } else {
                val serverUrl = Uri.parse(serverUrlString)
                userManager.loginRemoteUser(username, masterPassword, serverUrl)
            }
        }
    }
}