package de.sicherheitskritisch.passbutler

import android.app.Application
import de.sicherheitskritisch.passbutler.base.Failure
import de.sicherheitskritisch.passbutler.base.NonNullValueGetterLiveData
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.Success
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import de.sicherheitskritisch.passbutler.crypto.Biometrics
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

    suspend fun initializeSetupBiometricUnlockCipher(): Result<Cipher> {
        return try {
            val biometricUnlockCipher = Biometrics.obtainKeyInstance()
            Biometrics.initializeKeyForEncryption(UserViewModel.BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME, biometricUnlockCipher)

            Success(biometricUnlockCipher)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    suspend fun generateBiometricUnlockKey(): Result<Unit> {
        return try {
            Biometrics.generateKey(UserViewModel.BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME)
            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    suspend fun enableBiometricUnlock(initializedSetupBiometricUnlockCipher: Cipher, masterPassword: String): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw IllegalStateException("The logged-in user viewmodel is null!")
        return loggedInUserViewModel.enableBiometricUnlock(initializedSetupBiometricUnlockCipher, masterPassword)
    }

    suspend fun cancelBiometricUnlockSetup(): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw IllegalStateException("The logged-in user viewmodel is null!")
        return loggedInUserViewModel.disableBiometricUnlock()
    }

    suspend fun disableBiometricUnlock(): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw IllegalStateException("The logged-in user viewmodel is null!")
        return loggedInUserViewModel.disableBiometricUnlock()
    }
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