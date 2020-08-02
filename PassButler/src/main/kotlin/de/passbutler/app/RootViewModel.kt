package de.passbutler.app

import android.text.format.DateUtils
import androidx.lifecycle.viewModelScope
import de.passbutler.common.LoggedInUserResult
import de.passbutler.common.UserViewModel
import de.passbutler.common.base.BindableObserver
import de.passbutler.common.base.Failure
import de.passbutler.common.base.MutableBindable
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.resultOrThrowException
import de.passbutler.common.base.toUTF8String
import de.passbutler.common.database.models.UserType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger
import javax.crypto.Cipher

class RootViewModel : UserViewModelUsingViewModel() {

    val rootScreenState = MutableBindable<RootScreenState?>(null)
    val lockScreenState = MutableBindable<LockScreenState?>(null)

    private val loggedInUserResultObserver = LoggedInUserResultObserver()

    private var lockScreenTimerJob: Job? = null

    override fun onCleared() {
        unregisterLoggedInUserResultObserver()
        super.onCleared()
    }

    suspend fun restoreLoggedInUser() {
        registerLoggedInUserResultObserver()

        val wasRestored = userManager.restoreLoggedInUser()

        // If the logged-in user was already restored, trigger the observers manually to initialize the view
        if (!wasRestored) {
            loggedInUserResultObserver.invoke(userManager.loggedInUserResult.value)
        }
    }

    private fun registerLoggedInUserResultObserver() {
        // Only notify observer if result was changed triggered by `restoreLoggedInUser()` call
        userManager.loggedInUserResult.addObserver(viewModelScope, false, loggedInUserResultObserver)
    }

    private fun unregisterLoggedInUserResultObserver() {
        userManager.loggedInUserResult.removeObserver(loggedInUserResultObserver)
    }

    suspend fun unlockScreenWithPassword(masterPassword: String): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException

        return try {
            loggedInUserViewModel.decryptSensibleData(masterPassword).resultOrThrowException()

            if (loggedInUserViewModel.userType == UserType.REMOTE) {
                restoreWebservices(loggedInUserViewModel, masterPassword)
            }

            lockScreenState.value = LockScreenState.Unlocked

            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    private fun restoreWebservices(loggedInUserViewModel: UserViewModel, masterPassword: String) {
        // Restore webservices asynchronously to avoid slow network is blocking unlock progress
        // Do not use `viewModelScope` here because otherwise the job will be cancelled if `LockedScreenFragment` is destroyed
        loggedInUserViewModel.launch {
            userManager.restoreWebservices(masterPassword)
        }
    }

    suspend fun initializeBiometricUnlockCipher(): Result<Cipher> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        val encryptedMasterPasswordInitializationVector = loggedInUserViewModel.encryptedMasterPassword?.initializationVector
            ?: throw IllegalStateException("The encrypted master key initialization vector was not found, despite biometric unlock was tried!")

        return try {
            val biometricsProvider = loggedInUserViewModel.biometricsProvider
            val biometricUnlockCipher = biometricsProvider.obtainKeyInstance().resultOrThrowException()
            biometricsProvider.initializeKeyForDecryption(UserViewModel.BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME, biometricUnlockCipher, encryptedMasterPasswordInitializationVector)
                .resultOrThrowException()

            Success(biometricUnlockCipher)
        } catch (exception: Exception) {
            Logger.warn("The biometric authentication failed because key could not be initialized - disable biometric unlock")
            loggedInUserViewModel.disableBiometricUnlock()

            Failure(exception)
        }
    }

    suspend fun unlockScreenWithBiometrics(initializedBiometricUnlockCipher: Cipher): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        val encryptedMasterPassword = loggedInUserViewModel.encryptedMasterPassword?.encryptedValue
            ?: throw IllegalStateException("The encrypted master key was not found, despite biometric unlock was tried!")

        return try {
            val masterPassword = loggedInUserViewModel.biometricsProvider.decryptData(initializedBiometricUnlockCipher, encryptedMasterPassword).resultOrThrowException().toUTF8String()
            loggedInUserViewModel.decryptSensibleData(masterPassword).resultOrThrowException()

            if (loggedInUserViewModel.userType == UserType.REMOTE) {
                restoreWebservices(loggedInUserViewModel, masterPassword)
            }

            lockScreenState.value = LockScreenState.Unlocked

            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    fun rootFragmentWasPaused() {
        startLockScreenTimer()
    }

    fun rootFragmentWasResumed() {
        cancelLockScreenTimer()
    }

    private fun startLockScreenTimer() {
        val lockTimeout = loggedInUserViewModel?.automaticLockTimeout?.value

        // The lock timer must be only started if the user is logged-in and unlocked (lock timeout available)
        if (lockTimeout != null) {
            Logger.debug("Start lock screen timer")

            lockScreenTimerJob?.cancel()
            lockScreenTimerJob = viewModelScope.launch {
                delay(lockTimeout * DateUtils.SECOND_IN_MILLIS)
                Logger.debug("Lock screen")

                // Be sure all UI is hidden behind the lock screen before clear crypto resources
                lockScreenState.value = LockScreenState.Locked

                loggedInUserViewModel?.clearSensibleData()
            }
        } else {
            Logger.debug("Do not start timer (user not logged in or screen not unlocked)")
        }
    }

    private fun cancelLockScreenTimer() {
        Logger.debug("Cancel lock screen timer")
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

    private inner class LoggedInUserResultObserver : BindableObserver<LoggedInUserResult?> {
        override fun invoke(loggedInUserResult: LoggedInUserResult?) {
            when (loggedInUserResult) {
                is LoggedInUserResult.LoggedIn.PerformedLogin -> {
                    rootScreenState.value = RootScreenState.LoggedIn
                    lockScreenState.value = LockScreenState.Unlocked
                }
                is LoggedInUserResult.LoggedIn.RestoredLogin -> {
                    rootScreenState.value = RootScreenState.LoggedIn
                    lockScreenState.value = LockScreenState.Locked
                }
                is LoggedInUserResult.LoggedOut -> {
                    rootScreenState.value = RootScreenState.LoggedOut
                    lockScreenState.value = null
                }
            }
        }
    }
}