package de.passbutler.app.base

import android.content.Context
import android.text.format.DateUtils
import de.passbutler.common.base.formattedDateTime
import java.util.*

// TODO: Migrate to `Instant` usage
val Date.formattedDateTime: String
    get() = toInstant().formattedDateTime

fun Date.relativeDateTime(context: Context): String {
    return DateUtils.getRelativeDateTimeString(context, time, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString()
}