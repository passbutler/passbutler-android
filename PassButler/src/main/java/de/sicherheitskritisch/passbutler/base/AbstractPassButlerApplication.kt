package de.sicherheitskritisch.passbutler.base

import android.app.Application
import android.content.Context
import de.sicherheitskritisch.passbutler.UserManager
import de.sicherheitskritisch.passbutler.database.LocalRepository

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

