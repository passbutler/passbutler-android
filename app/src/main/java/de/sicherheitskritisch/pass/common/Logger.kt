package de.sicherheitskritisch.pass.common

import android.util.Log

class Logger {
    companion object {
        fun d(tag: String, message: String) {
            val currentThread = Thread.currentThread().name
            Log.d(tag, "[$currentThread]: $message")
        }
    }
}

typealias L = Logger