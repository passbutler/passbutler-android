package de.sicherheitskritisch.passbutler

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricConstants.ERROR_CANCELED
import androidx.biometric.BiometricConstants.ERROR_NEGATIVE_BUTTON
import androidx.biometric.BiometricConstants.ERROR_USER_CANCELED
import androidx.biometric.BiometricPrompt
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import de.sicherheitskritisch.passbutler.base.Failure
import de.sicherheitskritisch.passbutler.base.Success
import de.sicherheitskritisch.passbutler.base.launchRequestSending
import de.sicherheitskritisch.passbutler.crypto.BiometricAuthenticationCallbackExecutor
import de.sicherheitskritisch.passbutler.databinding.FragmentSettingsBinding
import de.sicherheitskritisch.passbutler.ui.FragmentPresenter
import de.sicherheitskritisch.passbutler.ui.ToolBarFragment
import de.sicherheitskritisch.passbutler.ui.showEditTextDialog
import de.sicherheitskritisch.passbutler.ui.showError
import de.sicherheitskritisch.passbutler.ui.showInformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger
import javax.crypto.Cipher

class SettingsFragment : ToolBarFragment<SettingsViewModel>() {

    private var binding: FragmentSettingsBinding? = null
    private var settingsPreferenceFragment: SettingsPreferenceFragment? = null
    private var biometricPrompt: BiometricPrompt? = null
    private var masterPasswordInputDialog: AlertDialog? = null

    private val biometricUnlockEnabledObserver = Observer<Boolean> { newValue ->
        settingsPreferenceFragment?.enableBiometricUnlockPreference?.isChecked = newValue
    }

    private val biometricCallbackExecutor by lazy {
        BiometricAuthenticationCallbackExecutor(this, Dispatchers.Main)
    }

    private var setupBiometricUnlockKeyJob: Job? = null

    override fun getToolBarTitle() = getString(R.string.settings_title)

