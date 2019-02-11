package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.sicherheitskritisch.passbutler.common.L
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.FragmentPresentingDelegate
import java.lang.ref.WeakReference

class RootFragment : BaseViewModelFragment<RootViewModel>() {

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        activity?.let {
            viewModel = ViewModelProviders.of(it).get(RootViewModel::class.java)

            val contentContainerResourceId = R.id.frameLayout_fragment_root_content_container
            fragmentPresentingDelegate = FragmentPresentingDelegate(
                WeakReference(it),
                WeakReference(this),
                contentContainerResourceId
            )
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_root, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        L.d("RootFragment", "onViewCreated(): savedInstanceState = $savedInstanceState")

        viewModel.rootScreenState.observe(this, Observer {
            updateRootScreen()
        })
    }

    private fun updateRootScreen() {
        val rootScreenState = viewModel.rootScreenState.value
        L.d("RootFragment", "updateRootScreen(): was called rootScreenState = $rootScreenState")

        when (rootScreenState) {
            is RootViewModel.RootScreenState.LoggedIn -> {
                when (rootScreenState.isUnlocked) {
                    true -> {
                        if (!isFragmentShown(OverviewFragment::class.java)) {
                            val overviewFragment = OverviewFragment.newInstance()
                            showFragmentAsFirstScreen(overviewFragment)
                        }
                    }
                    false -> {
                        if (!isFragmentShown(LockedScreenFragment::class.java)) {
                            val overviewFragment = LockedScreenFragment.newInstance()
                            showFragmentAsFirstScreen(overviewFragment)
                        }
                    }
                }
            }
            is RootViewModel.RootScreenState.LoggedOut -> {
                if (!isFragmentShown(LoginFragment::class.java)) {
                    val loginFragment = LoginFragment.newInstance()
                    showFragmentAsFirstScreen(loginFragment)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.applicationWasResumed()
    }

    override fun onPause() {
        super.onPause()
        viewModel.applicationWasPaused()
    }

    companion object {
        fun newInstance() = RootFragment()
    }
}