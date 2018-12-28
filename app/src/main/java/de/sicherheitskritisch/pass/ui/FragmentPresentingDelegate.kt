package de.sicherheitskritisch.pass.ui

import android.app.Activity
import android.support.transition.Slide
import android.support.transition.Transition
import android.support.transition.TransitionSet
import android.support.v4.app.Fragment
import android.util.Log
import android.view.Gravity
import de.sicherheitskritisch.pass.RootFragment

/**
 * Provides implementation of fragment management used by the one-activity app concept.
 * This delegate is delegated in `BaseFragment` and provided in `RootFragment`.
 */
class FragmentPresentingDelegate(
    private val activity: Activity,
    private val rootFragment: RootFragment,
    private val rootFragmentContainerResourceId: Int
) : FragmentPresenting {

    override fun showFragment(fragment: Fragment, replaceFragment: Boolean, addToBackstack: Boolean) {
        if (!activity.isFinishing) {

            addTransitionToAnimatedFragment(fragment)

            val fragmentTransaction = rootFragment.rootFragmentManager.beginTransaction()

            // TODO: debouncing

            if (fragment.isHidden) {
                fragmentTransaction.show(fragment)
            } else {
                if (fragment is BaseFragment) {
                    fragment.fragmentPresentingDelegate = this
                }

                val newFragmentTag = fragment.javaClass.toString()

                if (replaceFragment) {
                    fragmentTransaction.replace(rootFragmentContainerResourceId, fragment, newFragmentTag)
                } else {
                    fragmentTransaction.add(rootFragmentContainerResourceId, fragment)
                }

                if (addToBackstack) {
                    fragmentTransaction.addToBackStack(newFragmentTag)
                }
            }

            if (!rootFragment.isStateSaved) {
                fragmentTransaction.commit()
            } else {
                Log.w(TAG, "showFragment(): The fragment transaction was done after state of root fragment was saved!")
                fragmentTransaction.commitAllowingStateLoss()
            }
        }
    }

    private fun addTransitionToAnimatedFragment(fragment: Fragment) {
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
            }
        }
    }

    private fun createHorizontalSlideInTransition(): Transition {
        val transitionSet = TransitionSet()
        transitionSet.addTransition(Slide(Gravity.END))
        return transitionSet
    }

    private fun createHorizontalSlideOutTransition(): Transition {
        val transitionSet = TransitionSet()
        transitionSet.addTransition(Slide(Gravity.START))
        return transitionSet
    }

    private fun createVerticalSlideInTransition(): Transition {
        val transitionSet = TransitionSet()
        transitionSet.addTransition(Slide(Gravity.BOTTOM))
        return transitionSet
    }

    private fun createVerticalSlideOutTransition(): Transition {
        val transitionSet = TransitionSet()
        transitionSet.addTransition(Slide(Gravity.TOP))
        return transitionSet
    }

    override fun popBackstack() {
        if (!activity.isFinishing && !rootFragment.isStateSaved) {
            rootFragment.rootFragmentManager.popBackStack()
        }
    }

    companion object {
        private const val TAG = "FragmentPresentingDelegate"
    }
}