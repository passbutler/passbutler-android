package de.sicherheitskritisch.passbutler.ui

import android.app.Activity
import android.support.transition.Fade
import android.support.transition.Slide
import android.support.transition.Transition
import android.support.transition.TransitionSet
import android.support.v4.app.Fragment
import android.support.v4.view.animation.FastOutSlowInInterpolator
import android.view.Gravity
import de.sicherheitskritisch.passbutler.RootFragment
import de.sicherheitskritisch.passbutler.common.L
import java.lang.ref.WeakReference

/**
 * Provides implementation of fragment management used by the one-activity app concept.
 * This delegate is delegated in `BaseFragment` and provided in the root fragment.
 */
class FragmentPresentingDelegate(
    private val activityWeakReference: WeakReference<Activity>,
    private val rootFragmentWeakReference: WeakReference<RootFragment>,
    private val rootFragmentContainerResourceId: Int
) : FragmentPresenting {

    private val activity
        get() = activityWeakReference.get()

    private val rootFragment
        get() = rootFragmentWeakReference.get()

    /**
     * The fragment manager is used to provide fragment handling for the one-activity app concept
     */
    private val rootFragmentManager
        get() = rootFragment?.childFragmentManager

    override fun showFragment(fragment: Fragment, replaceFragment: Boolean, addToBackstack: Boolean) {
        if (activity.isNotFinished) {
            rootFragmentManager?.beginTransaction()?.let { fragmentTransaction ->
                // Add fragment transitions to look transaction beautiful
                applyTransitionToAnimatedFragment(fragment)

                // TODO: debouncing

                if (fragment.isHidden) {
                    fragmentTransaction.show(fragment)
                } else {
                    if (fragment is BaseFragment) {
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
                }

                if (rootFragment.isStateNotSaved) {
                    fragmentTransaction.commit()
                } else {
                    L.w("FragmentPresentingDelegate", "showFragment(): The fragment transaction was done after state of root fragment was saved!")
                    fragmentTransaction.commitAllowingStateLoss()
                }
            }
        }
    }

    override fun showFragmentAsFirstScreen(fragment: Fragment) {
        showFragment(fragment, replaceFragment = true, addToBackstack = false)
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

    companion object {
        fun getFragmentTag(fragment: Fragment): String {
            return fragment.javaClass.canonicalName ?: fragment.javaClass.toString()
        }

        fun <T : Fragment> getFragmentTag(fragmentClass: Class<T>): String {
            return fragmentClass.canonicalName ?: fragmentClass.toString()
        }

        fun applyTransitionToAnimatedFragment(fragment: Fragment) {
            if (fragment is AnimatedFragment) {
                when (fragment.transitionType) {
                    AnimatedFragment.TransitionType.SLIDE_HORIZONTAL -> {
                        fragment.enterTransition = createHorizontalSlideInTransition()
                        fragment.exitTransition = createHorizontalSlideOutTransition()
                    }
                    AnimatedFragment.TransitionType.SLIDE_VERTICAL -> {
                        fragment.enterTransition = createVerticalSlideInTransition()
                        fragment.exitTransition = createVerticalSlideOutTransition()
                    }
                    AnimatedFragment.TransitionType.FADE -> {
                        fragment.enterTransition = createFadeInOutTransition()
                        fragment.exitTransition = createFadeInOutTransition()
                    }
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

private val Activity?.isNotFinished
    get() = this?.isFinishing != true

private val Fragment?.isStateNotSaved
    get() = this?.isStateSaved != true