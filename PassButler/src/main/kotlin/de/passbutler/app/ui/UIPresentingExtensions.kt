package de.passbutler.app.ui

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import de.passbutler.app.R
import de.passbutler.common.ui.TransitionType

interface ScreenPresentingExtensions {
    fun FragmentTransaction.applyTransition(transitionType: TransitionType) {
        when (transitionType) {
            TransitionType.MODAL -> setCustomAnimations(R.anim.slide_in_bottom, R.anim.slide_out_top, R.anim.slide_in_top, R.anim.slide_out_bottom)
            TransitionType.SLIDE -> setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right)
            TransitionType.FADE -> setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
            TransitionType.NONE -> {
                // Nothing
            }
        }
    }

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
