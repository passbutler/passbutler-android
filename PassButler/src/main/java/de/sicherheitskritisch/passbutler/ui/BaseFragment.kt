package de.sicherheitskritisch.passbutler.ui

import android.content.Context
import android.support.v4.app.Fragment
import de.sicherheitskritisch.passbutler.MainActivity

open class BaseFragment : Fragment(), FragmentPresenting, MainActivity.OnBackPressedListener {

    lateinit var fragmentPresentingDelegate: FragmentPresentingDelegate

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        (activity as? MainActivity)?.addOnBackPressedListener(this)
    }

    override fun onDetach() {
        (activity as? MainActivity)?.removeOnBackPressedListener(this)
        super.onDetach()
    }

    override fun showFragment(fragment: Fragment, replaceFragment: Boolean, addToBackstack: Boolean) {
        fragmentPresentingDelegate.showFragment(fragment, replaceFragment, addToBackstack)
    }

    override fun showFragmentAsFirstScreen(fragment: Fragment) {
        fragmentPresentingDelegate.showFragmentAsFirstScreen(fragment)
    }

    override fun <T : Fragment> isFragmentShown(fragmentClass: Class<T>): Boolean {
        return fragmentPresentingDelegate.isFragmentShown(fragmentClass)
    }

    override fun popBackstack() {
        fragmentPresentingDelegate.popBackstack()
    }

    override fun backstackCount(): Int {
        return fragmentPresentingDelegate.backstackCount()
    }

    /**
     * Override the method to execute custom functionality on backpress action.
     * Return `true` if the fragment handled the action, `false` otherwise.
     */
    override fun onHandleBackPress(): Boolean {
        // Only if more than one fragment is on the backstack, handle action and pop backstack
        return if (backstackCount() > 0) {
            popBackstack()
            true
        } else {
            false
        }
    }
}