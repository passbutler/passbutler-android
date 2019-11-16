package de.sicherheitskritisch.passbutler

import android.app.Application
import de.sicherheitskritisch.passbutler.base.NonNullMutableLiveData
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel

class LoginViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    lateinit var rootViewModel: RootViewModel

    val isLocalLogin = NonNullMutableLiveData(false)

    suspend fun loginUser(serverUrlString: String?, username: String, masterPassword: String): Result<Unit> {
        return rootViewModel.loginUser(serverUrlString, username, masterPassword)
    }
}