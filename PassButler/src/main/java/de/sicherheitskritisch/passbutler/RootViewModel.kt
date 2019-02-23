package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import android.text.format.DateUtils
import de.sicherheitskritisch.passbutler.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RootViewModel : ViewModel() {

    internal val rootScreenState = MutableLiveData<RootScreenState>()
    internal val unlockScreenMethod = MutableLiveData<UnlockScreenMethod>()

    internal var loggedInUserViewModel: UserViewModel? = null

    private var lockScreenCoroutineJob: Job? = null

    private val loggedInUserObserver = Observer<User?> { newUser ->
        if (newUser != null) {
            loggedInUserViewModel = UserViewModel(newUser)
            rootScreenState.value = RootScreenState.LoggedIn(true)
        } else {
            loggedInUserViewModel = null
            rootScreenState.value = RootScreenState.LoggedOut
        }
    }

    init {
        UserManager.loggedInUser.observeForever(loggedInUserObserver)

        // Try to restore logged-in user after the observer was added
        UserManager.restoreLoggedInUser()
    }

    override fun onCleared() {
        super.onCleared()

        UserManager.loggedInUser.removeObserver(loggedInUserObserver)
        cancelLockScreenTimer()
    }

    internal fun applicationWasResumed() {
        cancelLockScreenTimer()
    }

    internal fun applicationWasPaused() {
        startLockScreenTimer()
    }

    private fun startLockScreenTimer() {
        // Only if user is logged in, start delayed lock screen timer
        loggedInUserViewModel?.lockTimeout?.value?.let { lockTimeout ->
            // TODO: This should be more elegant
            lockScreenCoroutineJob?.cancel()
            lockScreenCoroutineJob = GlobalScope.launch(Dispatchers.Main) {
                val userLockTimeoutMilliseconds = lockTimeout * DateUtils.SECOND_IN_MILLIS
                delay(userLockTimeoutMilliseconds)

                lockScreen()
            }
        }
    }

    private fun cancelLockScreenTimer() {
        lockScreenCoroutineJob?.cancel()
    }

    private fun lockScreen() {
        // Only alter the "logged in" screen state if the user is still in this state after the delay
        if (rootScreenState.value is RootScreenState.LoggedIn) {
            rootScreenState.value = RootScreenState.LoggedIn(false)

            // TODO: Free crypto memory and resources
        }
    }

    internal fun unlockScreen() {
        // Only alter the "logged in" screen state if the user is still in this state after the delay
        if (rootScreenState.value is RootScreenState.LoggedIn) {
            rootScreenState.value = RootScreenState.LoggedIn(true)

            // TODO: Decrypt data and recreate resources
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
