package de.sicherheitskritisch.passbutler.common

import android.util.Log

typealias L = Logger

object Logger {
    fun v(tag: String, message: String) = Log.v(tag, buildLogString(message))
    fun d(tag: String, message: String) = Log.d(tag, buildLogString(message))
    fun i(tag: String, message: String) = Log.i(tag, buildLogString(message))
    fun w(tag: String, message: String) = Log.w(tag, buildLogString(message))
}

private fun buildLogString(message: String): String {
    val threadName = Thread.currentThread().name
    return "[$threadName]: $message"
}