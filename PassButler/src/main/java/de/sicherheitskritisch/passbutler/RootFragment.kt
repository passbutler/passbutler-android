package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.sicherheitskritisch.passbutler.common.observe
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.FragmentPresentingDelegate

class RootFragment : BaseViewModelFragment<RootViewModel>() {

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        // TODO: Should be done in `onActivityCreated`
        viewModel = ViewModelProviders.of(this).get(RootViewModel::class.java)

        activity?.let {
            val contentContainerResourceId = R.id.frameLayout_fragment_root_content_container
            fragmentPresentingDelegate = FragmentPresentingDelegate(it, this, contentContainerResourceId)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_root, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.rootScreenState.observe(this, true, Observer {
            updateRootScreen()
        })
    }

    private fun updateRootScreen() {
        val rootScreenState = viewModel.rootScreenState.value

        when (rootScreenState) {
            is RootViewModel.RootScreenState.LoggedIn -> {
                // TODO: show lock screen if locked
                // val isUnlocked = rootScreenState.isUnlocked

                val overviewViewModel = OverviewViewModel(viewModel)
                val overviewFragment = OverviewFragment.newInstance(overviewViewModel)
                showFragmentAsFirstScreen(overviewFragment)
            }
            else -> {
                val loginViewModel = LoginViewModel(viewModel)
                val loginFragment = LoginFragment.newInstance(loginViewModel)
                showFragmentAsFirstScreen(loginFragment)
            }
        }
    }

    companion object {
        // No `viewModel` argument supplied because it is retrieved from `ViewModelProviders`
        fun newInstance() = RootFragment()
    }
}