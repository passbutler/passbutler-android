package de.sicherheitskritisch.passbutler

import android.app.Application
import android.net.Uri
import android.text.format.DateUtils
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.SignalEmitter
import de.sicherheitskritisch.passbutler.base.createRequestSendingJob
import de.sicherheitskritisch.passbutler.base.toUTF8String
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import de.sicherheitskritisch.passbutler.crypto.Biometrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

class RootViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    var loggedInUserViewModel: UserViewModel? = null

    val rootScreenState = MutableLiveData<RootScreenState?>()
    val lockScreenState = MutableLiveData<LockScreenState?>()

    val loginRequestSendingViewModel = DefaultRequestSendingViewModel()
    val unlockScreenRequestSendingViewModel = DefaultRequestSendingViewModel()

    val webserviceRestored = SignalEmitter()

    private val userManager
        get() = getApplication<AbstractPassButlerApplication>().userManager

    private val loggedInUserResultObserver = LoggedInUserResultObserver()

    private var loginCoroutineJob: Job? = null
    private var cryptoResourcesJob: Job? = null
    private var lockScreenTimerJob: Job? = null

    init {
        userManager.loggedInUserResult.observeForever(loggedInUserResultObserver)

        // Try to restore logged-in user after the observer was added
        launch {
            userManager.restoreLoggedInUser()
        }
    }

    override fun onCleared() {
        userManager.loggedInUserResult.removeObserver(loggedInUserResultObserver)
        super.onCleared()
    }

    fun loginUser(serverUrlString: String?, username: String, masterPassword: String) {
        loginCoroutineJob?.cancel()
        loginCoroutineJob = createRequestSendingJob(loginRequestSendingViewModel) {
            when (serverUrlString) {
                null -> userManager.loginLocalUser(username, masterPassword)
                else -> {
                    val serverUrl = Uri.parse(serverUrlString)
                    userManager.loginRemoteUser(username, masterPassword, serverUrl)
                }
            }
        }
    }

    fun logoutUser() {
        loginCoroutineJob?.cancel()
        loginCoroutineJob = createRequestSendingJob(loginRequestSendingViewModel) {
            // Some artificial delay to look flow more natural
            delay(500)

            loggedInUserViewModel?.logout()
        }
    }

    fun unlockScreenWithPassword(masterPassword: String) {
        cryptoResourcesJob?.cancel()
        cryptoResourcesJob = createRequestSendingJob(unlockScreenRequestSendingViewModel) {
            withContext(Dispatchers.Default) {
                loggedInUserViewModel?.unlockMasterEncryptionKey(masterPassword)
            }

            // Restore webservices asynchronously to avoid slow network is blocking unlock
            launch {
                restoreWebservices(masterPassword)
            }
        }
    }

    // TODO: Make non-suspend
    @Throws(InitializeBiometricUnlockCipherFailedException::class)
    suspend fun initializeBiometricUnlockCipher(): Cipher {
        return try {
            val biometricUnlockCipher = Biometrics.obtainKeyInstance()
            val encryptedMasterPasswordInitializationVector = loggedInUserViewModel?.encryptedMasterPassword?.initializationVector
                ?: throw IllegalStateException("The encrypted master key initialization vector was not found, despite biometric unlock was tried!")
            Biometrics.initializeKeyForDecryption(UserViewModel.BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME, biometricUnlockCipher, encryptedMasterPasswordInitializationVector)

            biometricUnlockCipher
        } catch (e: Exception) {
            L.w("RootViewModel", "initializeBiometricUnlockCipher(): The biometric authentication failed because key could not be initialized - disable biometric unlock!")
            disableBiometricUnlock()

            throw InitializeBiometricUnlockCipherFailedException(e)
        }
    }

    fun unlockScreenWithBiometrics(initializedBiometricUnlockCipher: Cipher) {
        cryptoResourcesJob?.cancel()
        cryptoResourcesJob = createRequestSendingJob(unlockScreenRequestSendingViewModel) {
            val encryptedMasterPassword = loggedInUserViewModel?.encryptedMasterPassword?.encryptedValue
                ?: throw IllegalStateException("The encrypted master key was not found, despite biometric unlock was tried!")

            val masterPassword = Biometrics.decryptData(initializedBiometricUnlockCipher, encryptedMasterPassword).toUTF8String()

            withContext(Dispatchers.Default) {
                loggedInUserViewModel?.unlockMasterEncryptionKey(masterPassword)
            }

            // Restore webservices asynchronously to avoid slow network is blocking unlock
            launch {
                restoreWebservices(masterPassword)
            }
        }
    }

    fun updateBiometricUnlockAvailability() {
        loggedInUserViewModel?.biometricUnlockAvailable?.notifyChange()
        loggedInUserViewModel?.biometricUnlockEnabled?.notifyChange()
    }

    fun rootFragmentWasStopped() {
        // Always cancel possible running unlock job, because view of `LockedScreenFragment` will be disconnected in `onStop()`, thus event can't be handled
        cryptoResourcesJob?.cancel()
    }

    fun rootFragmentWasPaused() {
        startLockScreenTimer()
    }

    fun rootFragmentWasResumed() {
        cancelLockScreenTimer()
    }

    private suspend fun disableBiometricUnlock() {
        try {
            loggedInUserViewModel?.disableBiometricUnlock()
        } catch (e: Exception) {
            L.w("RootViewModel", "disableBiometricUnlock(): The biometric unlock could not be disabled!", e)
        }
    }

    private suspend fun restoreWebservices(masterPassword: String) {
        if (loggedInUserViewModel?.userType is UserType.Server) {
            userManager.restoreWebservices(masterPassword)
            webserviceRestored.emit()
        }
    }

    private fun startLockScreenTimer() {
        // The lock timer must be only started if the user is logged-in and unlocked (lock timeout available)
        loggedInUserViewModel?.automaticLockTimeout?.value?.let { lockTimeout ->
            L.d("RootViewModel", "startLockScreenTimer()")

            lockScreenTimerJob?.cancel()
            lockScreenTimerJob = launch {
                delay(lockTimeout * DateUtils.SECOND_IN_MILLIS)
                lockScreen()
            }
        }
    }

    private fun lockScreen() {
        L.d("RootViewModel", "lockScreen()")

        cryptoResourcesJob?.cancel()
        cryptoResourcesJob = launch {
            // Be sure all UI is hidden behind the lock screen before clear crypto resources
            lockScreenState.postValue(LockScreenState.Locked)
            loggedInUserViewModel?.clearMasterEncryptionKey()
        }
    }

    private fun cancelLockScreenTimer() {
        L.d("RootViewModel", "cancelLockScreenTimer()")
        lockScreenTimerJob?.cancel()
    }

    sealed class RootScreenState {
        object LoggedIn : RootScreenState()
        object LoggedOut : RootScreenState()
    }

    sealed class LockScreenState {
        object Locked : LockScreenState()
        object Unlocked : LockScreenState()
    }

    private inner class LoggedInUserResultObserver : Observer<LoggedInUserResult?> {
        override fun onChanged(loggedInUserResult: LoggedInUserResult?) {
            when (loggedInUserResult) {
                is LoggedInUserResult.PerformedLogin -> {
                    loggedInUserViewModel = UserViewModel(userManager, loggedInUserResult.newLoggedInUser, loggedInUserResult.masterPassword)

                    rootScreenState.value = RootScreenState.LoggedIn
                    lockScreenState.value = LockScreenState.Unlocked
                }
                is LoggedInUserResult.RestoredLogin -> {
                    loggedInUserViewModel = UserViewModel(userManager, loggedInUserResult.newLoggedInUser, null)

                    rootScreenState.value = RootScreenState.LoggedIn
                    lockScreenState.value = LockScreenState.Locked
                }
                else -> {
                    rootScreenState.value = RootScreenState.LoggedOut
                    lockScreenState.value = null

                    // Finally clear crypto resources and reset related jobs
                    loggedInUserViewModel?.clearMasterEncryptionKey()
                    loggedInUserViewModel?.cancelJobs()
                    loggedInUserViewModel = null
                }
            }
        }
    }

    class InitializeBiometricUnlockCipherFailedException(cause: Throwable) : Exception(cause)
}

/**
 * Convenience method to obtain the `RootViewModel` from activity.
 */
fun getRootViewModel(activity: FragmentActivity): RootViewModel {
    // The `RootViewModel` must be received via activity to be sure it is the same for multiple fragment lifecycles
    return ViewModelProviders.of(activity).get(RootViewModel::class.java)
}