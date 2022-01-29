package de.passbutler.app.rules

import android.content.Context
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.screenshot.BasicScreenCaptureProcessor
import androidx.test.runner.screenshot.ScreenCaptureProcessor
import androidx.test.runner.screenshot.Screenshot
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.tinylog.kotlin.Logger
import java.io.File
import java.io.IOException
import java.util.*

val SCREENSHOT_DIRECTORY = "screenshots/" + Locale.getDefault()

class ScreenshotRule : TestWatcher() {
    override fun finished(description: Description?) {
        super.finished(description)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshotName = description?.methodName ?: throw IllegalStateException("The test method name could not be determined!")
        captureScreenshot(context, screenshotName)
    }
}

fun captureScreenshot(context: Context, screenshotName: String) {
    val screenCapture = Screenshot.capture().apply {
        name = screenshotName
        format = Bitmap.CompressFormat.PNG
    }

    val screenCaptureProcessors = setOf<ScreenCaptureProcessor>(ScreenshotTestScreenCaptureProcessor(context))

    try {
        screenCapture.process(screenCaptureProcessors)
    } catch (exception: IOException) {
        Logger.error(exception, "The screenshot could not be taken!")
    }
}

private class ScreenshotTestScreenCaptureProcessor(private val context: Context) : BasicScreenCaptureProcessor() {
    init {
        mDefaultScreenshotPath = getScreenshotsDirectory()
    }

    override fun getFilename(prefix: String): String {
        // Only use prefix (no UUID suffix)
        return prefix
    }

    private fun getScreenshotsDirectory(): File {
        // Use internal storage directory to avoid needing permissions
        return File(context.dataDir, SCREENSHOT_DIRECTORY)
    }
}
