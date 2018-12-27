package de.sicherheitskritisch.pass

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.sicherheitskritisch.pass.ui.BaseFragment
import de.sicherheitskritisch.pass.ui.FragmentPresentingHelper

class RootFragment : BaseFragment() {

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        activity?.let {
            val rootFragment = this
            fragmentPresentingHelper = FragmentPresentingHelper(it, rootFragment, R.id.frameLayout_fragment_root_content_container)
        }

        (activity as? MainActivity)?.addOnBackPressedListener(this)
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

    override fun onHandleBackPress(): Boolean {
        return if (childFragmentManager.backStackEntryCount > 0) {
            popBackstack()
            true
        } else {
            false
        }
    }

    override fun onDetach() {
        (activity as? MainActivity)?.removeOnBackPressedListener(this)
        super.onDetach()
    }

    companion object {
        fun newInstance() = RootFragment()
    }
}