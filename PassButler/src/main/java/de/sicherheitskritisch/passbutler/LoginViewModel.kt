package de.sicherheitskritisch.passbutler

import de.sicherheitskritisch.passbutler.common.AsyncCallbackResult
import de.sicherheitskritisch.passbutler.common.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.common.asyncCallback

class LoginViewModel : DefaultRequestSendingViewModel() {

    internal fun loginUser(serverUrl: String, username: String, password: String) {
        isLoading.value = true

        UserManager.loginUser(serverUrl, username, password, asyncCallback { result ->
            isLoading.postValue(false)

            when (result) {
                is AsyncCallbackResult.Success -> requestFinishedSuccessfully.emit()
                is AsyncCallbackResult.Failure -> requestError.postValue(result.error)
            }
        })
    }

    internal fun loginDemoUser() {
        isLoading.value = true

        UserManager.loginDemoUser(asyncCallback { result ->
            isLoading.postValue(false)

            when (result) {
                is AsyncCallbackResult.Success -> requestFinishedSuccessfully.emit()
                is AsyncCallbackResult.Failure -> requestError.postValue(result.error)
            }
        })
    }
}