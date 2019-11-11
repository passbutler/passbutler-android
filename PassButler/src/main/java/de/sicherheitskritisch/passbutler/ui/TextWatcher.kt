package de.sicherheitskritisch.passbutler.ui

import android.text.Editable
import android.text.TextWatcher

open class SimpleTextWatcher : TextWatcher {
    override fun afterTextChanged(s: Editable?) {
        // Implement if needed
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        // Implement if needed
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        // Implement if needed
    }
}

fun simpleTextWatcher(callback: (String?) -> Unit): SimpleTextWatcher {
    return object : SimpleTextWatcher() {
        override fun afterTextChanged(s: Editable?) {
            callback(s?.toString())
        }
    }
}