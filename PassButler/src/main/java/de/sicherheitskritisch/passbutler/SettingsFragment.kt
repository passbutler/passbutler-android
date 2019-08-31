package de.sicherheitskritisch.passbutler

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricPrompt
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewHandler
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.RequestSendingViewModel
import de.sicherheitskritisch.passbutler.crypto.BiometricAuthenticationCallbackExecutor
import de.sicherheitskritisch.passbutler.databinding.FragmentSettingsBinding
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.ToolBarFragment
import de.sicherheitskritisch.passbutler.ui.showError
import de.sicherheitskritisch.passbutler.ui.showInformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class SettingsFragment : ToolBarFragment<SettingsViewModel>() {

    override val transitionType = AnimatedFragment.TransitionType.MODAL

    private var binding: FragmentSettingsBinding? = null

    private var generateBiometricsUnlockKeyViewHandler: GenerateBiometricsUnlockKeyViewHandler? = null
    private var enableBiometricsUnlockKeyViewHandler: EnableBiometricsUnlockKeyViewHandler? = null
    private var disableBiometricsUnlockKeyViewHandler: DisableBiometricsUnlockKeyViewHandler? = null

    private val biometricCallbackExecutor by lazy {
        BiometricAuthenticationCallbackExecutor(this)
    }

    override fun getToolBarTitle() = getString(R.string.settings_title)

    override fun onAttach(context: Context) {
        super.onAttach(context)

        viewModel = ViewModelProviders.of(this).get(SettingsViewModel::class.java)

        activity?.let {
            val rootViewModel = getRootViewModel(it)
            viewModel.loggedInUserViewModel = rootViewModel.loggedInUserViewModel!!
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.binding = DataBindingUtil.inflate<FragmentSettingsBinding>(inflater, R.layout.fragment_settings, container, false).also { binding ->
            binding.lifecycleOwner = this
        }

        // TODO: Check if existing instead of replace
        childFragmentManager
            .beginTransaction()
            .replace(R.id.frameLayout_settings_root, SettingsPreferenceFragment.newInstance(viewModel))
            .commit()

        generateBiometricsUnlockKeyViewHandler = GenerateBiometricsUnlockKeyViewHandler(viewModel.generateBiometricUnlockKeyViewModel, WeakReference(this)).apply {
            registerObservers()
        }

        enableBiometricsUnlockKeyViewHandler = EnableBiometricsUnlockKeyViewHandler(viewModel.enableBiometricUnlockKeyViewModel, WeakReference(this)).apply {
            registerObservers()
        }

        disableBiometricsUnlockKeyViewHandler = DisableBiometricsUnlockKeyViewHandler(viewModel.disableBiometricUnlockKeyViewModel, WeakReference(this)).apply {
            registerObservers()
        }

        return binding?.root
    }

    private fun showBiometricPrompt() {
        launch(Dispatchers.IO) {
            try {
                val initializedSetupBiometricUnlockCipher = viewModel.initializeSetupBiometricUnlockCipher()

                withContext(Dispatchers.Main) {
                    activity?.let { activity ->
                        val biometricAuthenticationCallback = BiometricAuthenticationCallback()
                        val biometricPrompt = BiometricPrompt(activity, biometricCallbackExecutor, biometricAuthenticationCallback)
                        val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle(getString(R.string.settings_setup_biometric_unlock_biometrics_prompt_title))
                            .setSubtitle(getString(R.string.settings_setup_biometric_unlock_biometrics_prompt_subtitle))
                            .setNegativeButtonText(getString(R.string.settings_setup_biometric_unlock_biometrics_prompt_cancel_button_text))
                            .build()

                        val cryptoObject = BiometricPrompt.CryptoObject(initializedSetupBiometricUnlockCipher)
                        biometricPrompt.authenticate(biometricPromptInfo, cryptoObject)
                    }
                }
            } catch (e: Exception) {
                L.w("SettingsFragment", "showBiometricPrompt(): The biometric authentication failed!", e)

                withContext(Dispatchers.Main) {
                    showError(getString(R.string.settings_setup_biometric_unlock_failed_general_title))
                }
            }
        }
    }

    override fun onDestroyView() {
        generateBiometricsUnlockKeyViewHandler?.unregisterObservers()
        enableBiometricsUnlockKeyViewHandler?.unregisterObservers()
        disableBiometricsUnlockKeyViewHandler?.unregisterObservers()
        super.onDestroyView()
    }

    private class GenerateBiometricsUnlockKeyViewHandler(
        requestSendingViewModel: RequestSendingViewModel,
        fragmentWeakReference: WeakReference<SettingsFragment>
    ) : DefaultRequestSendingViewHandler<SettingsFragment>(requestSendingViewModel, fragmentWeakReference) {

        override fun requestErrorMessageResourceId(requestError: Throwable) = R.string.settings_setup_biometric_unlock_failed_general_title

        override fun onRequestFinishedSuccessfully() {
            fragment?.showBiometricPrompt()
        }
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

        override fun onRequestFinishedSuccessfully() {
            fragment?.showInformation(resources?.getString(R.string.settings_setup_biometric_unlock_successful_message))
        }
    }

    private class DisableBiometricsUnlockKeyViewHandler(
        requestSendingViewModel: RequestSendingViewModel,
        fragmentWeakReference: WeakReference<SettingsFragment>
    ) : DefaultRequestSendingViewHandler<SettingsFragment>(requestSendingViewModel, fragmentWeakReference) {

        override fun requestErrorMessageResourceId(requestError: Throwable) = R.string.settings_disable_biometric_unlock_failed_general_title

        override fun onRequestFinishedSuccessfully() {
            fragment?.showInformation(resources?.getString(R.string.settings_disable_biometric_unlock_successful_message))
        }
    }

    private inner class BiometricAuthenticationCallback : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            L.d("SettingsFragment", "onAuthenticationError(): errorCode = $errorCode, errString = '$errString'")

            launch {
                showError(getString(R.string.settings_setup_biometric_unlock_failed_general_title))
            }
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            L.d("SettingsFragment", "onAuthenticationSucceeded(): result = $result")

            val initializedSetupBiometricUnlockCipher = result.cryptoObject?.cipher

            if (initializedSetupBiometricUnlockCipher != null) {
                // TODO: Remove hardcoded password
                viewModel.enableBiometricUnlock(initializedSetupBiometricUnlockCipher, "1234")
            } else {
                launch {
                    showError(getString(R.string.settings_setup_biometric_unlock_failed_general_title))
                }
            }
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

    private lateinit var settingsViewModel: SettingsViewModel

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = SettingsPreferenceDataStore()
        preferenceScreen = preferenceManager.createPreferenceScreen(context)

        setupSecuritySettingsSection()
    }

    private fun setupSecuritySettingsSection() {
        preferenceScreen.addPreference(PreferenceCategory(context).apply {
            title = getString(R.string.settings_category_security_title)
        })

        preferenceScreen.addPreference(CheckBoxPreference(context).apply {
            key = SETTING_HIDE_PASSWORDS
            title = getString(R.string.settings_hide_passwords_setting_title)
            summary = getString(R.string.settings_hide_passwords_setting_summary)
        })

        preferenceScreen.addPreference(ListPreference(context).apply {
            key = SETTING_LOCK_TIMEOUT
            title = getString(R.string.settings_lock_timeout_setting_title)
            summary = getString(R.string.settings_lock_timeout_setting_summary)
            entries = arrayOf(
                getString(R.string.settings_lock_timeout_setting_value_0s),
                getString(R.string.settings_lock_timeout_setting_value_15s),
                getString(R.string.settings_lock_timeout_setting_value_30s),
                getString(R.string.settings_lock_timeout_setting_value_60s)
            )
            entryValues = arrayOf(
                "0",
                "15",
                "30",
                "60"
            )
        })
    }

    private inner class SettingsPreferenceDataStore : PreferenceDataStore() {
        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            return when (key) {
                SETTING_HIDE_PASSWORDS -> settingsViewModel.hidePasswordsSetting.value ?: false
                else -> false
            }
        }

        override fun putBoolean(key: String?, value: Boolean) {
            when (key) {
                SETTING_HIDE_PASSWORDS -> settingsViewModel.hidePasswordsSetting.value = value
            }
        }

        override fun getInt(key: String?, defValue: Int): Int {
            return when (key) {
                SETTING_LOCK_TIMEOUT -> settingsViewModel.lockTimeout.value ?: 0
                else -> 0
            }
        }

        override fun putInt(key: String?, value: Int) {
            when (key) {
                SETTING_LOCK_TIMEOUT -> settingsViewModel.lockTimeout.value = value
            }
        }

        override fun getString(key: String?, defValue: String?): String? {
            L.w("SettingsFragment", "getString(): key = $key")
            return ""
        }

        override fun putString(key: String?, value: String?) {
            L.w("SettingsFragment", "putString(): key = $key, value = $value")
        }
    }

    companion object {
        private const val SETTING_HIDE_PASSWORDS = "hidePasswordsSetting"
        private const val SETTING_LOCK_TIMEOUT = "lockTimeoutSetting"

        fun newInstance(viewModel: SettingsViewModel) = SettingsPreferenceFragment().apply {
            settingsViewModel = viewModel
        }
    }
}

