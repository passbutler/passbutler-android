package de.passbutler.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View

private const val FADE_ANIMATION_DURATION_MILLISECONDS = 350L

/**
 * Animates view with fade-in and fade-out.
 */
fun View.showFadeInOutAnimation(shouldShow: Boolean, visibilityHideMode: VisibilityHideMode = VisibilityHideMode.GONE) {
    val startAlpha = if (shouldShow) 0.0f else 1.0f
    val endAlpha = if (shouldShow) 1.0f else 0.0f

    animate()
        .setDuration(FADE_ANIMATION_DURATION_MILLISECONDS)
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