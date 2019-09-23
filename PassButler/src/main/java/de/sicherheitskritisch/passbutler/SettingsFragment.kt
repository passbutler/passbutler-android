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
import androidx.lifecycle.ViewModelProviders
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewHandler
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.RequestSendingViewModel
import de.sicherheitskritisch.passbutler.crypto.BiometricAuthenticationCallbackExecutor
import de.sicherheitskritisch.passbutler.databinding.FragmentSettingsBinding
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.ToolBarFragment
import de.sicherheitskritisch.passbutler.ui.showEditTextDialog
import de.sicherheitskritisch.passbutler.ui.showError
import de.sicherheitskritisch.passbutler.ui.showInformation
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import javax.crypto.Cipher

class SettingsFragment : ToolBarFragment<SettingsViewModel>() {

    override val transitionType = AnimatedFragment.TransitionType.MODAL

    private var binding: FragmentSettingsBinding? = null
    private var settingsPreferenceFragment: SettingsPreferenceFragment? = null
    private var masterPasswordInputDialog: AlertDialog? = null

    private var generateBiometricsUnlockKeyViewHandler: GenerateBiometricsUnlockKeyViewHandler? = null
    private var cancelSetupBiometricUnlockKeyViewHandler: CancelSetupBiometricsUnlockKeyViewHandler? = null
    private var enableBiometricsUnlockKeyViewHandler: EnableBiometricsUnlockKeyViewHandler? = null
    private var disableBiometricsUnlockKeyViewHandler: DisableBiometricsUnlockKeyViewHandler? = null

    private val biometricUnlockEnabledObserver = Observer<Boolean> { newValue ->
        settingsPreferenceFragment?.enableBiometricUnlockPreference?.isChecked = newValue
    }

    private val biometricCallbackExecutor by lazy {
        BiometricAuthenticationCallbackExecutor(this)
    }

    override fun getToolBarTitle() = getString(R.string.settings_title)

