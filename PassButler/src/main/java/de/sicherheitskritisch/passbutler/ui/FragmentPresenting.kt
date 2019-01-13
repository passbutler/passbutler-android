package de.sicherheitskritisch.passbutler.ui

import android.support.v4.app.Fragment

interface FragmentPresenting {
    fun showFragment(fragment: Fragment, replaceFragment: Boolean = false, addToBackstack: Boolean = true)
    fun showFragmentAsFirstScreen(fragment: Fragment)

    fun <T : Fragment> isFragmentShown(fragmentClass: Class<T>): Boolean

    fun popBackstack()
    fun backstackCount(): Int
}