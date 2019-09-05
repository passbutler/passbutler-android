package de.sicherheitskritisch.passbutler

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.observe
import de.sicherheitskritisch.passbutler.base.observeOnce
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.FragmentPresentingDelegate
import de.sicherheitskritisch.passbutler.ui.showFragmentAsFirstScreen
import java.lang.ref.WeakReference

class RootFragment : BaseViewModelFragment<RootViewModel>() {

    override fun onAttach(context: Context) {
        super.onAttach(context)

        activity?.let {
            viewModel = getRootViewModel(it)

            val contentContainerResourceId = R.id.frameLayout_fragment_root_content_container
            val progressScreenViewResourceId = R.id.frameLayout_progress_container
            fragmentPresentingDelegate = FragmentPresentingDelegate(
                WeakReference(it),
                WeakReference(this),
                contentContainerResourceId,
                progressScreenViewResourceId
            )
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_root, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        L.d("RootFragment", "onViewCreated(): savedInstanceState = $savedInstanceState")

        viewModel.rootScreenState.observeOnce(this) {
            setupRootScreen()
        }

        viewModel.lockScreenState.observe(this) { newLockScreenState ->
            if (newLockScreenState == RootViewModel.LockScreenState.Locked) {
                showLockedScreen()
            }
        }
    }

    private fun setupRootScreen() {
        val rootScreenState = viewModel.rootScreenState.value
        L.d("RootFragment", "setupRootScreen(): rootScreenState = $rootScreenState")

        when (rootScreenState) {
            is RootViewModel.RootScreenState.LoggedIn -> setupLoggedInState()
            is RootViewModel.RootScreenState.LoggedOut -> setupLoggedOutState()
        }
    }

    private fun setupLoggedInState() {
        if (!isFragmentShown(OverviewFragment::class.java)) {
            // The animation are disabled on app initialisation
            showFragmentAsFirstScreen(
                fragment = OverviewFragment.newInstance(),
                animated = false
            )
        }
    }

    private fun setupLoggedOutState() {
        if (!isFragmentShown(LoginFragment::class.java)) {
            // The animation are disabled on app initialisation
            showFragmentAsFirstScreen(
                fragment = LoginFragment.newInstance(),
                animated = false
            )
        }
    }

    private fun showLockedScreen() {
        if (!isFragmentShown(LockedScreenFragment::class.java)) {
            // The debounce check must be disabled because on app initialisation the fragment stack is artificially created
            showFragment(
                fragment = LockedScreenFragment.newInstance(),
                debounce = false
            )
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
