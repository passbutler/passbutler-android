package de.passbutler.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import de.passbutler.common.ui.FADE_TRANSITION_DURATION

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

fun Context.copyToClipboard(value: String) {
    // The value seems barely used, so ignore it for now
    val description = ""

    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(description, value)
    clipboard.setPrimaryClip(clipData)
}

/**
 * Apply given resolved color value to drawable.
 */
fun Drawable.applyTint(color: Int) {
    colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
}

/**
 * Animates view with fade-in and fade-out.
 */
fun View.showFadeInOutAnimation(shouldShow: Boolean, visibilityHideMode: VisibilityHideMode = VisibilityHideMode.GONE) {
    val startAlpha = if (shouldShow) 0.0f else 1.0f
    val endAlpha = if (shouldShow) 1.0f else 0.0f

    val viewPropertyAnimator = animate()

    viewPropertyAnimator
        .setDuration(FADE_TRANSITION_DURATION.toMillis())
        .alpha(endAlpha)
        .setListener(animatorListenerAdapter(
            onAnimationStart = {
                // If view should be shown, set values always on start to be sure also on animation cancel, the values are re-applied
                if (shouldShow) {
                    alpha = startAlpha
                    visibility = View.VISIBLE
                }
            },
            onAnimationEnd = {
                // Hide view according to desired hide mode if necessary
                if (!shouldShow) {
                    visibility = visibilityHideMode.viewConstant
                }

                // Remove this listener to avoid it is called if view is animated from other location (the `ViewPropertyAnimator` stays the same)
                viewPropertyAnimator.setListener(null)
            }
        ))
}

enum class VisibilityHideMode {
    INVISIBLE,
    GONE;

    val viewConstant: Int
        get() = when (this) {
            INVISIBLE -> View.INVISIBLE
            GONE -> View.GONE
        }
}

inline fun animatorListenerAdapter(crossinline onAnimationEnd: () -> Unit, crossinline onAnimationStart: () -> Unit): AnimatorListenerAdapter {
    return object : AnimatorListenerAdapter() {
        override fun onAnimationStart(animation: Animator) {
            onAnimationStart.invoke()
        }

        override fun onAnimationEnd(animation: Animator) {
            onAnimationEnd.invoke()
        }
    }
}