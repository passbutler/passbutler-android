package de.sicherheitskritisch.pass.ui

import android.support.v4.app.Fragment

open class BaseFragment : Fragment(), FragmentPresenting {

    var fragmentPresentingHelper: FragmentPresentingHelper? = null

    override fun showFragment(fragment: Fragment, replaceFragment: Boolean, addToBackstack: Boolean) {
        fragmentPresentingHelper?.showFragment(fragment, replaceFragment, addToBackstack)
    }

    override fun popBackstack() {
        fragmentPresentingHelper?.popBackstack()
    }
}