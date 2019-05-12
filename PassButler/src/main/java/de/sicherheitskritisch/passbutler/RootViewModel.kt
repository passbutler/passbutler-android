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

    var loggedInUserViewModel: UserViewModel? = null

    val rootScreenState = MutableLiveData<RootScreenState>()
    val lockScreenState = MutableLiveData<LockScreenState>()

    val unlockScreenRequestSendingViewModel = DefaultRequestSendingViewModel()

    private val userManager
        get() = getApplication<AbstractPassButlerApplication>().userManager

    private val loggedInUserObserver = Observer<LoggedInUserResult?> { loggedInUserResult ->
        if (loggedInUserResult != null) {
            loggedInUserViewModel = UserViewModel(userManager, loggedInUserResult.user, loggedInUserResult.masterPassword)

            rootScreenState.value = RootScreenState.LoggedIn

            // The master password is only contained in `LoggedInUserResult` directly after the login
            lockScreenState.value = if (loggedInUserResult.masterPassword == null) LockScreenState.Locked else LockScreenState.Unlocked
        } else {
            rootScreenState.value = RootScreenState.LoggedOut
            lockScreenState.value = null

            loggedInUserViewModel?.cancelJobs()
            loggedInUserViewModel = null
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
            // First show lock screen, than actually clear crypto resource
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
}
