package de.sicherheitskritisch.pass

import de.sicherheitskritisch.pass.common.AsyncCallbackResult
import de.sicherheitskritisch.pass.common.DefaultRequestSendingViewModel
import de.sicherheitskritisch.pass.common.asyncCallback

class LoginViewModel(private val rootViewModel: RootViewModel) : DefaultRequestSendingViewModel() {

    internal fun loginUser(serverUrl: String, username: String, password: String) {
        isLoading.value = true

        rootViewModel.loginUser(serverUrl, username, password, asyncCallback { result ->
            isLoading.postValue(false)

            when (result) {
                is AsyncCallbackResult.Success -> requestFinishedSuccessfully.emit()
                is AsyncCallbackResult.Failure -> requestError.postValue(result.error)
            }
        })
    }

    internal fun loginDemoUser() {
        isLoading.value = true

        rootViewModel.loginDemoUser(asyncCallback { result ->
            isLoading.postValue(false)

            when (result) {
                is AsyncCallbackResult.Success -> requestFinishedSuccessfully.emit()
                is AsyncCallbackResult.Failure -> requestError.postValue(result.error)
            }
        })
    }
}