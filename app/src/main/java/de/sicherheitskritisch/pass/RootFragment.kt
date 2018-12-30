package de.sicherheitskritisch.pass

import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.sicherheitskritisch.pass.ui.BaseViewModelFragment
import de.sicherheitskritisch.pass.ui.FragmentPresentingDelegate

class RootFragment : BaseViewModelFragment<RootViewModel>() {

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

            // Retrieve shared `RootViewModel` from activity backed `ViewModelProviders`
            viewModel = ViewModelProviders.of(it).get(RootViewModel::class.java)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_root, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isLoggedIn = viewModel?.isLoggedIn?.value ?: false
        val isUnlocked = viewModel?.isUnlocked?.value ?: false

        when {
            !isLoggedIn -> {
                val loginViewModel = LoginViewModel()
                val loginFragment = LoginFragment.newInstance(loginViewModel)
                showFragmentAsFirstScreen(loginFragment)
            }
            !isUnlocked -> {
                // TODO: show lock screen
            }
            else -> {
                val overviewFragment = OverviewFragment.newInstance()
                showFragmentAsFirstScreen(overviewFragment)
            }
        }
    }

    companion object {
        fun newInstance() = RootFragment()
    }
}