package de.sicherheitskritisch.passbutler

import android.app.Application
import android.text.format.DateUtils
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import de.sicherheitskritisch.passbutler.base.AbstractPassButlerApplication
import de.sicherheitskritisch.passbutler.base.Failure
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.Success
import de.sicherheitskritisch.passbutler.base.resultOrThrowException
import de.sicherheitskritisch.passbutler.base.toUTF8String
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import de.sicherheitskritisch.passbutler.crypto.Biometrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger
import javax.crypto.Cipher

class RootViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    var loggedInUserViewModel: UserViewModel? = null

    val rootScreenState = MutableLiveData<RootScreenState?>()
    val lockScreenState = MutableLiveData<LockScreenState?>()

    val userManager
        get() = getApplication<AbstractPassButlerApplication>().userManager

    private val loggedInUserResultObserver = LoggedInUserResultObserver()

    private var lockScreenTimerJob: Job? = null

    init {
        userManager.loggedInUserResult.observeForever(loggedInUserResultObserver)
    }

    override fun onCleared() {
        userManager.loggedInUserResult.removeObserver(loggedInUserResultObserver)
        super.onCleared()
    }

    suspend fun restoreLoggedInUser() {
        userManager.restoreLoggedInUser()
    }

    suspend fun unlockScreenWithPassword(masterPassword: String): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw IllegalStateException("The logged-in user viewmodel is null!")
        val decryptSensibleDataResult = loggedInUserViewModel.decryptSensibleData(masterPassword)

        if (decryptSensibleDataResult is Success && loggedInUserViewModel.userType is UserType.Remote) {
            // Restore webservices asynchronously to avoid slow network is blocking unlock progress
            launch {
                userManager.restoreWebservices(masterPassword)

                withContext(Dispatchers.Main) {
                    loggedInUserViewModel.isSynchronizationPossible.notifyChange()
                }
            }
        }

        return decryptSensibleDataResult
    }

    suspend fun initializeBiometricUnlockCipher(): Result<Cipher> {
        val encryptedMasterPasswordInitializationVector = loggedInUserViewModel?.encryptedMasterPassword?.initializationVector
            ?: throw IllegalStateException("The encrypted master key initialization vector was not found, despite biometric unlock was tried!")

        return try {
            val biometricUnlockCipher = Biometrics.obtainKeyInstance().resultOrThrowException()
            Biometrics.initializeKeyForDecryption(UserViewModel.BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME, biometricUnlockCipher, encryptedMasterPasswordInitializationVector)
                .resultOrThrowException()

            Success(biometricUnlockCipher)
        } catch (exception: Exception) {
            Logger.warn("The biometric authentication failed because key could not be initialized - disable biometric unlock")
            loggedInUserViewModel?.disableBiometricUnlock()

            Failure(exception)
        }
    }

    suspend fun unlockScreenWithBiometrics(initializedBiometricUnlockCipher: Cipher): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw IllegalStateException("The logged-in user viewmodel is null!")
        val encryptedMasterPassword = loggedInUserViewModel.encryptedMasterPassword?.encryptedValue
            ?: throw IllegalStateException("The encrypted master key was not found, despite biometric unlock was tried!")

        return try {
            val masterPassword = Biometrics.decryptData(initializedBiometricUnlockCipher, encryptedMasterPassword).resultOrThrowException().toUTF8String()
            loggedInUserViewModel.decryptSensibleData(masterPassword).resultOrThrowException()

            if (loggedInUserViewModel.userType is UserType.Remote) {
                // Restore webservices asynchronously to avoid slow network is blocking unlock progress
                launch {
                    userManager.restoreWebservices(masterPassword)

                    withContext(Dispatchers.Main) {
                        loggedInUserViewModel.isSynchronizationPossible.notifyChange()
                    }
                }
            }

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
            lockScreenTimerJob = launch {
                delay(lockTimeout * DateUtils.SECOND_IN_MILLIS)
                Logger.debug("Lock screen")

                // Be sure all UI is hidden behind the lock screen before clear crypto resources
                lockScreenState.postValue(LockScreenState.Locked)
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
                    launch {
                        loggedInUserViewModel?.clearSensibleData()
                        loggedInUserViewModel?.cancelJobs()
                        loggedInUserViewModel = null
                    }
                }
            }
        }
    }
}

/**
 * Convenience method to obtain the `RootViewModel` from activity.
 */
fun getRootViewModel(activity: FragmentActivity): RootViewModel {
    // The `RootViewModel` must be received via activity to be sure it is the same for multiple fragment lifecycles
    return ViewModelProviders.of(activity).get(RootViewModel::class.java)
}