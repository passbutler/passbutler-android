package de.sicherheitskritisch.passbutler.ui

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import de.sicherheitskritisch.passbutler.MainActivity
import de.sicherheitskritisch.passbutler.RootFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

open class BaseFragment : Fragment(), FragmentPresenting, MainActivity.OnBackPressedListener, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + coroutineJob

    private val coroutineJob = SupervisorJob()

    var fragmentPresentingDelegate: FragmentPresentingDelegate? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (activity as? MainActivity)?.addOnBackPressedListener(this)
    }

    override fun onDetach() {
        (activity as? MainActivity)?.removeOnBackPressedListener(this)
        super.onDetach()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState != null) {
            if (this !is RootFragment) {
                // Restore fragment presenting delegate from `RootFragment` on configuration changes
                (activity as? MainActivity)?.rootFragment?.fragmentPresentingDelegate?.let {
                    fragmentPresentingDelegate = it
                }
            }

            // Re-apply fragment transition after configuration change
            FragmentPresentingDelegate.applyTransitionToAnimatedFragment(this)
        }
    }

    override fun onDestroy() {
        coroutineJob.cancel()
        super.onDestroy()
    }

    override fun showFragment(fragment: Fragment, replaceFragment: Boolean, addToBackstack: Boolean, debounce: Boolean, animated: Boolean) {
        fragmentPresentingDelegate?.showFragment(fragment, replaceFragment, addToBackstack, debounce, animated)
    }

    override fun <T : Fragment> isFragmentShown(fragmentClass: Class<T>): Boolean {
        return fragmentPresentingDelegate?.isFragmentShown(fragmentClass) ?: false
    }

    override fun popBackstack() {
        fragmentPresentingDelegate?.popBackstack()
    }

    override fun backstackCount(): Int {
        return fragmentPresentingDelegate?.backstackCount() ?: 0
    }

    override fun showProgress() {
        fragmentPresentingDelegate?.showProgress()
    }

    override fun hideProgress() {
        fragmentPresentingDelegate?.hideProgress()
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