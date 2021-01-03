package de.passbutler.app

import android.app.Application
import android.content.Context
import de.passbutler.app.base.BuildInformationProvider
import de.passbutler.app.base.LoggingSetupProviding
import de.passbutler.app.base.loggingSetupProvider
import de.passbutler.app.database.createLocalRepository
import de.passbutler.common.UserManager
import kotlinx.coroutines.runBlocking

class PassButlerApplication : Application(), LoggingSetupProviding by loggingSetupProvider {

    override fun onCreate() {
        super.onCreate()

        val logFilePath = "${applicationContext.cacheDir.path}/passbutler-debug.log"
        setupLogging(logFilePath)

        Companion.applicationContext = applicationContext
        userManager = createUserManager()
    }

    private fun createUserManager(): UserManager {
        val localRepository = runBlocking {
            createLocalRepository(applicationContext)
        }

        return UserManager(localRepository, BuildInformationProvider)
    }

    companion object {
        lateinit var applicationContext: Context
            private set

        lateinit var userManager: UserManager
            private set
    }
}
