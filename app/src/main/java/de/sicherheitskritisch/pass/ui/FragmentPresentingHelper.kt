package de.sicherheitskritisch.pass.ui

import android.app.Activity
import android.support.v4.app.Fragment
import android.util.Log
import de.sicherheitskritisch.pass.RootFragment

class FragmentPresentingHelper(
    private val activity: Activity,
    private val rootFragment: RootFragment,
    private val rootFragmentContainerResourceId: Int
) : FragmentPresenting {

    private val rootFragmentFragmentManager
        get() = rootFragment.childFragmentManager

    override fun showFragment(fragment: Fragment, replaceFragment: Boolean, addToBackstack: Boolean) {
        if (!activity.isFinishing) {
            val fragmentTransaction = rootFragmentFragmentManager.beginTransaction()

            // TODO: transitions

            if (fragment.isHidden) {
                fragmentTransaction.show(fragment)
            } else {
                if (fragment is BaseFragment) {
                    fragment.fragmentPresentingHelper = this
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

    override fun popBackstack() {
        if (!activity.isFinishing && !rootFragment.isStateSaved) {
            rootFragmentFragmentManager.popBackStack()
        }
    }

    companion object {
        private const val TAG = "FragmentPresentingHelper"
    }
}