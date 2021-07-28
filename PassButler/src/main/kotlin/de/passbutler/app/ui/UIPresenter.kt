package de.passbutler.app.ui

import android.app.Activity
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import de.passbutler.app.AbstractRootFragment
import de.passbutler.app.ui.ScreenPresentingExtensions.Companion.instanceIdentification
import de.passbutler.app.ui.ScreenPresentingExtensions.Companion.isNotFinished
import de.passbutler.app.ui.ScreenPresentingExtensions.Companion.isStateNotSaved
import de.passbutler.common.ui.BannerPresenting
import de.passbutler.common.ui.DebouncedUIPresenting
import de.passbutler.common.ui.ProgressPresenting
import de.passbutler.common.ui.TransitionType
import org.tinylog.kotlin.Logger
import java.time.Instant

/**
 * Provides implementation of fragment management used by the one-activity app concept.
 * This delegate is delegated in `BaseFragment` and provided in the `AbstractRootFragment`.
 */
class UIPresenter(
    activity: Activity,
    rootFragment: AbstractRootFragment,
    rootFragmentScreenContainerResourceId: Int,
    rootFragmentProgressScreenViewResourceId: Int
) : UIPresenting,
    ScreenPresenting by ScreenPresenter(activity, rootFragment, rootFragmentScreenContainerResourceId),
    ProgressPresenting by ProgressPresenter(rootFragment, rootFragmentProgressScreenViewResourceId),
    BannerPresenting by BannerPresenter(rootFragment)

class ScreenPresenter(
    private val activity: Activity,
    private val rootFragment: AbstractRootFragment,
    private val rootFragmentScreenContainerResourceId: Int
) : ScreenPresenting, DebouncedUIPresenting {

    override var lastViewTransactionTime: Instant? = null

    private val rootFragmentManager
        get() = rootFragment.childFragmentManager

    override fun showFragment(fragment: Fragment, replaceFragment: Boolean, addToBackstack: Boolean, userTriggered: Boolean, transitionType: TransitionType) {
        val debouncedViewTransactionEnsured = ensureDebouncedViewTransaction().takeIf { userTriggered } ?: true

        if (activity.isNotFinished) {
            if (debouncedViewTransactionEnsured) {
                rootFragmentManager.beginTransaction().let { fragmentTransaction ->
                    if (fragment is BaseFragment) {
                        fragment.transitionType = transitionType
                        fragmentTransaction.applyTransition(transitionType)

                        fragment.uiPresentingDelegate = rootFragment.uiPresentingDelegate
                    }

                    val fragmentTag = fragment.instanceIdentification

                    if (replaceFragment) {
                        fragmentTransaction.replace(rootFragmentScreenContainerResourceId, fragment, fragmentTag)
                    } else {
                        fragmentTransaction.add(rootFragmentScreenContainerResourceId, fragment, fragmentTag)
                    }

                    if (addToBackstack) {
                        fragmentTransaction.addToBackStack(fragmentTag)
                    }

                    if (rootFragment.isStateNotSaved) {
                        fragmentTransaction.commit()
                    } else {
                        Logger.warn("The fragment transaction was done after state of root fragment was saved")
                        fragmentTransaction.commitAllowingStateLoss()
                    }
                }
            } else {
                Logger.warn("The view transaction was ignored because a recent transaction was already done!")
            }
        }
    }

    override fun <T : Fragment> isFragmentShown(fragmentClass: Class<T>): Boolean {
        return if (activity.isNotFinished && rootFragment.isStateNotSaved) {
            val fragmentTag = fragmentClass.instanceIdentification
            rootFragmentManager.findFragmentByTag(fragmentTag) != null
        } else {
            false
        }
    }

    override fun popBackstack() {
        if (activity.isNotFinished && rootFragment.isStateNotSaved) {
            rootFragmentManager.popBackStack()
        }
    }

    override fun backstackCount(): Int {
        return rootFragmentManager.backStackEntryCount
    }
}

class ProgressPresenter(
    private val rootFragment: AbstractRootFragment,
    private val rootFragmentProgressScreenViewResourceId: Int
) : ProgressPresenting {

    private val progressScreenView
        get() = rootFragment.view?.findViewById<View>(rootFragmentProgressScreenViewResourceId)

    override fun showProgress() {
        progressScreenView?.showFadeInOutAnimation(true)
    }

    override fun hideProgress() {
        progressScreenView?.showFadeInOutAnimation(false)
    }
}

class BannerPresenter(
    private val rootFragment: AbstractRootFragment
) : BannerPresenting {

    override fun showInformation(message: String) {
        rootFragment.view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun showError(message: String) {
        // Same as information at the moment
        showInformation(message)
    }
}
