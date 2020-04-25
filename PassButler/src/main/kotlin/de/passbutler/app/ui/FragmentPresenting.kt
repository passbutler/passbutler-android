package de.passbutler.app.ui

import androidx.fragment.app.Fragment

interface FragmentPresenting {
    fun showFragment(fragment: Fragment, replaceFragment: Boolean = false, addToBackstack: Boolean = true, debounce: Boolean = true, transitionType: TransitionType = TransitionType.SLIDE)

    fun <T : Fragment> isFragmentShown(fragmentClass: Class<T>): Boolean

    fun popBackstack()
    fun backstackCount(): Int

    fun showProgress()
    fun hideProgress()
}

fun FragmentPresenting.showFragmentAsFirstScreen(fragment: Fragment, transitionType: TransitionType = TransitionType.SLIDE) {
    showFragment(fragment, replaceFragment = true, addToBackstack = false, transitionType = transitionType)
}

fun FragmentPresenting.showFragmentModally(fragment: Fragment) {
    showFragment(fragment, transitionType = TransitionType.MODAL)
}

enum class TransitionType {
    MODAL,
    SLIDE,
    FADE,
    NONE
}