package de.sicherheitskritisch.pass.ui

import android.support.v4.app.Fragment

interface FragmentPresenting {
    fun showFragment(fragment: Fragment, replaceFragment: Boolean = false, addToBackstack: Boolean = true)
    fun showFragmentAsFirstScreen(fragment: Fragment)

    fun popBackstack()

    fun backstackCount(): Int
}