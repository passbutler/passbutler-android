package de.passbutler.app

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import de.passbutler.app.databinding.FragmentRootBinding
import de.passbutler.app.ui.BaseFragment
import de.passbutler.app.ui.FragmentPresenter
import de.passbutler.app.ui.TransitionType
import de.passbutler.app.ui.showFragmentAsFirstScreen
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger
import java.lang.ref.WeakReference

class RootFragment : BaseFragment() {

    val viewModel by userViewModelUsingViewModels<RootViewModel>(userViewModelProvidingViewModel = { userViewModelProvidingViewModel })
    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

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
            Logger.debug("Apply viewModel = $viewModel in $this")

            val contentContainerResourceId = R.id.frameLayout_fragment_root_content_container
            val progressScreenViewResourceId = R.id.frameLayout_progress_container
            fragmentPresentingDelegate = FragmentPresenter(
                WeakReference(it),
                WeakReference(this),
                contentContainerResourceId,
                progressScreenViewResourceId
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.debug("savedInstanceState = $savedInstanceState")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentRootBinding.inflate(inflater)

        viewModel.rootScreenState.observe(viewLifecycleOwner, rootScreenStateObserver)
        viewModel.lockScreenState.observe(viewLifecycleOwner, lockScreenStateObserver)

        // Try to restore logged-in user after the observers were added
        launch {
            viewModel.restoreLoggedInUser()
        }

        return binding.root
    }

    private fun showRootScreen() {
        val rootScreenState = viewModel.rootScreenState.value
        Logger.debug("Show screen state '$rootScreenState'")

        when (rootScreenState) {
            is RootViewModel.RootScreenState.LoggedIn -> showLoggedInState()
            is RootViewModel.RootScreenState.LoggedOut -> showLoggedOutState()
        }

        viewWasInitialized = true
    }

    private fun showLoggedInState() {
        if (!isFragmentShown(OverviewFragment::class.java)) {
            Logger.debug("Show logged-in state")

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
            Logger.debug("Show logged-out state")

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
            Logger.debug("Show locked screen state")

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
