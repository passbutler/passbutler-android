package de.passbutler.app.base

import android.app.Application
import android.content.Context

abstract class AbstractPassButlerApplication : Application() {

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

