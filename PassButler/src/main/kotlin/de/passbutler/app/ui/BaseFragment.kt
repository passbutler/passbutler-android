package de.passbutler.app.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import de.passbutler.app.AbstractRootFragment
import de.passbutler.app.MainActivity
import de.passbutler.common.ui.TransitionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

open class BaseFragment : Fragment(), UIPresenting, MainActivity.OnBackPressedListener, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + coroutineJob

    private val coroutineJob = SupervisorJob()

    var transitionType = TransitionType.NONE
    var uiPresentingDelegate: UIPresenting? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (activity as? MainActivity)?.addOnBackPressedListener(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState != null) {
            if (this !is AbstractRootFragment) {
                // Restore fragment presenting delegate from `RootFragment` on configuration changes
                (activity as? MainActivity)?.rootFragment?.uiPresentingDelegate?.let {
                    uiPresentingDelegate = it
                }
            }

            // Re-apply fragment transition after configuration change
            applyTransitionToFragment(this)
        }
    }

    override fun onDestroy() {
        coroutineJob.cancel()
        super.onDestroy()
    }

    override fun onDetach() {
        (activity as? MainActivity)?.removeOnBackPressedListener(this)
        super.onDetach()
    }

    override fun showFragment(fragment: Fragment, replaceFragment: Boolean, addToBackstack: Boolean, userTriggered: Boolean, transitionType: TransitionType) {
        uiPresentingDelegate?.showFragment(fragment, replaceFragment, addToBackstack, userTriggered, transitionType)
    }

    override fun <T : Fragment> isFragmentShown(fragmentClass: Class<T>): Boolean {
        return uiPresentingDelegate?.isFragmentShown(fragmentClass) ?: false
    }

    override fun popBackstack() {
        uiPresentingDelegate?.popBackstack()
    }

    override fun backstackCount(): Int {
        return uiPresentingDelegate?.backstackCount() ?: 0
    }

    override fun showProgress() {
        uiPresentingDelegate?.showProgress()
    }

    override fun hideProgress() {
        uiPresentingDelegate?.hideProgress()
    }

    override fun showInformation(message: String) {
        uiPresentingDelegate?.showInformation(message)
    }

    override fun showError(message: String) {
        uiPresentingDelegate?.showError(message)
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
