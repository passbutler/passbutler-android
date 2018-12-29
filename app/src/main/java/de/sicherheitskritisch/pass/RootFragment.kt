package de.sicherheitskritisch.pass

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.sicherheitskritisch.pass.ui.BaseFragment
import de.sicherheitskritisch.pass.ui.FragmentPresentingDelegate

class RootFragment : BaseFragment() {

    /**
     * The fragment manager is used by `FragmentPresentingDelegate` to provide fragment handling
     * used by the one-activity app concept.
     */
    val rootFragmentManager
        get() = childFragmentManager

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        activity?.let {
            val rootFragment = this
            val contentContainerResourceId = R.id.frameLayout_fragment_root_content_container
            fragmentPresentingDelegate = FragmentPresentingDelegate(it, rootFragment, contentContainerResourceId)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_root, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Show overview as first but do not add to backstack (it is first screen)
        val overviewFragment = OverviewFragment.newInstance()
        showFragment(overviewFragment, replaceFragment = true, addToBackstack = false)
    }

    /*
     * This methods `backstackCount()` and `popBackstack()` will indirectly call on `rootFragmentManager`,
     * but the `FragmentPresentingDelegate` is used nevertheless to have consistent calls and
     * avoid redundant safety checks done in `FragmentPresentingDelegate.popBackstack()`
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

    companion object {
        fun newInstance() = RootFragment()
    }
}