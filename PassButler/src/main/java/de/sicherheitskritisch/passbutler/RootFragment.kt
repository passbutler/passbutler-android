package de.sicherheitskritisch.passbutler

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.FragmentPresentingDelegate
import de.sicherheitskritisch.passbutler.ui.TransitionType
import de.sicherheitskritisch.passbutler.ui.showFragmentAsFirstScreen
import java.lang.ref.WeakReference

class RootFragment : BaseViewModelFragment<RootViewModel>() {

    private var viewWasInitialized = false

    private val rootScreenStateObserver = Observer<RootViewModel.RootScreenState?> {
        showRootScreen()
    }

    private val lockScreenStateObserver = Observer<RootViewModel.LockScreenState?> { newLockScreenState ->
        if (newLockScreenState == RootViewModel.LockScreenState.Locked) {
            showLockedScreen()
        }
    }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.d("RootFragment", "onCreate(): savedInstanceState = $savedInstanceState")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_root, container, false)
    }

    override fun onStart() {
        super.onStart()

        // TODO: Order of screen state and lock screen state always correct?
        viewModel.rootScreenState.observe(viewLifecycleOwner, rootScreenStateObserver)
        viewModel.lockScreenState.observe(viewLifecycleOwner, lockScreenStateObserver)
    }

    private fun showRootScreen() {
        val rootScreenState = viewModel.rootScreenState.value
        L.d("RootFragment", "showRootScreen(): rootScreenState = $rootScreenState")

        when (rootScreenState) {
            is RootViewModel.RootScreenState.LoggedIn -> showLoggedInState()
            is RootViewModel.RootScreenState.LoggedOut -> showLoggedOutState()
        }

        viewWasInitialized = true
    }

    private fun showLoggedInState() {
        if (!isFragmentShown(OverviewFragment::class.java)) {
            L.d("RootFragment", "showLoggedInState()")

            showFragmentAsFirstScreen(
                fragment = OverviewFragment.newInstance(),
                transitionType = if (viewWasInitialized) {
                    TransitionType.SLIDE
                } else {
                    TransitionType.NONE
                }
            )
        }
    }

    private fun showLoggedOutState() {
        if (!isFragmentShown(LoginFragment::class.java)) {
            L.d("RootFragment", "showLoggedOutState()")

            showFragmentAsFirstScreen(
                fragment = LoginFragment.newInstance(),
                transitionType = if (viewWasInitialized) {
                    TransitionType.SLIDE
                } else {
                    TransitionType.NONE
                }
            )
        }
    }

    private fun showLockedScreen() {
        if (!isFragmentShown(LockedScreenFragment::class.java)) {
            L.d("RootFragment", "showLockedScreen()")

            // The debounce check must be disabled because on app initialisation the fragment stack is artificially created
            showFragment(
                fragment = LockedScreenFragment.newInstance(),
                debounce = false,
                transitionType = if (viewWasInitialized) {
                    TransitionType.FADE
                } else {
                    TransitionType.NONE
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.rootFragmentWasResumed()
    }

    override fun onPause() {
        super.onPause()
        viewModel.rootFragmentWasPaused()
    }

    companion object {
        fun newInstance() = RootFragment()
    }
}
