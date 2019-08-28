package de.sicherheitskritisch.passbutler

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.createRequestSendingJob
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import de.sicherheitskritisch.passbutler.crypto.Biometrics
import kotlinx.coroutines.Job
import javax.crypto.Cipher

class SettingsViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    var loggedInUserViewModel: UserViewModel? = null

    val lockTimeout: MutableLiveData<Int?>?
        get() = loggedInUserViewModel?.lockTimeoutSetting

    val generateBiometricUnlockKeyViewModel = DefaultRequestSendingViewModel()
    val enableBiometricUnlockKeyViewModel = DefaultRequestSendingViewModel()
    val disableBiometricUnlockKeyViewModel = DefaultRequestSendingViewModel()

    private var setupBiometricUnlockKeyJob: Job? = null

    fun generateBiometricUnlockKey() {
        setupBiometricUnlockKeyJob?.cancel()
        setupBiometricUnlockKeyJob = createRequestSendingJob(generateBiometricUnlockKeyViewModel) {
            // TODO: Better remove key before?
            Biometrics.generateKey(UserViewModel.BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME)
        }
    }

    fun enableBiometricUnlock(initializedMasterPasswordEncryptionCipher: Cipher, masterPassword: String) {
        setupBiometricUnlockKeyJob?.cancel()
        setupBiometricUnlockKeyJob = createRequestSendingJob(enableBiometricUnlockKeyViewModel) {
            loggedInUserViewModel?.enableBiometricUnlock(initializedMasterPasswordEncryptionCipher, masterPassword)
        }
    }

    fun disableBiometricUnlock() {
        setupBiometricUnlockKeyJob?.cancel()
        setupBiometricUnlockKeyJob = createRequestSendingJob(disableBiometricUnlockKeyViewModel) {
            loggedInUserViewModel?.disableBiometricUnlock()
        }
    }
}
