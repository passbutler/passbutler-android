package de.sicherheitskritisch.passbutler

import android.os.StrictMode
import com.squareup.leakcanary.LeakCanary

class PassButlerApplication : AbstractPassButlerApplication() {
    override fun onCreate() {
        super.onCreate()

        // If process is dedicated to LeakCanary, do not initialize normal application
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return
        }

        LeakCanary.install(this)

        setupStrictMode()
    }

    private fun setupStrictMode() {
        val threadPolicy = StrictMode.ThreadPolicy.Builder()
            .detectAll()
            .penaltyLog()
            .build()

        val vmPolicy = StrictMode.VmPolicy.Builder()
            .detectActivityLeaks()
            .detectLeakedClosableObjects()
            .detectLeakedSqlLiteObjects()
            .detectLeakedRegistrationObjects()
            .detectFileUriExposure()
            .penaltyLog()
            .build()

        StrictMode.setThreadPolicy(threadPolicy)
        StrictMode.setVmPolicy(vmPolicy)
    }
}