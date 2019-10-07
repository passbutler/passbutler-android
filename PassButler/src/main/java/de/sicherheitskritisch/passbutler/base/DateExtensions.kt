package de.sicherheitskritisch.passbutler.base

import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.*

val Date.formattedDateTime: String
    get() {
        val locale = Locale.getDefault()
        val dateTimeFormatPattern = DateFormat.getBestDateTimePattern(locale, "MM/dd/yyyy HH:mm:ss")
        val formatter = SimpleDateFormat(dateTimeFormatPattern, locale)
        return formatter.format(this)
    }