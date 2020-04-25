package de.passbutler.app.ui

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable

/**
 * Apply given resolved color value to drawable.
 */
fun Drawable.applyTint(color: Int) {
    colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
}