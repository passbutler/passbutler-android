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

    override fun backstackCount(): Int {
        return rootFragment.rootFragmentManager.backStackEntryCount
    }

    companion object {
        private const val TAG = "FragmentPresentingDelegate"

        fun getFragmentTag(fragment: Fragment): String {
            val fragmentClassnameWithPath = fragment.javaClass.canonicalName ?: fragment.javaClass.toString()
            return fragmentClassnameWithPath
        }
    }
}