    override fun onAttach(context: Context) {
        super.onAttach(context)

        activity?.let {
            // Retrieve viewmodel from activity to provide nested fragment the same instance
            viewModel = ViewModelProvider(it).get(SettingsViewModel::class.java)


            val rootViewModel = getRootViewModel(it)
            viewModel.loggedInUserViewModel = rootViewModel.loggedInUserViewModel
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate<FragmentSettingsBinding>(inflater, R.layout.fragment_settings, container, false).also { binding ->
            binding.lifecycleOwner = viewLifecycleOwner
        }

        val settingsPreferenceFragmentTag = FragmentPresenter.getFragmentTag(SettingsPreferenceFragment::class.java)
        settingsPreferenceFragment = ((childFragmentManager.findFragmentByTag(settingsPreferenceFragmentTag) as? SettingsPreferenceFragment) ?: run {
            SettingsPreferenceFragment.newInstance().also { newSettingsPreferenceFragment ->
                childFragmentManager
                    .beginTransaction()
                    .replace(R.id.frameLayout_settings_root, newSettingsPreferenceFragment, settingsPreferenceFragmentTag)
                    .commit()
            }
        }).also {
            it.settingsFragment = this
        }

        return binding?.root
    }

    override fun onStart() {
        super.onStart()

        viewModel.biometricUnlockEnabled?.observe(viewLifecycleOwner, biometricUnlockEnabledObserver)
    }

    override fun onPause() {
        // Be sure all dialogs are dismissed to avoid they are shown on locked screen
        dismissPreferenceDialog()
        dismissMasterPasswordInputDialog()
        dismissBiometricPrompt()

        super.onPause()
    }

    private fun showBiometricPrompt() {
        launch {
            val initializedSetupBiometricUnlockCipherResult = viewModel.initializeSetupBiometricUnlockCipher()

            when (initializedSetupBiometricUnlockCipherResult) {
                is Success -> {
                    activity?.let { activity ->
                        val biometricAuthenticationCallback = BiometricAuthenticationCallback()
                        biometricPrompt = BiometricPrompt(activity, biometricCallbackExecutor, biometricAuthenticationCallback).also {
                            val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
                                .setTitle(getString(R.string.settings_setup_biometric_unlock_biometrics_prompt_title))
                                .setDescription(getString(R.string.settings_setup_biometric_unlock_biometrics_prompt_description))
                                .setNegativeButtonText(getString(R.string.settings_setup_biometric_unlock_biometrics_prompt_cancel_button_text))
                                .build()

                            val initializedSetupBiometricUnlockCipher = initializedSetupBiometricUnlockCipherResult.result
                            val cryptoObject = BiometricPrompt.CryptoObject(initializedSetupBiometricUnlockCipher)
                            it.authenticate(biometricPromptInfo, cryptoObject)
                        }
                    }
                }
                is Failure -> {
                    Logger.warn(initializedSetupBiometricUnlockCipherResult.throwable, "The biometric authentication failed")
                    showError(getString(R.string.settings_setup_biometric_unlock_failed_general_title))
                }
            }
        }
    }

    private fun dismissPreferenceDialog() {
        // Dirty approach to dismiss the visible preference dialog fragment (fragment tag copied from `PreferenceFragmentCompat.DIALOG_FRAGMENT_TAG`):
        val preferenceDialogFragmentTag = "androidx.preference.PreferenceFragment.DIALOG"
        (settingsPreferenceFragment?.parentFragmentManager?.findFragmentByTag(preferenceDialogFragmentTag) as? DialogFragment)?.dismiss()
    }

    private fun dismissMasterPasswordInputDialog() {
        masterPasswordInputDialog?.let {
            it.dismiss()

            Logger.debug("The master password dialog was dismissed, cancel setup")
            cancelBiometricUnlockSetup()
        }

        masterPasswordInputDialog = null
    }

    private fun dismissBiometricPrompt() {
        biometricPrompt?.cancelAuthentication()
        biometricPrompt = null
    }


    private fun generateBiometricUnlockKey() {
        setupBiometricUnlockKeyJob?.cancel()
        setupBiometricUnlockKeyJob = launchRequestSending(
            handleSuccess = {
                showBiometricPrompt()
            },
            handleFailure = {
                showError(getString(R.string.settings_setup_biometric_unlock_failed_general_title))
            }
        ) {
            viewModel.generateBiometricUnlockKey()
        }
    }

    private fun disableBiometricUnlock() {
        setupBiometricUnlockKeyJob?.cancel()
        setupBiometricUnlockKeyJob = launchRequestSending(
            handleSuccess = {
                showInformation(getString(R.string.settings_disable_biometric_unlock_successful_message))
            },
            handleFailure = {
                showError(getString(R.string.settings_disable_biometric_unlock_failed_general_title))
            }
        ) {
            viewModel.disableBiometricUnlock()
        }
    }

    private fun cancelBiometricUnlockSetup() {
        setupBiometricUnlockKeyJob?.cancel()
        setupBiometricUnlockKeyJob = launchRequestSending(
            handleFailure = {
                showError(getString(R.string.settings_setup_biometric_unlock_failed_general_title))
            }
        ) {
            viewModel.cancelBiometricUnlockSetup()
        }
    }

    private inner class BiometricAuthenticationCallback : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            Logger.debug("The authentication failed with errorCode = $errorCode, errString = '$errString'")

            // If the operation failed, try to roll-back to reset an uncompleted state
            Logger.debug("The biometric setup failed, cancel setup")
            cancelBiometricUnlockSetup()

            // If the user canceled or dismissed the dialog or if the dialog was dismissed via on pause, do not show error
            if (errorCode != ERROR_NEGATIVE_BUTTON && errorCode != ERROR_USER_CANCELED && errorCode != ERROR_CANCELED) {
                showError(getString(R.string.settings_setup_biometric_unlock_failed_general_title))
            }
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            Logger.debug("The authentication succeeded with result = $result")

            val initializedSetupBiometricUnlockCipher = result.cryptoObject?.cipher

            if (initializedSetupBiometricUnlockCipher != null) {
                showMasterPasswordInputDialog(initializedSetupBiometricUnlockCipher)
            } else {
                showError(getString(R.string.settings_setup_biometric_unlock_failed_general_title))
            }
        }

