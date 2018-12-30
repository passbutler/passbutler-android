package de.sicherheitskritisch.pass.common

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View

const val FADE_ANIMATION_DURATION_MILLISECONDS = 350L

fun View.showFadeAnimation(shouldShow: Boolean) {
    val shouldShowVisibilityEquivalent = if (shouldShow) View.VISIBLE else View.GONE

    // Only animate if necessary
    if (visibility != shouldShowVisibilityEquivalent) {
        val startAlpha = if (shouldShow) 0.0f else 1.0f
        val endAlpha = if (shouldShow) 1.0f else 0.0f

        // Set initial viability always to visible, but alpha dependent of state
        alpha = startAlpha
        visibility = View.VISIBLE

        animate()
            .setDuration(FADE_ANIMATION_DURATION_MILLISECONDS)
            .alpha(endAlpha)
            .setListener(animatorListenerAdapter(onAnimationEnd = {
                // Only set viability to gone if necessary
                if (!shouldShow) {
                    visibility = View.GONE
                }
            }))
    }
}

fun animatorListenerAdapter(onAnimationEnd: (() -> Unit)? = null, onAnimationStart: (() -> Unit)? = null): AnimatorListenerAdapter {
    return object : AnimatorListenerAdapter() {
        override fun onAnimationStart(animation: Animator) {
            onAnimationStart?.invoke()
        }

        override fun onAnimationEnd(animation: Animator) {
            onAnimationEnd?.invoke()
        }
    }
}