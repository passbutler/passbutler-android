package de.sicherheitskritisch.passbutler.common

import android.util.Log

typealias L = Logger

class Logger {
    companion object {
        fun v(message: String) = buildLogInformation()?.let {
            Log.v(it.buildLogTag(), it.buildLogString(message))
        }

        fun d(message: String) = buildLogInformation()?.let {
            Log.d(it.buildLogTag(), it.buildLogString(message))
        }

        fun i(message: String) = buildLogInformation()?.let {
            Log.i(it.buildLogTag(), it.buildLogString(message))
        }

        fun w(message: String) = buildLogInformation()?.let {
            Log.w(it.buildLogTag(), it.buildLogString(message))
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

private fun LogMetaInformation.buildLogTag(): String {
    return "$fileName:$lineNumber"
}

private fun LogMetaInformation.buildLogString(message: String): String {
    return "[$threadName]: $message"
}