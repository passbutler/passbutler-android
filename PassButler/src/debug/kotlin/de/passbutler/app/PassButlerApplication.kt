package de.passbutler.app

import android.os.Build
import android.os.StrictMode
import de.passbutler.app.base.AbstractPassButlerApplication
import de.passbutler.common.base.formattedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.tinylog.configuration.Configuration
import org.tinylog.kotlin.Logger
import java.time.Instant
import java.util.*

class PassButlerApplication : AbstractPassButlerApplication() {

    override fun setupStrictMode() {
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

    override fun setupLogger() {
        Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler())

        /*
         * Initialize Tinylog on IO to avoid disk read violations: despite it has `writingthread = true`,
         * the configuration and first write is done on calling thread.
         */
        GlobalScope.launch(Dispatchers.IO) {
            Configuration.replace(createLoggerConfiguration())

            val loggingHeader = createLoggingHeader()
            Logger.debug("Started Pass Butler\n$loggingHeader")
        }
    }

    private fun createLoggerConfiguration(): Map<String, String> {
        return mapOf(
            "writer1" to "logcat",
            "writer1.level" to "trace",
            "writer1.format" to "{class-name}.{method}() [{thread}]: {message}",

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

    private fun createLoggingHeader(): String {
        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE
        val formattedBuildTime = Instant.ofEpochMilli(BuildConfig.BUILD_TIMESTAMP).formattedDateTime
        val gitShortHash = BuildConfig.BUILD_REVISION_HASH

        return buildString {
            appendLine("--------------------------------------------------------------------------------")
            appendLine("App:         ${BuildConfig.APPLICATION_ID} $versionName-$versionCode (build on $formattedBuildTime from $gitShortHash)")
            appendLine("Android:     ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT} (${Build.VERSION.INCREMENTAL}, ${Build.VERSION.CODENAME})")
            appendLine("Brand:       ${Build.BRAND}")
            appendLine("Model:       ${Build.MODEL}")
            appendLine("Product:     ${Build.PRODUCT}")
            appendLine("Device:      ${Build.DEVICE}")
            appendLine("Fingerprint: ${Build.FINGERPRINT}")
            appendLine("Tags:        ${Build.TAGS}")
            appendLine("Hardware:    ${Build.HARDWARE}")
            appendLine("Locale:      ${Locale.getDefault()}")
            appendLine("--------------------------------------------------------------------------------")
        }
    }
}

private class UncaughtExceptionHandler : Thread.UncaughtExceptionHandler {
    private val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        Logger.error(e, "⚠️⚠️⚠️ FATAL ⚠️⚠️⚠️")
        defaultUncaughtExceptionHandler?.uncaughtException(t, e)
    }
}
