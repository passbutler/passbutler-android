package de.sicherheitskritisch.passbutler

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.TransformingMutableLiveData
import de.sicherheitskritisch.passbutler.base.NonNullValueGetterLiveData
import de.sicherheitskritisch.passbutler.base.createRequestSendingJob
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import de.sicherheitskritisch.passbutler.crypto.Biometrics
import kotlinx.coroutines.Job
import javax.crypto.Cipher

class SettingsViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    var loggedInUserViewModel: UserViewModel? = null

    // TODO: Add string/internal/stored mapping to convert more safe

    val automaticLockTimeoutSetting by lazy {
        // Lazy initialisation because `loggedInUserViewModel` is lately set by `SettingsFragment`
        loggedInUserViewModel?.automaticLockTimeout?.let { automaticLockTimeoutLiveData ->
            TransformingMutableLiveData(
                source = automaticLockTimeoutLiveData,
                toDestinationConverter = { it?.toString() },
                toSourceConverter = { it?.toIntOrNull() }
            )
        }
    }

    val hidePasswordsEnabledSetting: MutableLiveData<Boolean?>?
        get() = loggedInUserViewModel?.hidePasswordsEnabled

    val biometricUnlockEnabled: NonNullValueGetterLiveData<Boolean>?
        get() = loggedInUserViewModel?.biometricUnlockEnabled

    val generateBiometricUnlockKeyViewModel = DefaultRequestSendingViewModel()
    val cancelSetupBiometricUnlockKeyViewModel = DefaultRequestSendingViewModel()
    val enableBiometricUnlockKeyViewModel = DefaultRequestSendingViewModel()
    val disableBiometricUnlockKeyViewModel = DefaultRequestSendingViewModel()

    private var setupBiometricUnlockKeyJob: Job? = null

    @Throws(InitializeSetupBiometricUnlockCipherFailedException::class)
    suspend fun initializeSetupBiometricUnlockCipher(): Cipher {
        return try {
            val biometricUnlockCipher = Biometrics.obtainKeyInstance()
            Biometrics.initializeKeyForEncryption(UserViewModel.BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME, biometricUnlockCipher)

            biometricUnlockCipher
        } catch (e: Exception) {
            throw InitializeSetupBiometricUnlockCipherFailedException(e)
        }
    }

    fun generateBiometricUnlockKey() {
        setupBiometricUnlockKeyJob?.cancel()
        setupBiometricUnlockKeyJob = createRequestSendingJob(generateBiometricUnlockKeyViewModel) {
            Biometrics.generateKey(UserViewModel.BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME)
        }
    }

    fun enableBiometricUnlock(initializedSetupBiometricUnlockCipher: Cipher, masterPassword: String) {
        setupBiometricUnlockKeyJob?.cancel()
        setupBiometricUnlockKeyJob = createRequestSendingJob(enableBiometricUnlockKeyViewModel) {
            loggedInUserViewModel?.enableBiometricUnlock(initializedSetupBiometricUnlockCipher, masterPassword)
        }
    }

    fun cancelBiometricUnlockSetup() {
        setupBiometricUnlockKeyJob?.cancel()
        setupBiometricUnlockKeyJob = createRequestSendingJob(cancelSetupBiometricUnlockKeyViewModel) {
            // Discard all generated keys and persisted data if the setup is canceled to avoid incomplete setup state
            loggedInUserViewModel?.disableBiometricUnlock()
        }
    }

    fun disableBiometricUnlock() {
        setupBiometricUnlockKeyJob?.cancel()
        setupBiometricUnlockKeyJob = createRequestSendingJob(disableBiometricUnlockKeyViewModel) {
            loggedInUserViewModel?.disableBiometricUnlock()
        }
    }

    class InitializeSetupBiometricUnlockCipherFailedException(cause: Throwable) : Exception(cause)
}
