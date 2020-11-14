package de.passbutler.app.ui

import android.app.Activity
import android.view.Gravity
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.Transition
import androidx.transition.TransitionSet
import com.google.android.material.snackbar.Snackbar
import de.passbutler.app.AbstractRootFragment
import de.passbutler.common.ui.DebouncedUIPresenting
import de.passbutler.common.ui.FADE_TRANSITION_DURATION
import de.passbutler.common.ui.SLIDE_TRANSITION_DURATION
import de.passbutler.common.ui.TransitionType
import org.tinylog.kotlin.Logger
import java.lang.ref.WeakReference
import java.time.Instant

/**
 * Provides implementation of fragment management used by the one-activity app concept.
 * This delegate is delegated in `BaseFragment` and provided in the `AbstractRootFragment`.
 */
// TODO: Weak refs should not be needed
class UIPresenter(
    private val activityWeakReference: WeakReference<Activity>,
    private val rootFragmentWeakReference: WeakReference<AbstractRootFragment>,
    private val rootFragmentContainerResourceId: Int,
    private val rootFragmentProgressScreenViewResourceId: Int
) : UIPresenting, DebouncedUIPresenting {

    override var lastViewTransactionTime: Instant? = null

    private val activity
        get() = activityWeakReference.get()

    /**
     * The root fragment actually is used to contain the shown fragments
     */
    private val rootFragment
        get() = rootFragmentWeakReference.get()

    /**
     * The root fragment manager is used to provide fragment handling for the one-activity app concept
     */
    private val rootFragmentManager
        get() = rootFragment?.childFragmentManager

    /**
     * The progress screen view is contained in the root fragment and used for progress indication
     */
    private val rootFragmentProgressScreenView
        get() = rootFragment?.view?.findViewById<FrameLayout>(rootFragmentProgressScreenViewResourceId)

    override fun showFragment(fragment: Fragment, replaceFragment: Boolean, addToBackstack: Boolean, debounce: Boolean, transitionType: TransitionType) {
        val debouncedViewTransactionEnsured = ensureDebouncedViewTransaction().takeIf { debounce } ?: true

        if (activity.isNotFinished) {
            if (debouncedViewTransactionEnsured) {
                rootFragmentManager?.beginTransaction()?.let { fragmentTransaction ->
                    if (fragment is BaseFragment) {
                        fragment.transitionType = transitionType
                        applyTransitionToFragment(fragment)

                        fragment.uiPresentingDelegate = this
                    }

                    val fragmentTag = getFragmentTag(fragment)

                    if (replaceFragment) {
                        fragmentTransaction.replace(rootFragmentContainerResourceId, fragment, fragmentTag)
                    } else {
                        fragmentTransaction.add(rootFragmentContainerResourceId, fragment, fragmentTag)
                    }

                    if (addToBackstack) {
                        fragmentTransaction.addToBackStack(fragmentTag)
                    }

                    if (rootFragment.isStateNotSaved) {
                        fragmentTransaction.commit()
                    } else {
                        Logger.warn("The fragment transaction was done after state of root fragment was saved")
                        fragmentTransaction.commitAllowingStateLoss()
                    }
                }
            } else {
                Logger.warn("The view transaction was ignored because a recent transaction was already done!")
            }
        }
    }

    override fun <T : Fragment> isFragmentShown(fragmentClass: Class<T>): Boolean {
        return if (activity.isNotFinished && rootFragment.isStateNotSaved) {
            val fragmentTag = getFragmentTag(fragmentClass)
            rootFragmentManager?.findFragmentByTag(fragmentTag) != null
        } else {
            false
        }
    }

    override fun popBackstack() {
        if (activity.isNotFinished && rootFragment.isStateNotSaved) {
            rootFragmentManager?.popBackStack()
        }
    }

    override fun backstackCount(): Int {
        return rootFragmentManager?.backStackEntryCount ?: 0
    }

    override fun showProgress() {
        rootFragmentProgressScreenView?.showFadeInOutAnimation(true)
    }

    override fun hideProgress() {
        rootFragmentProgressScreenView?.showFadeInOutAnimation(false)
    }

    override fun showInformation(message: String) {
        rootFragment?.view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun showError(message: String) {
        // Same as information at the moment
        showInformation(message)
    }

    companion object {
        fun getFragmentTag(fragment: Fragment): String {
            return fragment.javaClass.fragmentInstanceIdentification
        }

        fun <T : Fragment> getFragmentTag(fragmentClass: Class<T>): String {
            return fragmentClass.fragmentInstanceIdentification
        }

        fun applyTransitionToFragment(fragment: BaseFragment) {
            fragment.transitionType.createFragmentTransition()?.let {
                fragment.enterTransition = it.enterTransition
                fragment.exitTransition = it.exitTransition
            }
        }
    }
}

internal val Class<*>.fragmentInstanceIdentification: String
    get() {
        // Use class name with full package
        return name
    }

internal val Activity?.isNotFinished
    get() = this?.isFinishing != true

internal val Fragment?.isStateNotSaved
    get() = this?.isStateSaved != true

data class FragmentTransition(val enterTransition: Transition, val exitTransition: Transition)

private fun TransitionType.createFragmentTransition(): FragmentTransition? {
    return when (this) {
        TransitionType.MODAL -> {
            FragmentTransition(enterTransition = createVerticalSlideInTransition(), exitTransition = createVerticalSlideOutTransition())
        }
        TransitionType.SLIDE -> {
            FragmentTransition(enterTransition = createHorizontalSlideInTransition(), exitTransition = createHorizontalSlideOutTransition())
        }
        TransitionType.FADE -> {
            FragmentTransition(enterTransition = createFadeInOutTransition(), exitTransition = createFadeInOutTransition())
        }
        TransitionType.NONE -> {
            null
        }
    }
}

private fun createHorizontalSlideInTransition(): Transition {
    return createTransitionSetWithDefaultInterpolator().apply {
        addTransition(Slide(Gravity.END))
        duration = SLIDE_TRANSITION_DURATION.toMillis()
    }
}

private fun createHorizontalSlideOutTransition(): Transition {
    return createTransitionSetWithDefaultInterpolator().apply {
        addTransition(Slide(Gravity.START))
        duration = SLIDE_TRANSITION_DURATION.toMillis()
    }
}

private fun createVerticalSlideInTransition(): Transition {
    return createTransitionSetWithDefaultInterpolator().apply {
        addTransition(Slide(Gravity.BOTTOM))
        duration = SLIDE_TRANSITION_DURATION.toMillis()
    }
}

private fun createVerticalSlideOutTransition(): Transition {
    return createTransitionSetWithDefaultInterpolator().apply {
        addTransition(Slide(Gravity.TOP))
        duration = SLIDE_TRANSITION_DURATION.toMillis()
    }
}

private fun createFadeInOutTransition(): Transition {
    return createTransitionSetWithDefaultInterpolator().apply {
        addTransition(Fade(Fade.IN))
        addTransition(Fade(Fade.OUT))
        duration = FADE_TRANSITION_DURATION.toMillis()
    }
}

private fun createTransitionSetWithDefaultInterpolator(): TransitionSet {
    return TransitionSet().apply {
        interpolator = FastOutSlowInInterpolator()
    }
}