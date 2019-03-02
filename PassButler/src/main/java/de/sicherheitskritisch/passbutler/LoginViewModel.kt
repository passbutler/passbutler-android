package de.sicherheitskritisch.passbutler

import android.app.Application
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import de.sicherheitskritisch.passbutler.common.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.common.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    override val coroutineDispatcher = Dispatchers.IO

    internal val loginRequestSendingViewModel = DefaultRequestSendingViewModel()

    private val userManager
        get() = getApplication<AbstractPassButlerApplication>().userManager

    private var loginJob: Job? = null

    fun loginUser(serverUrl: String, username: String, password: String) {
        loginJob?.cancel()
        loginJob = launch {
            loginRequestSendingViewModel.isLoading.postValue(true)

            try {
                userManager.loginUser(serverUrl, username, password)
                loginRequestSendingViewModel.requestFinishedSuccessfully.emit()
            } catch (exception: LoginFailedException) {
                L.w("UserManager", "loginUser(): The login failed with exception!", exception)
                loginRequestSendingViewModel.requestError.postValue(exception)
            } finally {
                loginRequestSendingViewModel.isLoading.postValue(false)
            }
        }
    }

    fun loginDemoUser() {
        loginJob?.cancel()
        loginJob = launch {
            loginRequestSendingViewModel.isLoading.postValue(true)

            try {
                userManager.loginDemoUser()
                loginRequestSendingViewModel.requestFinishedSuccessfully.emit()
            } catch (exception: LoginFailedException) {
                L.w("UserManager", "loginDemoUser(): The demo login failed with exception!", exception)
                loginRequestSendingViewModel.requestError.postValue(exception)
            } finally {
                loginRequestSendingViewModel.isLoading.postValue(false)
            }
        }
    }
}