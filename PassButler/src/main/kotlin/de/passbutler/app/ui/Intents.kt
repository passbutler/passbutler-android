package de.passbutler.app.ui

import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment
import org.tinylog.kotlin.Logger

fun Fragment.openBrowser(url: String) {
    try {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
    } catch (exception: Exception) {
        Logger.warn(exception, "The URL could not be opened!")
    }
}
