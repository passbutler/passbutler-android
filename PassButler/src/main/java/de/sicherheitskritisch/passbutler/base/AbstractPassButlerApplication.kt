package de.sicherheitskritisch.passbutler.base

import android.app.Application
import android.content.Context
import de.sicherheitskritisch.passbutler.UserManager
import de.sicherheitskritisch.passbutler.database.PassButlerRepository

abstract class AbstractPassButlerApplication : Application() {

    internal val userManager by lazy {
        UserManager(applicationContext, localRepository)
    }

    private val localRepository by lazy {
        PassButlerRepository(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        AbstractPassButlerApplication.applicationContext = applicationContext
    }

    companion object {
        // If the context is accessed before application `onCreate` there is something wrong, so doublebang is okay here
        lateinit var applicationContext: Context
            private set
    }
}