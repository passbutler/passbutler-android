package de.sicherheitskritisch.passbutler

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.ValueGetterLiveData
import de.sicherheitskritisch.passbutler.base.createRequestSendingJob
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import de.sicherheitskritisch.passbutler.crypto.Biometrics
import kotlinx.coroutines.Job
import javax.crypto.Cipher

class SettingsViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    // For the `SettingsViewModel` it is guaranteed that a `UserViewModel` is set, so lateinit is not problem
    lateinit var loggedInUserViewModel: UserViewModel

    val lockTimeout: MutableLiveData<Int?>
        get() = loggedInUserViewModel.lockTimeoutSetting

    val hidePasswordsSetting: MutableLiveData<Boolean?>
        get() = loggedInUserViewModel.hidePasswordsSetting

    val biometricUnlockEnabled: ValueGetterLiveData<Boolean>
        get() = loggedInUserViewModel.biometricUnlockEnabled

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
            val masterPasswordEncryptionKeyName = UserViewModel.BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME
            Biometrics.removeKey(masterPasswordEncryptionKeyName)
            Biometrics.generateKey(masterPasswordEncryptionKeyName)
        }
    }

    fun enableBiometricUnlock(initializedSetupBiometricUnlockCipher: Cipher, masterPassword: String) {
        setupBiometricUnlockKeyJob?.cancel()
        setupBiometricUnlockKeyJob = createRequestSendingJob(enableBiometricUnlockKeyViewModel) {
            loggedInUserViewModel.enableBiometricUnlock(initializedSetupBiometricUnlockCipher, masterPassword)
        }
    }

    fun cancelBiometricUnlockSetup() {
        setupBiometricUnlockKeyJob?.cancel()
        setupBiometricUnlockKeyJob = createRequestSendingJob(cancelSetupBiometricUnlockKeyViewModel) {
            val masterPasswordEncryptionKeyName = UserViewModel.BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME
            Biometrics.removeKey(masterPasswordEncryptionKeyName)
        }
    }

    fun disableBiometricUnlock() {
        setupBiometricUnlockKeyJob?.cancel()
        setupBiometricUnlockKeyJob = createRequestSendingJob(disableBiometricUnlockKeyViewModel) {
            loggedInUserViewModel.disableBiometricUnlock()
        }
    }

    class InitializeSetupBiometricUnlockCipherFailedException(cause: Throwable) : Exception(cause)
}