        private fun showMasterPasswordInputDialog(initializedSetupBiometricUnlockCipher: Cipher) = launch {
            masterPasswordInputDialog = showEditTextDialog(
                title = getString(R.string.settings_setup_biometric_unlock_master_password_dialog_title),
                positiveClickListener = { editText ->
                    val masterPassword = editText.text.toString()
                    confirmMasterPasswordInputDialog(initializedSetupBiometricUnlockCipher, masterPassword)
                },
                negativeClickListener = {
                    dismissMasterPasswordInputDialog()
                }
            )
        }

        private fun confirmMasterPasswordInputDialog(initializedSetupBiometricUnlockCipher: Cipher, masterPassword: String) {
            setupBiometricUnlockKeyJob?.cancel()
            setupBiometricUnlockKeyJob = launchRequestSending(
                handleSuccess = {
                    showInformation(getString(R.string.settings_setup_biometric_unlock_successful_message))
                },
                handleFailure = {
                    val errorStringResourceId = when (it) {
                        is DecryptMasterEncryptionKeyFailedException -> R.string.settings_setup_biometric_unlock_failed_wrong_master_password_title
                        else -> R.string.settings_setup_biometric_unlock_failed_general_title
                    }

                    showError(getString(errorStringResourceId))
                }
            ) {
                viewModel.enableBiometricUnlock(initializedSetupBiometricUnlockCipher, masterPassword)
            }

            // Clear view instance because the dialog want properly closed, so no later dismiss is needed
            masterPasswordInputDialog = null
        }

