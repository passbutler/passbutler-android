package de.sicherheitskritisch.pass

import de.sicherheitskritisch.pass.common.RequestSendingViewModel
import java.util.*

class LoginViewModel : RequestSendingViewModel() {

    internal fun loginUser(serverUrl: String, username: String, password: String) {
        isLoading.value = true

        // TODO: Remove mocking
        Timer().schedule(object : TimerTask() {
            override fun run() {
                // Thus this is not executed on main thread, use `postValue` instead of `value` setter
                isLoading.postValue(false)
                requestFinishedSuccessfully.emit()
            }
        }, 2000)
    }

    internal fun loginDemoUser() {
        loginUser("https://127.0.0.1", "demouser", "demopassword")
    }
}