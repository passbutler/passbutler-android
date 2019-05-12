package de.sicherheitskritisch.passbutler

import android.app.Application
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.text.format.DateUtils
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RootViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    val rootScreenState = MutableLiveData<RootScreenState>()

    var loggedInUserViewModel: UserViewModel? = null

    val unlockRequestSendingViewModel = DefaultRequestSendingViewModel()

    private val userManager
        get() = getApplication<AbstractPassButlerApplication>().userManager

    private val loggedInUserObserver = Observer<LoggedInUserResult?> { loggedInUserResult ->
        if (loggedInUserResult != null) {
            loggedInUserViewModel = UserViewModel(userManager, loggedInUserResult.user, loggedInUserResult.masterPassword)

            // The unlocked state happens if the `LoggedInUserResult` contains master password (only on login)
            val loggedInUserIsUnlocked = loggedInUserResult.masterPassword != null
            rootScreenState.value = RootScreenState.LoggedIn(loggedInUserIsUnlocked)
        } else {
            loggedInUserViewModel?.cancelJobs()
            loggedInUserViewModel = null

            rootScreenState.value = RootScreenState.LoggedOut
        }
    }

    private var handleCryptoResourcesCoroutineJob: Job? = null
    private var lockTimerCoroutineJob: Job? = null

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
        handleCryptoResourcesCoroutineJob?.cancel()
        handleCryptoResourcesCoroutineJob = launch {
            unlockRequestSendingViewModel.isLoading.postValue(true)

            try {
                loggedInUserViewModel?.unlockMasterEncryptionKey(masterPassword)

                unlockRequestSendingViewModel.isLoading.postValue(false)
                unlockRequestSendingViewModel.requestFinishedSuccessfully.emit()

                rootScreenState.postValue(RootScreenState.LoggedIn(true))
            } catch (exception: Exception) {
                L.w("RootViewModel", "unlockScreen(): The unlock failed with exception!", exception)
                unlockRequestSendingViewModel.isLoading.postValue(false)
                unlockRequestSendingViewModel.requestError.postValue(exception)
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
                lockTimerCoroutineJob?.cancelAndJoin()
                lockTimerCoroutineJob = launch {
                    delay(lockTimeout * DateUtils.SECOND_IN_MILLIS)
                    lockScreen()
                }
            }
        }
    }

    private fun cancelLockScreenTimer() {
        lockTimerCoroutineJob?.cancel()
    }

    private fun lockScreen() {
        handleCryptoResourcesCoroutineJob?.cancel()
        handleCryptoResourcesCoroutineJob = launch {
            // Only change the screen state if the user is still in this state when this code is called
            if (rootScreenState.value is RootScreenState.LoggedIn) {
                loggedInUserViewModel?.clearMasterEncryptionKey()
                rootScreenState.postValue(RootScreenState.LoggedIn(false))
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
}