    override fun onAttach(context: Context) {
        super.onAttach(context)

        activity?.let {
            // Retrieve viewmodel from activity to provide nested fragment the same instance
            viewModel = ViewModelProviders.of(it).get(SettingsViewModel::class.java)

            val rootViewModel = getRootViewModel(it)
            viewModel.loggedInUserViewModel = rootViewModel.loggedInUserViewModel
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate<FragmentSettingsBinding>(inflater, R.layout.fragment_settings, container, false).also { binding ->
            binding.lifecycleOwner = viewLifecycleOwner
        }

        settingsPreferenceFragment = SettingsPreferenceFragment.newInstance().also { settingsPreferenceFragment ->
            childFragmentManager
                .beginTransaction()
                .replace(R.id.frameLayout_settings_root, settingsPreferenceFragment)
                .commit()
        }

        generateBiometricsUnlockKeyViewHandler = GenerateBiometricsUnlockKeyViewHandler(viewModel.generateBiometricUnlockKeyViewModel, WeakReference(this)).apply {
            registerObservers()
        }

        cancelSetupBiometricUnlockKeyViewHandler = CancelSetupBiometricsUnlockKeyViewHandler(viewModel.cancelSetupBiometricUnlockKeyViewModel, WeakReference(this)).apply {
            registerObservers()
        }

        enableBiometricsUnlockKeyViewHandler = EnableBiometricsUnlockKeyViewHandler(viewModel.enableBiometricUnlockKeyViewModel, WeakReference(this)).apply {
            registerObservers()
        }

        disableBiometricsUnlockKeyViewHandler = DisableBiometricsUnlockKeyViewHandler(viewModel.disableBiometricUnlockKeyViewModel, WeakReference(this)).apply {
            registerObservers()
        }

        viewModel.biometricUnlockEnabled?.observe(viewLifecycleOwner, biometricUnlockEnabledObserver)

        return binding?.root
    }

    private fun showBiometricPrompt() {
        launch {
            try {
                val initializedSetupBiometricUnlockCipher = viewModel.initializeSetupBiometricUnlockCipher()

                activity?.let { activity ->
                    val biometricAuthenticationCallback = BiometricAuthenticationCallback()
                    val biometricPrompt = BiometricPrompt(activity, biometricCallbackExecutor, biometricAuthenticationCallback)
                    val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.settings_setup_biometric_unlock_biometrics_prompt_title))
                        .setDescription(getString(R.string.settings_setup_biometric_unlock_biometrics_prompt_description))
                        .setNegativeButtonText(getString(R.string.settings_setup_biometric_unlock_biometrics_prompt_cancel_button_text))
                        .build()

                    val cryptoObject = BiometricPrompt.CryptoObject(initializedSetupBiometricUnlockCipher)
                    biometricPrompt.authenticate(biometricPromptInfo, cryptoObject)
                }
            } catch (e: Exception) {
                L.w("SettingsFragment", "showBiometricPrompt(): The biometric authentication failed!", e)
                showError(getString(R.string.settings_setup_biometric_unlock_failed_general_title))
            }
        }
    }

    override fun onPause() {
        // Be sure all dialogs are dismissed to avoid they are shown on locked screen
        dismissPreferenceDialog()
        dismissMasterPasswordInputDialog()

        super.onPause()
    }

    private fun dismissPreferenceDialog() {
        // Dirty approach to dismiss the visible preference dialog fragment (fragment tag copied from `PreferenceFragmentCompat.DIALOG_FRAGMENT_TAG`):
        val preferenceDialogFragmentTag = "androidx.preference.PreferenceFragment.DIALOG"
        (settingsPreferenceFragment?.fragmentManager?.findFragmentByTag(preferenceDialogFragmentTag) as? DialogFragment)?.dismiss()
    }

    private fun confirmMasterPasswordInputDialog(initializedSetupBiometricUnlockCipher: Cipher, masterPassword: String) {
        viewModel.enableBiometricUnlock(initializedSetupBiometricUnlockCipher, masterPassword)
        masterPasswordInputDialog = null
    }

    private fun dismissMasterPasswordInputDialog() {
        masterPasswordInputDialog?.let {
            it.dismiss()

            L.d("SettingsFragment", "dismissMasterPasswordInputDialog(): The master password dialog was dismissed, cancel setup.")
            viewModel.cancelBiometricUnlockSetup()
        }

        masterPasswordInputDialog = null
    }

    override fun onDestroyView() {
        generateBiometricsUnlockKeyViewHandler?.unregisterObservers()
        cancelSetupBiometricUnlockKeyViewHandler?.unregisterObservers()
        enableBiometricsUnlockKeyViewHandler?.unregisterObservers()
        disableBiometricsUnlockKeyViewHandler?.unregisterObservers()
        super.onDestroyView()
    }

    private class GenerateBiometricsUnlockKeyViewHandler(
        requestSendingViewModel: RequestSendingViewModel,
        fragmentWeakReference: WeakReference<SettingsFragment>
    ) : DefaultRequestSendingViewHandler<SettingsFragment>(requestSendingViewModel, fragmentWeakReference) {

        override fun requestErrorMessageResourceId(requestError: Throwable) = R.string.settings_setup_biometric_unlock_failed_general_title

        override fun handleRequestFinishedSuccessfully() {
            fragment?.launch {
                fragment?.showBiometricPrompt()
            }
        }
    }

    private class CancelSetupBiometricsUnlockKeyViewHandler(
        requestSendingViewModel: RequestSendingViewModel,
        fragmentWeakReference: WeakReference<SettingsFragment>
    ) : DefaultRequestSendingViewHandler<SettingsFragment>(requestSendingViewModel, fragmentWeakReference) {

        override fun handleIsLoadingChanged(isLoading: Boolean) {
            // Do not show any loading progress for cancel operation
        }

        override fun requestErrorMessageResourceId(requestError: Throwable) = R.string.settings_setup_biometric_unlock_failed_general_title
    }

    private class EnableBiometricsUnlockKeyViewHandler(
        requestSendingViewModel: RequestSendingViewModel,
        fragmentWeakReference: WeakReference<SettingsFragment>
    ) : DefaultRequestSendingViewHandler<SettingsFragment>(requestSendingViewModel, fragmentWeakReference) {

        override fun requestErrorMessageResourceId(requestError: Throwable): Int {
            val enableBiometricUnlockFailedExceptionCause = (requestError as? UserViewModel.EnableBiometricUnlockFailedException)?.cause

            return when (enableBiometricUnlockFailedExceptionCause) {
                is UserViewModel.DecryptMasterEncryptionKeyFailedException -> R.string.settings_setup_biometric_unlock_failed_wrong_master_password_title
                else -> R.string.settings_setup_biometric_unlock_failed_general_title
            }
        }

        override fun handleRequestFinishedSuccessfully() {
            fragment?.launch {
                fragment?.showInformation(resources?.getString(R.string.settings_setup_biometric_unlock_successful_message))
            }
        }
    }

    private class DisableBiometricsUnlockKeyViewHandler(
        requestSendingViewModel: RequestSendingViewModel,
        fragmentWeakReference: WeakReference<SettingsFragment>
    ) : DefaultRequestSendingViewHandler<SettingsFragment>(requestSendingViewModel, fragmentWeakReference) {

        override fun requestErrorMessageResourceId(requestError: Throwable) = R.string.settings_disable_biometric_unlock_failed_general_title

        override fun handleRequestFinishedSuccessfully() {
            fragment?.launch {
                fragment?.showInformation(resources?.getString(R.string.settings_disable_biometric_unlock_successful_message))
            }
        }
    }

    private inner class BiometricAuthenticationCallback : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            L.d("SettingsFragment", "onAuthenticationError(): errorCode = $errorCode, errString = '$errString'")

            // If the operation failed, try to roll-back to avoid an uncompleted state
            L.d("SettingsFragment", "onAuthenticationError(): The biometric setup failed, cancel setup.")
            viewModel.cancelBiometricUnlockSetup()

            // If the user canceled or dismissed the dialog or if the dialog was dismissed via on pause, do not show error
            if (errorCode != ERROR_NEGATIVE_BUTTON && errorCode != ERROR_USER_CANCELED && errorCode != ERROR_CANCELED) {
                showSetupBiometricUnlockFailedError()
            }
        }

        private fun showSetupBiometricUnlockFailedError() = launch {
            showError(getString(R.string.settings_setup_biometric_unlock_failed_general_title))
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            L.d("SettingsFragment", "onAuthenticationSucceeded(): result = $result")

            val initializedSetupBiometricUnlockCipher = result.cryptoObject?.cipher

            if (initializedSetupBiometricUnlockCipher != null) {
                showMasterPasswordInputDialog(initializedSetupBiometricUnlockCipher)
            } else {
                showSetupBiometricUnlockFailedError()
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

        override fun onAuthenticationFailed() {
            // Don't do anything more, the prompt shows error
            L.d("SettingsFragment", "onAuthenticationFailed()")
        }
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}

class SettingsPreferenceFragment : PreferenceFragmentCompat() {

    var enableBiometricUnlockPreference: SwitchPreferenceCompat? = null
        private set

    private lateinit var settingsViewModel: SettingsViewModel

    override fun onAttach(context: Context) {
        super.onAttach(context)

        activity?.let {
            // Retrieve viewmodel from activity to use same instance as the parent fragment
            settingsViewModel = ViewModelProviders.of(it).get(SettingsViewModel::class.java)

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
                    true -> settingsViewModel.generateBiometricUnlockKey()
                    false -> settingsViewModel.disableBiometricUnlock()
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
                L.w("SettingsPreferenceDataStore", "getBoolean(): The setting with key = '$key' is not mapped - return default value!")
                false
            }
        }

        override fun putBoolean(key: String?, value: Boolean) {
            val settingKey = key?.let { SettingKey.valueOf(it) }

            // Only persist value if mapped setting exists for type and setter is given
            (settingsMapping[settingKey] as? Setting.Boolean)?.setter?.invoke(value) ?: run {
                L.w("SettingsPreferenceDataStore", "putBoolean(): The setting with key = '$key' is not mapped for writing - thus the value is not persisted!")
            }
        }

        override fun getString(key: String?, defValue: String?): String? {
            val settingKey = key?.let { SettingKey.valueOf(it) }
            return (settingsMapping[settingKey] as? Setting.String)?.getter?.invoke() ?: run {
                L.w("SettingsPreferenceDataStore", "getString(): The setting with key = '$key' is not mapped - return default value!")
                null
            }
        }

        override fun putString(key: String?, value: String?) {
            val settingKey = key?.let { SettingKey.valueOf(it) }

            if (value != null) {
                // Only persist value if mapped setting exists for type and setter is given
                (settingsMapping[settingKey] as? Setting.String)?.setter?.invoke(value) ?: run {
                    L.w("SettingsPreferenceDataStore", "putString(): The setting with key = '$key' is not mapped for writing - thus the value is not persisted!")
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
