package de.passbutler.app.base

import android.app.Application
import android.content.Context
import de.passbutler.app.UserManager
import de.passbutler.app.database.LocalRepository

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

        setupStrictMode()
        setupLogger()
    }

    abstract fun setupStrictMode()
    abstract fun setupLogger()

    companion object {
        lateinit var applicationContext: Context
            private set
    }
}

