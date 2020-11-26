package de.passbutler.app

import android.text.format.DateUtils
import androidx.lifecycle.viewModelScope
import de.passbutler.common.LoggedInUserViewModelUninitializedException
import de.passbutler.common.UserViewModel
import de.passbutler.common.base.Bindable
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

    val rootScreenState: Bindable<RootScreenState?>
        get() = _rootScreenState

    val lockScreenState: Bindable<LockScreenState?>
        get() = _lockScreenState

    private val _rootScreenState = MutableBindable<RootScreenState?>(null)
    private val _lockScreenState = MutableBindable<LockScreenState?>(null)

    private var lockScreenTimerJob: Job? = null

    fun restoreLoggedInUser() {
        Logger.debug("Try to restore logged-in user")

        if (userViewModelProvidingViewModel.loggedInUserViewModel != null) {
            Logger.debug("The logged-in user was restored")

            _rootScreenState.value = RootScreenState.LoggedIn
            _lockScreenState.value = LockScreenState.Locked
        } else {
            Logger.debug("No logged-in user found")

            _rootScreenState.value = RootScreenState.LoggedOut
            _lockScreenState.value = null
        }
    }

    suspend fun loginVault(serverUrlString: String?, username: String, masterPassword: String): Result<Unit> {
        val loginResult = userViewModelProvidingViewModel.loginUser(serverUrlString, username, masterPassword)

        return when (loginResult) {
            is Success -> {
                _rootScreenState.value = RootScreenState.LoggedIn
                _lockScreenState.value = LockScreenState.Unlocked
                Success(Unit)
            }
            is Failure -> Failure(loginResult.throwable)
        }
    }

    suspend fun unlockVaultWithPassword(masterPassword: String): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException

        return try {
            loggedInUserViewModel.decryptSensibleData(masterPassword).resultOrThrowException()

            if (loggedInUserViewModel.userType == UserType.REMOTE) {
                loggedInUserViewModel.restoreWebservices(masterPassword)
            }

            _lockScreenState.value = LockScreenState.Unlocked

            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
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

    suspend fun unlockVaultWithBiometrics(initializedBiometricUnlockCipher: Cipher): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        val encryptedMasterPassword = loggedInUserViewModel.encryptedMasterPassword?.encryptedValue
            ?: throw IllegalStateException("The encrypted master key was not found, despite biometric unlock was tried!")

        return try {
            val masterPassword = loggedInUserViewModel.biometricsProvider.decryptData(initializedBiometricUnlockCipher, encryptedMasterPassword).resultOrThrowException().toUTF8String()
            loggedInUserViewModel.decryptSensibleData(masterPassword).resultOrThrowException()

            if (loggedInUserViewModel.userType == UserType.REMOTE) {
                loggedInUserViewModel.restoreWebservices(masterPassword)
            }

            _lockScreenState.value = LockScreenState.Unlocked

            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    suspend fun closeVault(): Result<Unit> {
        val logoutResult = userViewModelProvidingViewModel.logoutUser()

        return when (logoutResult) {
            is Success -> {
                _rootScreenState.value = RootScreenState.LoggedOut
                _lockScreenState.value = null
                Success(Unit)
            }
            is Failure -> Failure(logoutResult.throwable)
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
                _lockScreenState.value = LockScreenState.Locked

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
}