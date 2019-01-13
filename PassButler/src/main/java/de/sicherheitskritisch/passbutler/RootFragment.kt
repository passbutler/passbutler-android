package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.sicherheitskritisch.passbutler.common.L
import de.sicherheitskritisch.passbutler.common.observe
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

        viewModel.rootScreenState.observe(this, true, Observer {
            updateRootScreen()
        })
    }

    private fun updateRootScreen() {
        val rootScreenState = viewModel.rootScreenState.value
        L.d("RootFragment", "updateRootScreen(): was called rootScreenState = $rootScreenState")

        when (rootScreenState) {
            is RootViewModel.RootScreenState.LoggedIn -> {
                // TODO: show lock screen if locked
                // val isUnlocked = rootScreenState.isUnlocked

                val overviewFragment = OverviewFragment.newInstance()
                showFragmentAsFirstScreen(overviewFragment)
            }
            is RootViewModel.RootScreenState.LoggedOut -> {
                val loginFragment = LoginFragment.newInstance()
                showFragmentAsFirstScreen(loginFragment)
            }
        }
    }

    companion object {
        fun newInstance() = RootFragment()
    }
}