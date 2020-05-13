package de.passbutler.app

import android.text.format.DateUtils
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.passbutler.app.base.AbstractPassButlerApplication
import de.passbutler.app.base.BuildInformationProvider
import de.passbutler.app.base.viewmodels.CoroutineScopedViewModel
import de.passbutler.app.crypto.Biometrics
import de.passbutler.app.database.createLocalRepository
import de.passbutler.common.LoggedInUserResult
import de.passbutler.common.UserManager
import de.passbutler.common.UserManagerUninitializedException
import de.passbutler.common.base.BindableObserver
import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.resultOrThrowException
import de.passbutler.common.base.toUTF8String
import de.passbutler.common.database.models.UserType
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
        unregisterLoggedInUserResultObserver()
        super.onCleared()
    }

    private fun unregisterLoggedInUserResultObserver() {
        val userManager = userManager ?: throw UserManagerUninitializedException
        userManager.loggedInUserResult.removeObserver(loggedInUserResultObserver)
    }

    suspend fun restoreLoggedInUser() {
        // Create `UserManager` if not already created
        val userManager = userManager ?: run {
            val applicationContext = AbstractPassButlerApplication.applicationContext
            val localRepository = createLocalRepository(applicationContext)
            val createdUserManager = UserManager(localRepository, BuildInformationProvider)

            // Set `UserManager` first to be sure registered observer can already access field
            userManager = createdUserManager
            registerLoggedInUserResultObserver()

            createdUserManager
        }

        userManager.restoreLoggedInUser()
    }

    private fun registerLoggedInUserResultObserver() {
        val userManager = userManager ?: throw UserManagerUninitializedException
        userManager.loggedInUserResult.addObserver(viewModelScope, false, loggedInUserResultObserver)
    }

    suspend fun unlockScreenWithPassword(masterPassword: String): Result<Unit> {
        val userManager = userManager ?: throw UserManagerUninitializedException
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        val decryptSensibleDataResult = loggedInUserViewModel.decryptSensibleData(masterPassword)

        if (decryptSensibleDataResult is Success && loggedInUserViewModel.userType == UserType.REMOTE) {
            // Restore webservices asynchronously to avoid slow network is blocking unlock progress
            launch {
                userManager.restoreWebservices(masterPassword)
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
        val userManager = userManager ?: throw UserManagerUninitializedException
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        val encryptedMasterPassword = loggedInUserViewModel.encryptedMasterPassword?.encryptedValue
            ?: throw IllegalStateException("The encrypted master key was not found, despite biometric unlock was tried!")

        return try {
            val masterPassword = Biometrics.decryptData(initializedBiometricUnlockCipher, encryptedMasterPassword).resultOrThrowException().toUTF8String()
            loggedInUserViewModel.decryptSensibleData(masterPassword).resultOrThrowException()

            if (loggedInUserViewModel.userType == UserType.REMOTE) {
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

    private inner class LoggedInUserResultObserver : BindableObserver<LoggedInUserResult?> {
        override fun invoke(loggedInUserResult: LoggedInUserResult?) {
            val userManager = userManager ?: throw UserManagerUninitializedException

            when (loggedInUserResult) {
                is LoggedInUserResult.LoggedIn.PerformedLogin -> {
                    loggedInUserViewModel = UserViewModel(userManager, loggedInUserResult.loggedInUser, loggedInUserResult.masterPassword)

                    rootScreenState.value = RootScreenState.LoggedIn
                    lockScreenState.value = LockScreenState.Unlocked
                }
                is LoggedInUserResult.LoggedIn.RestoredLogin -> {
                    loggedInUserViewModel = UserViewModel(userManager, loggedInUserResult.loggedInUser, null)

                    rootScreenState.value = RootScreenState.LoggedIn
                    lockScreenState.value = LockScreenState.Locked
                }
                is LoggedInUserResult.LoggedOut -> {
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