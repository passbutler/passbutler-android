package de.sicherheitskritisch.passbutler

import android.app.Application
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.text.format.DateUtils
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RootViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    /**
     * In this class the tasks mainly wait and have not high load tasks
     */
    override val coroutineDispatcher = Dispatchers.IO

    val rootScreenState = MutableLiveData<RootScreenState>()

    var loggedInUserViewModel: UserViewModel? = null

    private val userManager
        get() = getApplication<AbstractPassButlerApplication>().userManager

    private val loggedInUserObserver = Observer<LoggedInUserResult?> { loggedInUserResult ->
        if (loggedInUserResult != null) {
            loggedInUserViewModel = UserViewModel(userManager, loggedInUserResult.user, loggedInUserResult.masterPassword)

            // The unlocked state happens if the `LoggedInUserResult` contains master password (only on login)
            val isUnlocked = loggedInUserResult.masterPassword != null
            rootScreenState.value = RootScreenState.LoggedIn(isUnlocked)
        } else {
            loggedInUserViewModel = null
            rootScreenState.value = RootScreenState.LoggedOut
        }
    }

    private var lockScreenCoroutineJob: Job? = null

    init {
        userManager.loggedInUser.observeForever(loggedInUserObserver)

        // Try to restore logged-in user after the observer was added
        userManager.restoreLoggedInUser()
    }

    override fun onCleared() {
        userManager.loggedInUser.removeObserver(loggedInUserObserver)
        super.onCleared()
    }

    fun applicationWasPaused() {
        startLockScreenTimer()
    }

    fun applicationWasResumed() {
        cancelLockScreenTimer()
    }

    private fun startLockScreenTimer() {
        loggedInUserViewModel?.lockTimeoutSetting?.value?.let { lockTimeout ->
            launch {
                // Cancel previous `lockScreenCoroutineJob` and wait it finished, until the new job is started
                lockScreenCoroutineJob?.cancelAndJoin()

                // The `unlockScreen` method is called from view on main thread, so call `lockScreen()` also there
                lockScreenCoroutineJob = launch(Dispatchers.Main) {
                    delay(lockTimeout * DateUtils.SECOND_IN_MILLIS)
                    lockScreen()
                }
            }
        }
    }

    private fun cancelLockScreenTimer() {
        lockScreenCoroutineJob?.cancel()
    }

    private fun lockScreen() {
        // Only change the "logged in" screen state if the user is still in this state after the delay
        if (rootScreenState.value is RootScreenState.LoggedIn) {
            rootScreenState.value = RootScreenState.LoggedIn(false)
            loggedInUserViewModel?.clearCryptoResources()
        }
    }

    // TODO: suspend + show error if failed
    internal fun unlockScreen(masterPassword: String) {
        // Only change the "logged in" screen state if the user is still in this state after the delay
        if (rootScreenState.value is RootScreenState.LoggedIn) {
            loggedInUserViewModel?.unlockCryptoResources(masterPassword)
            rootScreenState.value = RootScreenState.LoggedIn(true)
        }
    }

    sealed class RootScreenState {
        class LoggedIn(val isUnlocked: Boolean) : RootScreenState() {
            override fun toString(): String {
                return "LoggedIn(isUnlocked=$isUnlocked)"
            }
        }

        object LoggedOut : RootScreenState()
    }
}
