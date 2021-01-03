package de.passbutler.app

import android.app.Application
import android.content.Context
import de.passbutler.app.base.BuildInformationProvider
import de.passbutler.app.base.LoggingSetupProviding
import de.passbutler.app.base.loggingSetupProvider
import de.passbutler.app.database.createLocalRepository
import de.passbutler.common.UserManager
import kotlinx.coroutines.runBlocking
import org.tinylog.kotlin.Logger

class PassButlerApplication : Application(), LoggingSetupProviding by loggingSetupProvider {

    override fun onCreate() {
        super.onCreate()

        setupCrashHandler()
        setupLogging()

        Companion.applicationContext = applicationContext
        userManager = createUserManager()
    }

    private fun setupCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler())
    }

    private fun setupLogging() {
        val logFilePath = "${applicationContext.cacheDir.path}/passbutler-debug.log"
        setupLogging(logFilePath)
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

private class UncaughtExceptionHandler : Thread.UncaughtExceptionHandler {
    private val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        Logger.error(e, "⚠️⚠️⚠️ FATAL ⚠️⚠️⚠️")
        defaultUncaughtExceptionHandler?.uncaughtException(t, e)
    }
}
