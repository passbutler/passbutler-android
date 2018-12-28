package de.sicherheitskritisch.pass.ui

import android.content.Context
import android.support.v4.app.Fragment
import de.sicherheitskritisch.pass.MainActivity

open class BaseFragment : Fragment(), FragmentPresenting, MainActivity.OnBackPressedListener {

    var fragmentPresentingDelegate: FragmentPresentingDelegate? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        (activity as? MainActivity)?.addOnBackPressedListener(this)
    }

    override fun onDetach() {
        (activity as? MainActivity)?.removeOnBackPressedListener(this)
        super.onDetach()
    }

    override fun showFragment(fragment: Fragment, replaceFragment: Boolean, addToBackstack: Boolean) {
        fragmentPresentingDelegate?.showFragment(fragment, replaceFragment, addToBackstack)
    }

    override fun popBackstack() {
        fragmentPresentingDelegate?.popBackstack()
    }

    /**
     * Override the method to execute custom functionality on backpress action.
     * Return `true` if the fragment handled the action, `false` otherwise.
     */
    override fun onHandleBackPress(): Boolean {
        return false
    }
}