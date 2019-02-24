package de.sicherheitskritisch.passbutler.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val REQUEST_TIMEOUT_MILLISECONDS = 2000

/**
 * Requests text resource from given HTTPS URL.
 */
suspend fun requestTextResource(urlString: String): String? {
    return withContext(Dispatchers.IO) {
        val url = URL(urlString)

        if (url.protocol != "https") {
            throw IllegalArgumentException("Only the HTTPS protocol is allowed!")
        }

        val urlConnection = url.openConnection() as HttpsURLConnection
        urlConnection.connectTimeout = REQUEST_TIMEOUT_MILLISECONDS
        urlConnection.readTimeout = REQUEST_TIMEOUT_MILLISECONDS

        try {
            BufferedInputStream(urlConnection.inputStream).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { responseReader ->
                    responseReader.readTextFileContents()
                }
            }
        } catch (e: IOException) {
            Logger.w("WebRequest", "requestTextResource(): Can't fetch request '$urlString'!", e)
            null
        }
    }
}

fun Reader.readTextFileContents(): String {
    return readLines().joinToString("\n")
}