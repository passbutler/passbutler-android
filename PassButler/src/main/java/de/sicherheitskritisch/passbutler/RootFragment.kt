package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.FragmentPresentingDelegate
import de.sicherheitskritisch.passbutler.ui.showFragmentAsFirstScreen
import java.lang.ref.WeakReference

class RootFragment : BaseViewModelFragment<RootViewModel>() {

    val progressScreenView
        get() = view?.findViewById<FrameLayout>(R.id.frameLayout_progress_container)

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        activity?.let {
            // The `RootViewModel` must be received via activity to be sure it survives fragment lifecycles
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
                    true -> handleLoggedInUnlockedState()
                    false -> handleLoggedInLockedState()
                }
            }
            is RootViewModel.RootScreenState.LoggedOut -> handleLoggedOutState()
        }
    }

    private fun handleLoggedInUnlockedState() {
        val lockedScreenShown = isFragmentShown(LockedScreenFragment::class.java)
        val overviewScreenShown = isFragmentShown(OverviewFragment::class.java)

        when {
            lockedScreenShown && !overviewScreenShown -> {
                L.d("RootFragment", "handleLoggedInUnlockedState(): Locked screen shown, but overview screen not shown - show overview screen")

                // TODO: This should be done more elegant
                popBackstack()

                val overviewFragment = OverviewFragment.newInstance()
                showFragmentAsFirstScreen(overviewFragment)
            }
            lockedScreenShown -> {
                L.d("RootFragment", "handleLoggedInUnlockedState(): Locked screen shown - pop locked screen")

                // Pop locked locked screen fragment if it is shown
                popBackstack()
            }
            !overviewScreenShown -> {
                L.d("RootFragment", "handleLoggedInUnlockedState(): Show overview screen")

                val overviewFragment = OverviewFragment.newInstance()
                showFragmentAsFirstScreen(overviewFragment)
            }
            else -> {
                L.w("RootFragment", "handleLoggedInUnlockedState(): Unexpected state!")
            }
        }
    }

    private fun handleLoggedInLockedState() {
        // Only show locked screen fragment is still not shown - do not replace fragment, because we want to pop back
        if (!isFragmentShown(LockedScreenFragment::class.java)) {
            L.d("RootFragment", "handleLoggedInLockedState(): Show locked screen fragment")

            val overviewFragment = LockedScreenFragment.newInstance()
            showFragment(overviewFragment, replaceFragment = false, addToBackstack = true)
        } else {
            L.w("RootFragment", "handleLoggedInLockedState(): Unexpected state!")
        }
    }

    private fun handleLoggedOutState() {
        if (!isFragmentShown(LoginFragment::class.java)) {
            val loginFragment = LoginFragment.newInstance()
            showFragmentAsFirstScreen(loginFragment)
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
