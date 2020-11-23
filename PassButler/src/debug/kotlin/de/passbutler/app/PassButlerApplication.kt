package de.passbutler.app

import android.os.Build
import android.os.StrictMode
import de.passbutler.app.base.AbstractPassButlerApplication
import de.passbutler.common.base.LoggingConstants
import de.passbutler.common.base.formattedDateTime
import org.tinylog.configuration.Configuration
import org.tinylog.kotlin.Logger
import java.time.Instant
import java.util.*

class PassButlerApplication : AbstractPassButlerApplication() {

    override fun setupLogger() {
        Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler())

        Configuration.replace(createLoggerConfiguration())

        val loggingHeader = createLoggingHeader()
        Logger.debug("Started Pass Butler\n$loggingHeader")
    }

    private fun createLoggerConfiguration(): Map<String, String> {
        val consoleLogFormat = "{class-name}.{method}() [{thread}]: {message}"
        val fileLogFormat = LoggingConstants.LOG_FORMAT_FILE
        val logFilePath = "${applicationContext.cacheDir.path}/passbutler-debug.log"

        return mapOf(
            "writer1" to "logcat",
            "writer1.level" to "trace",
            "writer1.format" to consoleLogFormat,

            "writer2" to "file",
            "writer2.level" to "debug",
            "writer2.format" to fileLogFormat,
            "writer2.file" to logFilePath,
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
}

private class UncaughtExceptionHandler : Thread.UncaughtExceptionHandler {
    private val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        Logger.error(e, "⚠️⚠️⚠️ FATAL ⚠️⚠️⚠️")
        defaultUncaughtExceptionHandler?.uncaughtException(t, e)
    }
}
