package de.sicherheitskritisch.passbutler

import android.app.Application
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    val loginRequestSendingViewModel = DefaultRequestSendingViewModel()

    private val userManager
        get() = getApplication<AbstractPassButlerApplication>().userManager

    private var loginCoroutineJob: Job? = null

    fun loginUser(serverUrl: String, username: String, password: String) {
        loginCoroutineJob?.cancel()
        loginCoroutineJob = launch {
            loginRequestSendingViewModel.isLoading.postValue(true)

            try {
                userManager.loginUser(serverUrl, username, password)

                loginRequestSendingViewModel.isLoading.postValue(false)
                loginRequestSendingViewModel.requestFinishedSuccessfully.emit()
            } catch (exception: Exception) {
                L.w("LoginViewModel", "loginUser(): The login failed with exception!", exception)
                loginRequestSendingViewModel.isLoading.postValue(false)
                loginRequestSendingViewModel.requestError.postValue(exception)
            }
        }
    }

    fun loginLocalUser() {
        loginCoroutineJob?.cancel()
        loginCoroutineJob = launch {
            loginRequestSendingViewModel.isLoading.postValue(true)

            try {
                userManager.loginLocalUser()

                loginRequestSendingViewModel.isLoading.postValue(false)
                loginRequestSendingViewModel.requestFinishedSuccessfully.emit()
            } catch (exception: Exception) {
                L.w("LoginViewModel", "loginLocalUser(): The local login failed with exception!", exception)
                loginRequestSendingViewModel.isLoading.postValue(false)
                loginRequestSendingViewModel.requestError.postValue(exception)
            }
        }
    }
}