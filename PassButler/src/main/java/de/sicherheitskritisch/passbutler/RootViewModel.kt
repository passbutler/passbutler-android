package de.sicherheitskritisch.passbutler

import android.app.Application
import android.text.format.DateUtils
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RootViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    var loggedInUserViewModel: UserViewModel? = null

    val rootScreenState = MutableLiveData<RootScreenState>()
    val lockScreenState = MutableLiveData<LockScreenState>()

    val unlockScreenRequestSendingViewModel = DefaultRequestSendingViewModel()

    private val userManager
        get() = getApplication<AbstractPassButlerApplication>().userManager

    private val loggedInUserObserver = LoggedInUserObserver()

    private var cryptoResourcesJob: Job? = null
    private var lockScreenTimerJob: Job? = null

    init {
        userManager.loggedInUser.observeForever(loggedInUserObserver)

        // Try to restore logged-in user after the observer was added
        launch {
            userManager.restoreLoggedInUser()
        }
    }

    override fun onCleared() {
        userManager.loggedInUser.removeObserver(loggedInUserObserver)
        super.onCleared()
    }

    fun unlockScreen(masterPassword: String) {
        cryptoResourcesJob?.cancel()
        cryptoResourcesJob = launch {
            unlockScreenRequestSendingViewModel.isLoading.postValue(true)

            try {
                loggedInUserViewModel?.unlockMasterEncryptionKey(masterPassword)

                unlockScreenRequestSendingViewModel.isLoading.postValue(false)
                unlockScreenRequestSendingViewModel.requestFinishedSuccessfully.emit()
            } catch (exception: Exception) {
                L.w("RootViewModel", "unlockScreen(): The unlock failed with exception!", exception)
                unlockScreenRequestSendingViewModel.isLoading.postValue(false)
                unlockScreenRequestSendingViewModel.requestError.postValue(exception)
            }
        }
    }

    fun applicationWasPaused() {
        startLockScreenTimer()
    }

    fun applicationWasResumed() {
        cancelLockScreenTimer()
    }

    private fun startLockScreenTimer() {
        // The lock timer must be only started if the user is logged in and unlocked (lock timeout available)
        loggedInUserViewModel?.lockTimeoutSetting?.value?.let { lockTimeout ->
            launch {
                lockScreenTimerJob?.cancelAndJoin()
                lockScreenTimerJob = launch {
                    delay(lockTimeout * DateUtils.SECOND_IN_MILLIS)
                    lockScreen()
                }
            }
        }
    }

    private fun cancelLockScreenTimer() {
        lockScreenTimerJob?.cancel()
    }

    private fun lockScreen() {
        cryptoResourcesJob?.cancel()
        cryptoResourcesJob = launch {
            // First show lock screen, than clear crypto resources
            lockScreenState.postValue(LockScreenState.Locked)
            loggedInUserViewModel?.clearMasterEncryptionKey()
        }
    }

    sealed class RootScreenState {
        object LoggedIn : RootScreenState()
        object LoggedOut : RootScreenState()
    }

    sealed class LockScreenState {
        object Locked : LockScreenState()
        object Unlocked : LockScreenState()
    }

    private inner class LoggedInUserObserver : Observer<LoggedInUserResult?> {
        override fun onChanged(loggedInUserResult: LoggedInUserResult?) {
            if (loggedInUserResult != null) {
                // Create new logged-in user first
                loggedInUserViewModel = UserViewModel(userManager, loggedInUserResult.user, loggedInUserResult.masterPassword)

                rootScreenState.value = RootScreenState.LoggedIn

                // The master password is only contained in `LoggedInUserResult` directly after the login
                lockScreenState.value = if (loggedInUserResult.masterPassword == null) LockScreenState.Locked else LockScreenState.Unlocked
            } else {
                rootScreenState.value = RootScreenState.LoggedOut
                lockScreenState.value = null

                // Finally reset logged-in user related jobs
                loggedInUserViewModel?.cancelJobs()
                loggedInUserViewModel = null
            }
        }
    }
}

/**
 * Convenience method to obtain the `RootViewModel` from activity.
 */
// Extension should be only available for specific `Fragment` type
@Suppress("unused")
fun BaseViewModelFragment<*>.getRootViewModel(activity: FragmentActivity): RootViewModel {
    // The `RootViewModel` must be received via activity to be sure it is the same for multiple fragment lifecycles
    return ViewModelProviders.of(activity).get(RootViewModel::class.java)
}