        override fun onAuthenticationFailed() {
            // Don't do anything more, the prompt shows error
            Logger.debug("The authentication failed")
        }
    }

    class SettingsPreferenceFragment : PreferenceFragmentCompat() {

        var enableBiometricUnlockPreference: SwitchPreferenceCompat? = null
            private set

        var settingsFragment: SettingsFragment? = null

        private lateinit var settingsViewModel: SettingsViewModel

        override fun onAttach(context: Context) {
            super.onAttach(context)

            activity?.let {
                // Retrieve viewmodel from activity to use same instance as the parent fragment
                settingsViewModel = ViewModelProvider(it).get(SettingsViewModel::class.java)

                val rootViewModel = getRootViewModel(it)
                settingsViewModel.loggedInUserViewModel = rootViewModel.loggedInUserViewModel
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = SettingsPreferenceDataStore(settingsViewModel)
            preferenceScreen = preferenceManager.createPreferenceScreen(context)

            setupSecuritySettingsSection()
        }

        private fun setupSecuritySettingsSection() {
            preferenceScreen.addPreference(PreferenceCategory(context).apply {
                title = getString(R.string.settings_category_security_title)
            })

            addAutomaticLockTimeoutSetting()
            addHidePasswordsSetting()
            addBiometricUnlockSetting()
        }

        private fun addAutomaticLockTimeoutSetting() {
            preferenceScreen.addPreference(ListPreference(context).apply {
                key = SettingKey.AUTOMATIC_LOCK_TIMEOUT.name
                title = getString(R.string.settings_automatic_lock_timeout_setting_title)
                summary = getString(R.string.settings_automatic_lock_timeout_setting_summary)
                entries = settingsViewModel.automaticLockTimeoutSettingValues.userFacingStrings { getString(it) }
                entryValues = settingsViewModel.automaticLockTimeoutSettingValues.listPreferenceEntryValues
            })
        }

        private fun addHidePasswordsSetting() {
            preferenceScreen.addPreference(CheckBoxPreference(context).apply {
                key = SettingKey.HIDE_PASSWORDS_ENABLED.name
                title = getString(R.string.settings_hide_passwords_setting_title)
                summary = getString(R.string.settings_hide_passwords_setting_summary)
            })
        }

        private fun addBiometricUnlockSetting() {
            enableBiometricUnlockPreference = SwitchPreferenceCompat(context).apply {
                key = SettingKey.BIOMETRIC_UNLOCK_ENABLED.name
                title = getString(R.string.settings_biometric_unlock_setting_title)
                summary = getString(R.string.settings_biometric_unlock_setting_summary)
                isVisible = settingsViewModel.loggedInUserViewModel?.biometricUnlockAvailable?.value ?: false

                setOnPreferenceChangeListener { _, newValue ->
                    when (newValue) {
                        true -> settingsFragment?.generateBiometricUnlockKey()
                        false -> settingsFragment?.disableBiometricUnlock()
                    }

                    // Never update the preference value on switch change (this will be done programmatically after setup)
                    false
                }
            }.also {
                preferenceScreen.addPreference(it)
            }
        }

        private class SettingsPreferenceDataStore(settingsViewModel: SettingsViewModel) : PreferenceDataStore() {

            private val settingsMapping = mapOf(
                SettingKey.HIDE_PASSWORDS_ENABLED to Setting.Boolean(
                    getter = { settingsViewModel.hidePasswordsEnabledSetting },
                    setter = { settingsViewModel.hidePasswordsEnabledSetting = it }
                ),
                SettingKey.AUTOMATIC_LOCK_TIMEOUT to Setting.String(
                    getter = { settingsViewModel.automaticLockTimeoutSetting },
                    setter = { settingsViewModel.automaticLockTimeoutSetting = it }
                ),
                SettingKey.BIOMETRIC_UNLOCK_ENABLED to Setting.Boolean(
                    getter = { settingsViewModel.biometricUnlockEnabledSetting },
                    setter = null
                )
            )

            override fun getBoolean(key: String?, defValue: Boolean): Boolean {
                val settingKey = key?.let { SettingKey.valueOf(it) }
                return (settingsMapping[settingKey] as? Setting.Boolean)?.getter?.invoke() ?: run {
                    Logger.warn("The setting with key = '$key' is not mapped - return default value")
                    false
                }
            }

            override fun putBoolean(key: String?, value: Boolean) {
                val settingKey = key?.let { SettingKey.valueOf(it) }

                // Only persist value if mapped setting exists for type and setter is given
                (settingsMapping[settingKey] as? Setting.Boolean)?.setter?.invoke(value) ?: run {
                    Logger.warn("The setting with key = '$key' is not mapped for writing - thus the value is not persisted")
                }
            }

            override fun getString(key: String?, defValue: String?): String? {
                val settingKey = key?.let { SettingKey.valueOf(it) }
                return (settingsMapping[settingKey] as? Setting.String)?.getter?.invoke() ?: run {
                    Logger.warn("The setting with key = '$key' is not mapped - return default value")
                    null
                }
            }

            override fun putString(key: String?, value: String?) {
                val settingKey = key?.let { SettingKey.valueOf(it) }

                if (value != null) {
                    // Only persist value if mapped setting exists for type and setter is given
                    (settingsMapping[settingKey] as? Setting.String)?.setter?.invoke(value) ?: run {
                        Logger.warn("The setting with key = '$key' is not mapped for writing - thus the value is not persisted")
                    }
                }
            }
        }

        private enum class SettingKey {
            HIDE_PASSWORDS_ENABLED,
            AUTOMATIC_LOCK_TIMEOUT,
            BIOMETRIC_UNLOCK_ENABLED
        }

        private sealed class Setting {
            class Boolean(val getter: () -> kotlin.Boolean, val setter: ((kotlin.Boolean) -> Unit)?) : Setting()
            class String(val getter: () -> kotlin.String, val setter: ((kotlin.String) -> Unit)?) : Setting()
        }

        companion object {
            fun newInstance() = SettingsPreferenceFragment()
        }
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}