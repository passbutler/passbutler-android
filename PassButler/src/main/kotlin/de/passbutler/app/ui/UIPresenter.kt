package de.passbutler.app.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.Transition
import androidx.transition.TransitionSet
import com.google.android.material.snackbar.Snackbar
import de.passbutler.app.AbstractRootFragment
import de.passbutler.common.ui.BannerPresenting
import de.passbutler.common.ui.DebouncedUIPresenting
import de.passbutler.common.ui.FADE_TRANSITION_DURATION
import de.passbutler.common.ui.ProgressPresenting
import de.passbutler.common.ui.SLIDE_TRANSITION_DURATION
import de.passbutler.common.ui.TransitionType
import org.tinylog.kotlin.Logger
import java.time.Instant

/**
 * Provides implementation of fragment management used by the one-activity app concept.
 * This delegate is delegated in `BaseFragment` and provided in the `AbstractRootFragment`.
 */
class UIPresenter(
    private val activity: Activity,
    private val rootFragment: AbstractRootFragment,
    private val rootFragmentScreenContainerResourceId: Int,
    private val rootFragmentProgressScreenViewResourceId: Int
) : UIPresenting,
    ProgressPresenting by ProgressPresenter(rootFragment, rootFragmentProgressScreenViewResourceId),
    BannerPresenting by BannerPresenter(rootFragment),
    DebouncedUIPresenting {

    override var lastViewTransactionTime: Instant? = null

    private val rootFragmentManager
        get() = rootFragment.childFragmentManager

    override fun showFragment(fragment: Fragment, replaceFragment: Boolean, addToBackstack: Boolean, userTriggered: Boolean, transitionType: TransitionType) {
        val debouncedViewTransactionEnsured = ensureDebouncedViewTransaction().takeIf { userTriggered } ?: true

        if (activity.isNotFinished) {
            if (debouncedViewTransactionEnsured) {
                rootFragmentManager.beginTransaction().let { fragmentTransaction ->
                    if (fragment is BaseFragment) {
                        fragment.transitionType = transitionType
                        applyTransitionToFragment(fragment)

                        fragment.uiPresentingDelegate = this
                    }

                    val fragmentTag = getFragmentTag(fragment)

                    if (replaceFragment) {
                        fragmentTransaction.replace(rootFragmentScreenContainerResourceId, fragment, fragmentTag)
                    } else {
                        fragmentTransaction.add(rootFragmentScreenContainerResourceId, fragment, fragmentTag)
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
            rootFragmentManager.findFragmentByTag(fragmentTag) != null
        } else {
            false
        }
    }

    override fun popBackstack() {
        if (activity.isNotFinished && rootFragment.isStateNotSaved) {
            rootFragmentManager.popBackStack()
        }
    }

    override fun backstackCount(): Int {
        return rootFragmentManager.backStackEntryCount
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

class ProgressPresenter(
    private val rootFragment: AbstractRootFragment,
    private val rootFragmentProgressScreenViewResourceId: Int
) : ProgressPresenting {

    private val progressScreenView
        get() = rootFragment.view?.findViewById<View>(rootFragmentProgressScreenViewResourceId)

    override fun showProgress() {
        progressScreenView?.showFadeInOutAnimation(true)
    }

    override fun hideProgress() {
        progressScreenView?.showFadeInOutAnimation(false)
    }
}

class BannerPresenter(
    private val rootFragment: AbstractRootFragment
) : BannerPresenting {

    override fun showInformation(message: String) {
        rootFragment.view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun showError(message: String) {
        // Same as information at the moment
        showInformation(message)
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
