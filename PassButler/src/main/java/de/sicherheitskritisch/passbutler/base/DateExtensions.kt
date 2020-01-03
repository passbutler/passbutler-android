package de.sicherheitskritisch.passbutler.base

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.*

val Date.formattedDateTime: String
    get() {
        val locale = Locale.getDefault()
        val dateTimeFormatPattern = DateFormat.getBestDateTimePattern(locale, "MM/dd/yyyy HH:mm:ss")
        val formatter = SimpleDateFormat(dateTimeFormatPattern, locale)
        return formatter.format(this)
    }

fun Date.relativeDateTime(context: Context): String {
    return DateUtils.getRelativeDateTimeString(context, time, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString()
}