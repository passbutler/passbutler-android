package de.sicherheitskritisch.passbutler

import android.app.Application
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.Success
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel

class MigrateLocalUserViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    lateinit var rootViewModel: RootViewModel

    suspend fun migrateLocalUser(serverUrlString: String): Result<Unit> {
//        val userManager = rootViewModel.userManager
//
//        val serverUrl = Uri.parse(serverUrlString)
//        return userManager.loginRemoteUser(serverUrl)

        return Success(Unit)
    }
}