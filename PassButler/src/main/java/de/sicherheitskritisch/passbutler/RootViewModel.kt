package de.sicherheitskritisch.passbutler

import android.app.Application
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.text.format.DateUtils
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import de.sicherheitskritisch.passbutler.models.User
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RootViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    val rootScreenState = MutableLiveData<RootScreenState>()
    val unlockScreenMethod = MutableLiveData<UnlockScreenMethod>()

    var loggedInUserViewModel: UserViewModel? = null

    private var lockScreenCoroutineJob: Job? = null

    private val userManager
        get() = getApplication<AbstractPassButlerApplication>().userManager

    private val loggedInUserObserver = Observer<User?> { newUser ->
        if (newUser != null) {
            loggedInUserViewModel = UserViewModel(userManager, newUser)
            rootScreenState.value = RootScreenState.LoggedIn(true)
        } else {
            loggedInUserViewModel = null
            rootScreenState.value = RootScreenState.LoggedOut
        }
    }

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
        // Only if user is logged in, start delayed lock screen timer
        loggedInUserViewModel?.lockTimeout?.value?.let { lockTimeout ->
            launch {
                // Cancel previous `lockScreenCoroutineJob` and wait it finished, until the new job is started
                lockScreenCoroutineJob?.cancelAndJoin()
                lockScreenCoroutineJob = launch {
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
        // Always dispatch on background thread because of resource cleanup happen her
        launch {
            // Only alter the "logged in" screen state if the user is still in this state after the delay
            if (rootScreenState.value is RootScreenState.LoggedIn) {
                rootScreenState.postValue(RootScreenState.LoggedIn(false))

                // TODO: Free crypto memory and resources
            }
        }
    }

    internal fun unlockScreen() {
        // Always dispatch on background thread because of resource creation happen her
        launch {
            // Only alter the "logged in" screen state if the user is still in this state after the delay
            if (rootScreenState.value is RootScreenState.LoggedIn) {
                rootScreenState.postValue(RootScreenState.LoggedIn(true))

                // TODO: Decrypt data and recreate resources
            }
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

    enum class UnlockScreenMethod {
        PASSWORD,
        FINGERPRINT
    }
}
