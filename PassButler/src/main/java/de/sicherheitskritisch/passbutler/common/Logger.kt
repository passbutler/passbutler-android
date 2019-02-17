package de.sicherheitskritisch.passbutler.common

import android.util.Log

typealias L = Logger

object Logger {
    fun v(tag: String, message: String) = Log.v(tag, buildLogString(message))
    fun d(tag: String, message: String) = Log.d(tag, buildLogString(message))
    fun i(tag: String, message: String) = Log.i(tag, buildLogString(message))
    fun w(tag: String, message: String) = Log.w(tag, buildLogString(message))
    fun w(tag: String, message: String, throwable: Throwable) = Log.w(tag, buildLogString(message, throwable))
}

private fun buildLogString(message: String, throwable: Throwable? = null): String {
    val threadName = Thread.currentThread().name

    // The logger swallows `UnknownHostException` exception, so we can't use `Log.x(String, String, Throwable)`
    val throwableStackTrace = throwable?.let {
        val throwableMessage = it.message
        val stacktraceString = it.stackTrace?.joinToString("\n")
        "\n$throwableMessage\n$stacktraceString"
    }

    return "[$threadName]: $message$throwableStackTrace"
}