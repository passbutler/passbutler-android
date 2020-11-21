package de.passbutler.app.base

import android.app.Application
import android.content.Context
import de.passbutler.app.database.createLocalRepository
import de.passbutler.common.UserManager
import kotlinx.coroutines.runBlocking

abstract class AbstractPassButlerApplication : Application() {

    @Suppress("RemoveRedundantQualifierName")
    override fun onCreate() {
        super.onCreate()

        setupStrictMode()
        setupLogger()

        AbstractPassButlerApplication.applicationContext = applicationContext
        AbstractPassButlerApplication.userManager = createUserManager()
    }

    private fun createUserManager(): UserManager {
        val localRepository = runBlocking {
            createLocalRepository(applicationContext)
        }

        return UserManager(localRepository, BuildInformationProvider)
    }

    abstract fun setupStrictMode()
    abstract fun setupLogger()

    companion object {
        lateinit var applicationContext: Context
            private set

        lateinit var userManager: UserManager
            private set
    }
}

