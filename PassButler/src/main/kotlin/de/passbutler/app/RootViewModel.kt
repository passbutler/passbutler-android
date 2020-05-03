package de.passbutler.app

import android.text.format.DateUtils
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import de.passbutler.app.base.AbstractPassButlerApplication
import de.passbutler.app.base.viewmodels.CoroutineScopedViewModel
import de.passbutler.app.crypto.Biometrics
import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.resultOrThrowException
import de.passbutler.common.base.toUTF8String
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger
import javax.crypto.Cipher

class RootViewModel : CoroutineScopedViewModel() {

    var userManager: UserManager? = null
    var loggedInUserViewModel: UserViewModel? = null

    val rootScreenState = MutableLiveData<RootScreenState?>()
    val lockScreenState = MutableLiveData<LockScreenState?>()

    private val loggedInUserResultObserver = LoggedInUserResultObserver()

    private var lockScreenTimerJob: Job? = null

    override fun onCleared() {
        val userManager = userManager ?: throw UserManagerUninitializedException
        userManager.loggedInUserResult.removeObserver(loggedInUserResultObserver)

        super.onCleared()
    }

    suspend fun restoreLoggedInUser() {
        // Create `UserManager` if not already created
        val userManager = userManager ?: run {
            val localRepository = null
            val createdUserManager = UserManager(AbstractPassButlerApplication.applicationContext, localRepository)
            userManager = createdUserManager

            createdUserManager
        }

        userManager.restoreLoggedInUser()
    }

    suspend fun unlockScreenWithPassword(masterPassword: String): Result<Unit> {
        val userManager = userManager ?: throw UserManagerUninitializedException
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        val decryptSensibleDataResult = loggedInUserViewModel.decryptSensibleData(masterPassword)

        if (decryptSensibleDataResult is Success && loggedInUserViewModel.userType.value == UserType.REMOTE) {
            // Restore webservices asynchronously to avoid slow network is blocking unlock progress
            launch {
                userManager.restoreWebservices(masterPassword)
            }
        }

        return decryptSensibleDataResult
    }

    suspend fun initializeBiometricUnlockCipher(): Result<Cipher> {
        val encryptedMasterPasswordInitializationVector = loggedInUserViewModel?.encryptedMasterPassword?.value?.initializationVector
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
        val userManager = userManager ?: throw UserManagerUninitializedException
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        val encryptedMasterPassword = loggedInUserViewModel.encryptedMasterPassword.value?.encryptedValue
            ?: throw IllegalStateException("The encrypted master key was not found, despite biometric unlock was tried!")

        return try {
            val masterPassword = Biometrics.decryptData(initializedBiometricUnlockCipher, encryptedMasterPassword).resultOrThrowException().toUTF8String()
            loggedInUserViewModel.decryptSensibleData(masterPassword).resultOrThrowException()

            if (loggedInUserViewModel.userType.value == UserType.REMOTE) {
                // Restore webservices asynchronously to avoid slow network is blocking unlock progress
                launch {
                    userManager.restoreWebservices(masterPassword)
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

                withContext(Dispatchers.Main) {
                    // Be sure all UI is hidden behind the lock screen before clear crypto resources
                    lockScreenState.value = LockScreenState.Locked
                }

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
            val userManager = userManager ?: throw UserManagerUninitializedException

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
    return ViewModelProvider(activity).get(RootViewModel::class.java)
}

object LoggedInUserViewModelUninitializedException : IllegalStateException("The logged-in user viewmodel is null!")