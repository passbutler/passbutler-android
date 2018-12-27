package de.sicherheitskritisch.pass.ui

import android.support.v4.app.Fragment

interface FragmentPresenting {
    fun showFragment(fragment: Fragment, replaceFragment: Boolean = false, addToBackstack: Boolean = true)
    fun popBackstack()
}