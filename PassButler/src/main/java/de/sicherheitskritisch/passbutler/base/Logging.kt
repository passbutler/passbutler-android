package de.sicherheitskritisch.passbutler.base

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

typealias L = Logger

object Logger {
    fun v(tag: String, message: String) = Log.v(tag, buildLogString(message))
    fun d(tag: String, message: String) = Log.d(tag, buildLogString(message))
    fun i(tag: String, message: String) = Log.i(tag, buildLogString(message))
    fun w(tag: String, message: String) = Log.w(tag, buildLogString(message))
    fun w(tag: String, message: String, throwable: Throwable) = Log.w(tag, buildLogString(message, throwable))
    fun e(tag: String, message: String) = Log.e(tag, buildLogString(message))
}

private fun buildLogString(message: String, throwable: Throwable? = null): String {
    val threadName = Thread.currentThread().name

    // The logger swallows `UnknownHostException` exception, so we can't use `Log.x(String, String, Throwable)`
    val throwableStackTrace = throwable?.let {
        val stringWriter = StringWriter()
        it.printStackTrace(PrintWriter(stringWriter))

        stringWriter.toString()
    } ?: ""

    return "[$threadName]: $message\n$throwableStackTrace"
}