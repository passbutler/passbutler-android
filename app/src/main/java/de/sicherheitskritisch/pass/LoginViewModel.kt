package de.sicherheitskritisch.pass

import android.os.Handler
import android.os.Looper
import de.sicherheitskritisch.pass.common.RequestSendingViewModel
import java.util.*

class LoginViewModel : RequestSendingViewModel() {

    internal fun login(username: String, password: String) {
        isLoading.value = true

        // TODO: Remove mocking
        Timer().schedule(object : TimerTask() {
            override fun run() {
                Handler(Looper.getMainLooper()).post {
                    isLoading.value = false
                    requestFinishedSuccessfully.emit()
                }
            }
        }, 2000)
    }
}