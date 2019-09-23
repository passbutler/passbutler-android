package de.sicherheitskritisch.passbutler

import android.app.Application
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.NonNullValueGetterLiveData
import de.sicherheitskritisch.passbutler.base.createRequestSendingJob
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import de.sicherheitskritisch.passbutler.crypto.Biometrics
import kotlinx.coroutines.Job
import javax.crypto.Cipher

class SettingsViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    var loggedInUserViewModel: UserViewModel? = null

    var automaticLockTimeoutSetting: String
        get() {
            val internalValue = loggedInUserViewModel?.automaticLockTimeout?.value
            return automaticLockTimeoutSettingValues.firstOrNull { it.internalValue == internalValue }?.listPreferenceValue ?: ""
        }
        set(value) {
            // Do not set `null` if setting value had no result
            automaticLockTimeoutSettingValues.firstOrNull { it.listPreferenceValue == value }?.internalValue?.let { internalValue ->
                loggedInUserViewModel?.automaticLockTimeout?.value = internalValue
            }
        }

    val automaticLockTimeoutSettingValues = listOf(
        AutomaticLockTimeoutSettingMapping(R.string.settings_automatic_lock_timeout_setting_value_0s, 0),
        AutomaticLockTimeoutSettingMapping(R.string.settings_automatic_lock_timeout_setting_value_15s, 15),
        AutomaticLockTimeoutSettingMapping(R.string.settings_automatic_lock_timeout_setting_value_30s, 30),
        AutomaticLockTimeoutSettingMapping(R.string.settings_automatic_lock_timeout_setting_value_60s, 60)
    )

    var hidePasswordsEnabledSetting: Boolean
        get() {
            return loggedInUserViewModel?.hidePasswordsEnabled?.value ?: false
        }
        set(value) {
            loggedInUserViewModel?.hidePasswordsEnabled?.value = value
        }

    val biometricUnlockEnabled: NonNullValueGetterLiveData<Boolean>?
        get() = loggedInUserViewModel?.biometricUnlockEnabled

    val biometricUnlockEnabledSetting: Boolean
        get() = biometricUnlockEnabled?.value ?: false

    val generateBiometricUnlockKeyViewModel = DefaultRequestSendingViewModel()
    val cancelSetupBiometricUnlockKeyViewModel = DefaultRequestSendingViewModel()
    val enableBiometricUnlockKeyViewModel = DefaultRequestSendingViewModel()
    val disableBiometricUnlockKeyViewModel = DefaultRequestSendingViewModel()

    private var setupBiometricUnlockKeyJob: Job? = null

    // TODO: Make non-suspend
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

class AutomaticLockTimeoutSettingMapping(
    userFacingStringResource: Int,
    internalValue: Int
) : ListPreferenceMapping<Int>(userFacingStringResource, internalValue, { internalValue.toString() })

open class ListPreferenceMapping<T : Any>(val userFacingStringResource: Int, val internalValue: T, private val listPreferenceValueConverter: (T) -> String) {
    val listPreferenceValue: String
        get() = listPreferenceValueConverter(internalValue)
}

fun <ItemType : Any, MappingType : ListPreferenceMapping<ItemType>> List<MappingType>.userFacingStrings(stringResourceResolver: (Int) -> String): Array<String> {
    return this.map { stringResourceResolver(it.userFacingStringResource) }.toTypedArray()
}

val <ItemType : Any, MappingType : ListPreferenceMapping<ItemType>> List<MappingType>.listPreferenceEntryValues: Array<String>
    get() = this.map { it.listPreferenceValue }.toTypedArray()