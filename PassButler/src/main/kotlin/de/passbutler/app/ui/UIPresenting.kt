package de.passbutler.app.ui

import androidx.fragment.app.Fragment
import de.passbutler.common.ui.BannerPresenting
import de.passbutler.common.ui.ProgressPresenting
import de.passbutler.common.ui.TransitionType

interface UIPresenting : ProgressPresenting, BannerPresenting {
    fun showFragment(fragment: Fragment, replaceFragment: Boolean = false, addToBackstack: Boolean = true, userTriggered: Boolean = true, transitionType: TransitionType = TransitionType.SLIDE)

    fun <T : Fragment> isFragmentShown(fragmentClass: Class<T>): Boolean

    fun popBackstack()
    fun backstackCount(): Int
}

fun UIPresenting.showFragmentAsFirstScreen(fragment: Fragment, transitionType: TransitionType = TransitionType.SLIDE) {
    showFragment(fragment, replaceFragment = true, addToBackstack = false, transitionType = transitionType)
}

fun UIPresenting.showFragmentModally(fragment: Fragment) {
    showFragment(fragment, transitionType = TransitionType.MODAL)
}