package de.sicherheitskritisch.passbutler.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View

private const val FADE_ANIMATION_DURATION_MILLISECONDS = 350L

/**
 * Animates view with fade-in only.
 */
fun View.showFadeInAnimation(shouldShow: Boolean, visibilityHideMode: VisibilityHideMode = VisibilityHideMode.GONE) {
    val shouldShowVisibilityEquivalent = if (shouldShow) View.VISIBLE else View.GONE

    // Only animate if necessary
    if (visibility != shouldShowVisibilityEquivalent) {
        if (shouldShow) {
            visibility = View.VISIBLE

            animate()
                .setDuration(FADE_ANIMATION_DURATION_MILLISECONDS)
                .alpha(1.0f)
        } else {
            visibility = visibilityHideMode.viewConstant
        }
    }
}

/**
 * Animates view with fade-in and fade-out.
 */
fun View.showFadeInOutAnimation(shouldShow: Boolean, visibilityHideMode: VisibilityHideMode = VisibilityHideMode.GONE) {
    val shouldShowVisibilityEquivalent = if (shouldShow) View.VISIBLE else View.GONE

    // Only animate if necessary
    if (visibility != shouldShowVisibilityEquivalent) {
        val startAlpha = if (shouldShow) 0.0f else 1.0f
        val endAlpha = if (shouldShow) 1.0f else 0.0f

        // Set initial visibility always to visible, but alpha dependent of state
        alpha = startAlpha
        visibility = View.VISIBLE

        animate()
            .setDuration(FADE_ANIMATION_DURATION_MILLISECONDS)
            .alpha(endAlpha)
            .setListener(animatorListenerAdapter(
                onAnimationStart = {
                    // Not needed
                },
                onAnimationEnd = {
                    // Only set viability to gone if necessary
                    if (!shouldShow) {
                        visibility = visibilityHideMode.viewConstant
                    }
                }
            ))
    }
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