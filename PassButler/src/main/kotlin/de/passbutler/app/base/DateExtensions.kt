package de.passbutler.app.base

import android.content.Context
import android.text.format.DateUtils
import java.time.Instant

fun Instant.relativeDateTime(context: Context): String {
    return DateUtils.getRelativeDateTimeString(context, toEpochMilli(), DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString()
}