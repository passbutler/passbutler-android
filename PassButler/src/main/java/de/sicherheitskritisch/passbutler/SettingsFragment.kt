package de.sicherheitskritisch.passbutler

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.biometric.BiometricPrompt
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewHandler
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.RequestSendingViewModel
import de.sicherheitskritisch.passbutler.crypto.BiometricAuthenticationCallbackExecutor
import de.sicherheitskritisch.passbutler.crypto.Biometrics
import de.sicherheitskritisch.passbutler.databinding.FragmentSettingsBinding
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.SimpleOnSeekBarChangeListener
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
            viewModel.loggedInUserViewModel = rootViewModel.loggedInUserViewModel
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.binding = DataBindingUtil.inflate<FragmentSettingsBinding>(inflater, R.layout.fragment_settings, container, false).also { binding ->
            binding.lifecycleOwner = this
            binding.viewModel = viewModel

            setupLockTimeoutSeekBar(binding)
            setupBiometricsUnlockSetupButton(binding)
        }

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

    private fun setupLockTimeoutSeekBar(binding: FragmentSettingsBinding) {
        binding.seekBarSettingLocktimeout.apply {
            max = 5
            progress = viewModel.lockTimeout?.value ?: 0

            setOnSeekBarChangeListener(object : SimpleOnSeekBarChangeListener() {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    // Manually update value (in this callback, the value is not written to the viewmodel)
                    binding.textViewSettingLocktimeoutValue.text = progress.toString()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.progress?.let { newProgress ->
                        viewModel.lockTimeout?.value = newProgress
                    }
                }
            })
        }
    }

    private fun setupBiometricsUnlockSetupButton(binding: FragmentSettingsBinding) {
        binding.buttonSetupBiometricUnlock.setOnClickListener {
            viewModel.generateBiometricUnlockKey()
        }
    }

    private fun showBiometricPrompt() {
        launch(Dispatchers.IO) {
            try {
                val masterPasswordEncryptionKeyCipher = Biometrics.obtainKeyInstance()
                Biometrics.initializeKeyForEncryption(UserViewModel.BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME, masterPasswordEncryptionKeyCipher)

                withContext(Dispatchers.Main) {
                    activity?.let { activity ->
                        val biometricAuthenticationCallback = BiometricAuthenticationCallback()
                        val biometricPrompt = BiometricPrompt(activity, biometricCallbackExecutor, biometricAuthenticationCallback)
                        val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle(getString(R.string.settings_setup_biometric_unlock_biometrics_prompt_title))
                            .setSubtitle(getString(R.string.settings_setup_biometric_unlock_biometrics_prompt_subtitle))
                            .setNegativeButtonText(getString(R.string.settings_setup_biometric_unlock_biometrics_prompt_cancel_button_text))
                            .build()

                        val cryptoObject = BiometricPrompt.CryptoObject(masterPasswordEncryptionKeyCipher)
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

        override fun requestErrorMessageResourceId(requestError: Throwable) = R.string.settings_setup_biometric_unlock_failed_general_title

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

            val initializedMasterPasswordEncryptionCipher = result.cryptoObject?.cipher

            if (initializedMasterPasswordEncryptionCipher != null) {
                // TODO: Remove hardcoded password
                viewModel.enableBiometricUnlock(initializedMasterPasswordEncryptionCipher, "1234")
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
