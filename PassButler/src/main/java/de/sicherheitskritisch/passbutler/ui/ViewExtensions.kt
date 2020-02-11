package de.sicherheitskritisch.passbutler.ui

import android.content.Context
import android.util.TypedValue
import android.view.View

var View.visible: Boolean
    get() = visibility == View.VISIBLE
    set(value) {
        visibility = if (value) View.VISIBLE else View.GONE
    }

fun Context.resolveThemeAttribute(attribute: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attribute, typedValue, true)

    return typedValue.data
}