package de.sicherheitskritisch.passbutler

import android.app.Application
import android.content.Context

abstract class AbstractPassButlerApplication : Application() {

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