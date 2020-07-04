package de.passbutler.app

import de.passbutler.app.base.NonNullValueGetterLiveData
import de.passbutler.app.crypto.Biometrics
import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.resultOrThrowException
import javax.crypto.Cipher

class SettingsViewModel : UserViewModelUsingViewModel() {

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
            val biometricUnlockCipher = Biometrics.obtainKeyInstance().resultOrThrowException()
            Biometrics.initializeKeyForEncryption(UserViewModel.BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME, biometricUnlockCipher).resultOrThrowException()

            Success(biometricUnlockCipher)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    suspend fun generateBiometricUnlockKey(): Result<Unit> {
        return Biometrics.generateKey(UserViewModel.BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME)
    }

    suspend fun enableBiometricUnlock(initializedSetupBiometricUnlockCipher: Cipher, masterPassword: String): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        return loggedInUserViewModel.enableBiometricUnlock(initializedSetupBiometricUnlockCipher, masterPassword)
    }

    suspend fun cancelBiometricUnlockSetup(): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        return loggedInUserViewModel.disableBiometricUnlock()
    }

    suspend fun disableBiometricUnlock(): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
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