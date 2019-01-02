package de.sicherheitskritisch.passbutler.common

import android.util.Log

typealias L = Logger

class Logger {
    companion object {
        fun d(message: String) {
            buildLogInformation()?.let {
                Log.d("${it.fileName}:${it.lineNumber}", "[${it.threadName}]: $message")
            }
        }
    }
}

private fun buildLogInformation(): LogMetaInformation? {
    // Be sure enough trace elements are available (otherwise ProGuard may be enabled)
    val throwableStacktrace = Throwable().stackTrace.takeIf { it.size >= 3 }

    return throwableStacktrace?.let { stackTrace ->
        val fileName = stackTrace[2].fileName
        val lineNumber = stackTrace[2].lineNumber
        val threadName = Thread.currentThread().name
        LogMetaInformation(fileName, lineNumber, threadName)
    }
}

data class LogMetaInformation(val fileName: String, val lineNumber: Int, val threadName: String)
