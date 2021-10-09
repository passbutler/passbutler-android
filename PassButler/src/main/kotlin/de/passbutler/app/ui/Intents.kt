package de.passbutler.app.ui

import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment
import org.tinylog.kotlin.Logger

fun Fragment.openBrowser(url: String) {
    try {
        val openBrowserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(openBrowserIntent)
    } catch (exception: Exception) {
        Logger.warn(exception, "The URL could not be opened!")
    }
}

fun Fragment.openWriteEmail(email: String) {
    try {
        val writeEmailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
        startActivity(writeEmailIntent)
    } catch (exception: Exception) {
        Logger.warn(exception, "The open write email intent could not be sent!")
    }
}
