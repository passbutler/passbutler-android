package de.passbutler.app.base

import android.content.Context
import de.passbutler.app.R
import de.passbutler.common.base.RelativeDateFormattingTranslations
import de.passbutler.common.base.UnitTranslation
import de.passbutler.common.base.UnitTranslations

fun createRelativeDateFormattingTranslations(context: Context): RelativeDateFormattingTranslations {
    return RelativeDateFormattingTranslations(
        unitTranslations = UnitTranslations(
            second = UnitTranslation(context.getString(R.string.general_relative_date_unit_second_one), context.getString(R.string.general_relative_date_unit_second_other)),
            minute = UnitTranslation(context.getString(R.string.general_relative_date_unit_minute_one), context.getString(R.string.general_relative_date_unit_minute_other)),
            hour = UnitTranslation(context.getString(R.string.general_relative_date_unit_hour_one), context.getString(R.string.general_relative_date_unit_hour_other)),
            day = UnitTranslation(context.getString(R.string.general_relative_date_unit_day_one), context.getString(R.string.general_relative_date_unit_day_other)),
            month = UnitTranslation(context.getString(R.string.general_relative_date_unit_month_one), context.getString(R.string.general_relative_date_unit_month_other)),
            year = UnitTranslation(context.getString(R.string.general_relative_date_unit_year_one), context.getString(R.string.general_relative_date_unit_year_other))
        ),
        sinceString = context.getString(R.string.general_relative_date_since)
    )
}
