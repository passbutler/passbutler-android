package de.sicherheitskritisch.passbutler.base

import android.app.Application
import android.content.Context
import de.sicherheitskritisch.passbutler.UserManager
import de.sicherheitskritisch.passbutler.database.LocalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

abstract class AbstractPassButlerApplication : Application() {

    internal val userManager by lazy {
        UserManager(applicationContext, localRepository)
    }

    private val localRepository by lazy {
        LocalRepository(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        AbstractPassButlerApplication.applicationContext = applicationContext

        setupLogger()
        setupUncaughtExceptionHandler()
    }

    private fun setupLogger() {
        /*
         * Initialize Tinylog on IO to avoid disk read violations: despite it has `writingthread = true`,
         * the first write is done on calling thread when logger is still not initialized.
         */
        GlobalScope.launch(Dispatchers.IO) {
            Logger.debug("Started Pass Butler")
        }
    }

    private fun setupUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler())
    }

    companion object {
        lateinit var applicationContext: Context
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
