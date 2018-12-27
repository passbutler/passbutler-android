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
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_root, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Show overview by default
        val overviewFragment = OverviewFragment.newInstance()
        showFragment(overviewFragment)
    }

    companion object {
        fun newInstance() = RootFragment()
    }
}