package de.sicherheitskritisch.passbutler

import android.os.StrictMode
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication

class PassButlerApplication : AbstractPassButlerApplication() {

    override fun onCreate() {
        super.onCreate()
        setupStrictMode()
    }

    override fun createLoggerConfiguration(): Map<String, String> {
        return mapOf(
            "writer1" to "logcat",
            "writer1.level" to "trace",
            "writer1.format" to "{class-name}.{method}(): [{thread}] {message}",

            "writer2" to "file",
            "writer2.level" to "debug",
            "writer2.format" to "{date} {level} {class-name}.{method}() [{thread}]: {message}",
            "writer2.file" to "${applicationContext.cacheDir.path}/passbutler-debug.log",
            "writer2.charset" to "UTF-8",
            "writer2.append" to "true",
            "writer2.buffered" to "true",

            "writingthread" to "true"
        )
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