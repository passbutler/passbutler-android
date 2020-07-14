package de.passbutler.app.autofill

import de.passbutler.app.AbstractRootFragment
import de.passbutler.app.LockedScreenFragment
import de.passbutler.app.OverviewFragment
import de.passbutler.app.ui.TransitionType
import de.passbutler.app.ui.showFragmentAsFirstScreen
import org.tinylog.kotlin.Logger

class AutofillRootFragment : AbstractRootFragment() {

    override fun showLoggedInState() {
        if (!isFragmentShown(OverviewFragment::class.java)) {
            Logger.debug("Show logged-in state")

            // Disabled transition because the fragment stack is always cold initialised
            showFragmentAsFirstScreen(
                fragment = AutofillSelectionFragment.newInstance(),
                transitionType = TransitionType.NONE
            )
        }
    }

    override fun showLoggedOutState() {
        throw IllegalStateException("The logged-out state should never happen in autofill!")
    }

    override fun showLockedScreen() {
        if (!isFragmentShown(LockedScreenFragment::class.java)) {
            Logger.debug("Show locked screen state")

            // Disabled transition and debounce check because the fragment stack is always cold initialised
            showFragment(
                fragment = LockedScreenFragment.newInstance(),
                debounce = false,
                transitionType = TransitionType.NONE
            )
        }
    }

    companion object {
        fun newInstance() = AutofillRootFragment()
    }
}