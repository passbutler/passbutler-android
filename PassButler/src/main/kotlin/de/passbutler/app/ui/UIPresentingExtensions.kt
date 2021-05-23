package de.passbutler.app.ui

import android.app.Activity
import android.view.Gravity
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.Transition
import androidx.transition.TransitionSet
import de.passbutler.common.ui.FADE_TRANSITION_DURATION
import de.passbutler.common.ui.SLIDE_TRANSITION_DURATION
import de.passbutler.common.ui.TransitionType

interface ScreenPresentingExtensions {
    fun applyTransitionToFragment(fragment: BaseFragment) {
        fragment.transitionType.createFragmentTransition()?.let {
            fragment.enterTransition = it.enterTransition
            fragment.exitTransition = it.exitTransition
        }
    }

    private fun TransitionType.createFragmentTransition(): FragmentTransition? {
        return when (this) {
            TransitionType.MODAL -> FragmentTransition(enterTransition = createVerticalSlideInTransition(), exitTransition = createVerticalSlideOutTransition())
            TransitionType.SLIDE -> FragmentTransition(enterTransition = createHorizontalSlideInTransition(), exitTransition = createHorizontalSlideOutTransition())
            TransitionType.FADE -> FragmentTransition(enterTransition = createFadeInOutTransition(), exitTransition = createFadeInOutTransition())
            TransitionType.NONE -> null
        }
    }

    private fun createHorizontalSlideInTransition(): Transition {
        return createTransitionSetWithDefaultInterpolator().apply {
            addTransition(Slide(Gravity.END))
            duration = SLIDE_TRANSITION_DURATION.toMillis()
        }
    }

    private fun createHorizontalSlideOutTransition(): Transition {
        return createTransitionSetWithDefaultInterpolator().apply {
            addTransition(Slide(Gravity.START))
            duration = SLIDE_TRANSITION_DURATION.toMillis()
        }
    }

    private fun createVerticalSlideInTransition(): Transition {
        return createTransitionSetWithDefaultInterpolator().apply {
            addTransition(Slide(Gravity.BOTTOM))
            duration = SLIDE_TRANSITION_DURATION.toMillis()
        }
    }

    private fun createVerticalSlideOutTransition(): Transition {
        return createTransitionSetWithDefaultInterpolator().apply {
            addTransition(Slide(Gravity.TOP))
            duration = SLIDE_TRANSITION_DURATION.toMillis()
        }
    }

    private fun createFadeInOutTransition(): Transition {
        return createTransitionSetWithDefaultInterpolator().apply {
            addTransition(Fade(Fade.IN))
            addTransition(Fade(Fade.OUT))
            duration = FADE_TRANSITION_DURATION.toMillis()
        }
    }

    private fun createTransitionSetWithDefaultInterpolator(): TransitionSet {
        return TransitionSet().apply {
            interpolator = FastOutSlowInInterpolator()
        }
    }

    data class FragmentTransition(val enterTransition: Transition, val exitTransition: Transition)

    companion object {
        val Fragment.instanceIdentification: String
            get() = javaClass.instanceIdentification

        val <T : Fragment> Class<T>.instanceIdentification: String
            get() {
                // Use class name with full package
                return name
            }

        val Activity?.isNotFinished
            get() = this?.isFinishing != true

        val Fragment?.isStateNotSaved
            get() = this?.isStateSaved != true
    }
}

fun ScreenPresenting.showFragmentAsFirstScreen(fragment: Fragment, userTriggered: Boolean = true, transitionType: TransitionType = TransitionType.SLIDE) {
    showFragment(fragment, replaceFragment = true, addToBackstack = false, userTriggered = userTriggered, transitionType = transitionType)
}

fun ScreenPresenting.showFragmentModally(fragment: Fragment) {
    showFragment(fragment, transitionType = TransitionType.MODAL)
}
