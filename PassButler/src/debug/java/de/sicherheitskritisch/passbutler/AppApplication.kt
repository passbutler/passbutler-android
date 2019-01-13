package de.sicherheitskritisch.passbutler

import android.app.Application
import com.squareup.leakcanary.LeakCanary

class AppApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // If process is dedicated to LeakCanary, do not initialize normal application
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return
        }

        LeakCanary.install(this)
    }
}