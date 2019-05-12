package de.sicherheitskritisch.passbutler.ui

import android.support.v4.app.Fragment

interface FragmentPresenting {
    fun showFragment(fragment: Fragment, replaceFragment: Boolean = false, addToBackstack: Boolean = true, debounce: Boolean = true, animated: Boolean = true)

    fun <T : Fragment> isFragmentShown(fragmentClass: Class<T>): Boolean

    fun popBackstack()
    fun backstackCount(): Int

    fun showProgress()
    fun hideProgress()
}

fun FragmentPresenting.showFragmentAsFirstScreen(fragment: Fragment, animated: Boolean = true) {
    showFragment(fragment, replaceFragment = true, addToBackstack = false, animated = animated)
}