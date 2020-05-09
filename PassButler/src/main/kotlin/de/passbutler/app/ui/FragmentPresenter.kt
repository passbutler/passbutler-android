package de.passbutler.app.ui

import android.app.Activity
import android.view.Gravity
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.Transition
import androidx.transition.TransitionSet
import de.passbutler.app.RootFragment
import org.tinylog.kotlin.Logger
import java.lang.ref.WeakReference

/**
 * Provides implementation of fragment management used by the one-activity app concept.
 * This delegate is delegated in `BaseFragment` and provided in the root fragment.
 */
class FragmentPresenter(
    private val activityWeakReference: WeakReference<Activity>,
    private val rootFragmentWeakReference: WeakReference<RootFragment>,
    private val rootFragmentContainerResourceId: Int,
    private val rootFragmentProgressScreenViewResourceId: Int
) : FragmentPresenting {

    private val activity
        get() = activityWeakReference.get()

    /**
     * The root fragment actually is used to contain the shown fragments
     */
    private val rootFragment
        get() = rootFragmentWeakReference.get()

    /**
     * The root fragment manager is used to provide fragment handling for the one-activity app concept
     */
    private val rootFragmentManager
        get() = rootFragment?.childFragmentManager

    /**
     * The progress screen view is contained in the root fragment and used for progress indication
     */
    private val rootFragmentProgressScreenView
        get() = rootFragment?.view?.findViewById<FrameLayout>(rootFragmentProgressScreenViewResourceId)

    private var lastShowFragmentTransactionTimestamp: Long = 0

    override fun showFragment(fragment: Fragment, replaceFragment: Boolean, addToBackstack: Boolean, debounce: Boolean, transitionType: TransitionType) {
        val noRecentTransactionWasDone = checkNoRecentShowFragmentTransactionWasDone().takeIf { debounce } ?: true

        if (activity.isNotFinished && noRecentTransactionWasDone) {
            rootFragmentManager?.beginTransaction()?.let { fragmentTransaction ->

                if (fragment is BaseFragment) {
                    fragment.transitionType = transitionType
                    applyTransitionToFragment(fragment)

                    fragment.fragmentPresentingDelegate = this
                }

                val fragmentTag = getFragmentTag(fragment)

                if (replaceFragment) {
                    fragmentTransaction.replace(rootFragmentContainerResourceId, fragment, fragmentTag)
                } else {
                    fragmentTransaction.add(rootFragmentContainerResourceId, fragment, fragmentTag)
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
        }
    }

    private fun checkNoRecentShowFragmentTransactionWasDone(): Boolean {
        val currentTimestamp = System.currentTimeMillis()
        val lastShowFragmentTransactionTimestampDelta = currentTimestamp - lastShowFragmentTransactionTimestamp
        val noRecentShowFragmentTransactionWasDone = lastShowFragmentTransactionTimestampDelta > SHOW_FRAGMENT_DEBOUNCE_TIME_MILLISECONDS

        // If no recent show fragment transaction was done, set current timestamp
        if (noRecentShowFragmentTransactionWasDone) {
            lastShowFragmentTransactionTimestamp = currentTimestamp
        }

        return noRecentShowFragmentTransactionWasDone
    }

    override fun <T : Fragment> isFragmentShown(fragmentClass: Class<T>): Boolean {
        return if (activity.isNotFinished && rootFragment.isStateNotSaved) {
            val fragmentTag = getFragmentTag(fragmentClass)
            rootFragmentManager?.findFragmentByTag(fragmentTag) != null
        } else {
            false
        }
    }

    override fun popBackstack() {
        if (activity.isNotFinished && rootFragment.isStateNotSaved) {
            rootFragmentManager?.popBackStack()
        }
    }

    override fun backstackCount(): Int {
        return rootFragmentManager?.backStackEntryCount ?: 0
    }

    override fun showProgress() {
        rootFragmentProgressScreenView?.showFadeInOutAnimation(true)
    }

    override fun hideProgress() {
        rootFragmentProgressScreenView?.showFadeInOutAnimation(false)
    }

    companion object {
        private const val SHOW_FRAGMENT_DEBOUNCE_TIME_MILLISECONDS = 450L

        fun getFragmentTag(fragment: Fragment): String {
            return fragment.javaClass.fragmentInstanceIdentification
        }

        fun <T : Fragment> getFragmentTag(fragmentClass: Class<T>): String {
            return fragmentClass.fragmentInstanceIdentification
        }

        fun applyTransitionToFragment(fragment: BaseFragment) {
            when (fragment.transitionType) {
                TransitionType.SLIDE -> {
                    fragment.enterTransition = createHorizontalSlideInTransition()
                    fragment.exitTransition = createHorizontalSlideOutTransition()
                }
                TransitionType.MODAL -> {
                    fragment.enterTransition = createVerticalSlideInTransition()
                    fragment.exitTransition = createVerticalSlideOutTransition()
                }
                TransitionType.FADE -> {
                    fragment.enterTransition = createFadeInOutTransition()
                    fragment.exitTransition = createFadeInOutTransition()
                }
                TransitionType.NONE -> {
                    // No transition
                }
            }
        }

        private fun createHorizontalSlideInTransition(): Transition {
            val transitionSet = createTransitionSetWithDefaultInterpolator()
            transitionSet.addTransition(Slide(Gravity.END))
            return transitionSet
        }

        private fun createHorizontalSlideOutTransition(): Transition {
            val transitionSet = createTransitionSetWithDefaultInterpolator()
            transitionSet.addTransition(Slide(Gravity.START))
            return transitionSet
        }

        private fun createVerticalSlideInTransition(): Transition {
            val transitionSet = createTransitionSetWithDefaultInterpolator()
            transitionSet.addTransition(Slide(Gravity.BOTTOM))
            return transitionSet
        }

        private fun createVerticalSlideOutTransition(): Transition {
            val transitionSet = createTransitionSetWithDefaultInterpolator()
            transitionSet.addTransition(Slide(Gravity.TOP))
            return transitionSet
        }

        private fun createFadeInOutTransition(): Transition {
            val transitionSet = createTransitionSetWithDefaultInterpolator()
            transitionSet.addTransition(Fade(Fade.IN))
            transitionSet.addTransition(Fade(Fade.OUT))
            return transitionSet
        }

        private fun createTransitionSetWithDefaultInterpolator(): TransitionSet {
            val transitionSet = TransitionSet()
            transitionSet.interpolator = FastOutSlowInInterpolator()
            return transitionSet
        }
    }
}

internal val Class<*>.fragmentInstanceIdentification: String
    get() {
        // Use class name with full package
        return name
    }

internal val Activity?.isNotFinished
    get() = this?.isFinishing != true

internal val Fragment?.isStateNotSaved
    get() = this?.isStateSaved != true