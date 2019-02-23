package de.sicherheitskritisch.passbutler.io

import de.sicherheitskritisch.passbutler.common.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
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
                    val readResponse = responseReader.readLines().joinToString("\n")
                    readResponse
                }
            }
        } catch (e: IOException) {
            L.w("WebRequest", "requestTextResource(): Can't fetch request '$urlString'!", e)
            null
        }
    }
}
