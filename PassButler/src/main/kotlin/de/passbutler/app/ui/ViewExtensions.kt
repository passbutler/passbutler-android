package de.passbutler.app.ui

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes

var View.visible: Boolean
    get() = visibility == View.VISIBLE
    set(value) {
        visibility = if (value) View.VISIBLE else View.GONE
    }

/**
 * Returns resolved theme attribute value (e.g. `ColorInt`)
 */
fun Context.resolveThemeAttributeData(@AttrRes attribute: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attribute, typedValue, true)

    return typedValue.data
}

/**
 * Returns resolved theme attribute id (e.g. `ColorRes`)
 */
fun Context.resolveThemeAttributeId(@AttrRes attribute: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attribute, typedValue, true)

    return typedValue.resourceId
}

/**
 * Apply given resolved color value to drawable.
 */
fun Drawable.applyTint(color: Int) {
    colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
}