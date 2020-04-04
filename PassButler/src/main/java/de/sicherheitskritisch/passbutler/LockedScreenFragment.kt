package de.sicherheitskritisch.passbutler

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricConstants.ERROR_CANCELED
import androidx.biometric.BiometricConstants.ERROR_NEGATIVE_BUTTON
import androidx.biometric.BiometricConstants.ERROR_USER_CANCELED
import androidx.biometric.BiometricPrompt
import androidx.databinding.DataBindingUtil
import de.sicherheitskritisch.passbutler.base.BuildType
import de.sicherheitskritisch.passbutler.base.DebugConstants
import de.sicherheitskritisch.passbutler.base.Failure
import de.sicherheitskritisch.passbutler.base.FormFieldValidator
import de.sicherheitskritisch.passbutler.base.FormValidationResult
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.Success
import de.sicherheitskritisch.passbutler.base.launchRequestSending
import de.sicherheitskritisch.passbutler.base.validateForm
import de.sicherheitskritisch.passbutler.crypto.BiometricAuthenticationCallbackExecutor
import de.sicherheitskritisch.passbutler.databinding.FragmentLockedScreenBinding
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.Keyboard
import de.sicherheitskritisch.passbutler.ui.showError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

class LockedScreenFragment : BaseViewModelFragment<RootViewModel>() {

    private var formPassword: String? = null

    private var binding: FragmentLockedScreenBinding? = null

    private var unlockRequestSendingJob: Job? = null

    private val biometricCallbackExecutor by lazy {
        BiometricAuthenticationCallbackExecutor(this, Dispatchers.Main)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        activity?.let {
            viewModel = getRootViewModel(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        formPassword = savedInstanceState?.getString(FORM_FIELD_PASSWORD)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate<FragmentLockedScreenBinding>(inflater, R.layout.fragment_locked_screen, container, false).also { binding ->
            binding.lifecycleOwner = viewLifecycleOwner
            binding.fragment = this
            binding.userViewModel = viewModel.loggedInUserViewModel

            applyRestoredViewStates(binding)
        }

        return binding?.root
    }

    private fun applyRestoredViewStates(binding: FragmentLockedScreenBinding) {
        formPassword?.let { binding.textInputEditTextPassword.setText(it) }
    }

    override fun onStart() {
        super.onStart()

        binding?.let {
            setupDebugPresetsButton(it)
            setupUnlockWithPasswordButton(it)
            setupUnlockWithBiometricsButton(it)
        }
    }

    private fun setupDebugPresetsButton(binding: FragmentLockedScreenBinding) {
        if (BuildType.isDebugBuild) {
            binding.imageViewLogo.setOnLongClickListener {
                binding.textInputEditTextPassword.setText(DebugConstants.TEST_PASSWORD)
                true
            }
        }
    }

    private fun setupUnlockWithPasswordButton(binding: FragmentLockedScreenBinding) {
        binding.buttonUnlockPassword.setOnClickListener {
            unlockWithPasswordClicked(binding)
        }
    }

    private fun unlockWithPasswordClicked(binding: FragmentLockedScreenBinding) {
        val formValidationResult = validateForm(
            listOf(
                FormFieldValidator(
                    binding.textInputLayoutPassword, binding.textInputEditTextPassword, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.locked_screen_password_validation_error_empty))
                    )
                )
            )
        )

        when (formValidationResult) {
            is FormValidationResult.Valid -> {
                // Remove focus and hide keyboard before unlock
                removeFormFieldsFocus()
                Keyboard.hideKeyboard(context, this)

                val password = binding.textInputEditTextPassword.text?.toString()

                if (password != null) {
                    unlockRequestSendingJob?.cancel()
                    unlockRequestSendingJob = launchUnlockRequestSending {
                        viewModel.unlockScreenWithPassword(password)
                    }
                }
            }
            is FormValidationResult.Invalid -> {
                formValidationResult.firstInvalidFormField.requestFocus()
            }
        }
    }

    private fun setupUnlockWithBiometricsButton(binding: FragmentLockedScreenBinding) {
        binding.buttonUnlockBiometric.setOnClickListener {
            unlockWithBiometricsClicked()
        }
    }

    private fun unlockWithBiometricsClicked() {
        // Remove focus and hide keyboard before showing biometrics prompt
        removeFormFieldsFocus()
        Keyboard.hideKeyboard(context, this)

        showBiometricPrompt()
    }

    private fun showBiometricPrompt() {
        launch {
            val initializedBiometricUnlockCipherResult = viewModel.initializeBiometricUnlockCipher()

            when (initializedBiometricUnlockCipherResult) {
                is Success -> {
                    activity?.let { activity ->
                        val biometricAuthenticationCallback = BiometricAuthenticationCallback()
                        val biometricPrompt = BiometricPrompt(activity, biometricCallbackExecutor, biometricAuthenticationCallback)
                        val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle(getString(R.string.locked_screen_biometrics_prompt_title))
                            .setDescription(getString(R.string.locked_screen_biometrics_prompt_description))
                            .setNegativeButtonText(getString(R.string.locked_screen_biometrics_prompt_cancel_button_text))
                            .build()

                        val initializedBiometricUnlockCipher = initializedBiometricUnlockCipherResult.result
                        val cryptoObject = BiometricPrompt.CryptoObject(initializedBiometricUnlockCipher)
                        biometricPrompt.authenticate(biometricPromptInfo, cryptoObject)
                    }
                }
                is Failure -> {
                    Logger.warn(initializedBiometricUnlockCipherResult.throwable, "The biometric authentication failed")
                    showError(getString(R.string.locked_screen_biometrics_unlock_failed_missing_key_title))
                }
            }
        }
    }

    private fun removeFormFieldsFocus() {
        binding?.constraintLayoutRootContainer?.requestFocus()
    }

    override fun onResume() {
        super.onResume()

        // The states may changed when user had the app in the background
        viewModel.loggedInUserViewModel?.biometricUnlockAvailable?.notifyChange()
        viewModel.loggedInUserViewModel?.biometricUnlockEnabled?.notifyChange()
    }

    override fun onStop() {
        // Always hide keyboard if fragment gets stopped
        Keyboard.hideKeyboard(context, this)

        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(FORM_FIELD_PASSWORD, binding?.textInputEditTextPassword?.text?.toString())

        super.onSaveInstanceState(outState)
    }

    override fun onHandleBackPress(): Boolean {
        // Do not allow pop fragment via backpress
        return true
    }

    private inner class BiometricAuthenticationCallback : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            Logger.debug("The authentication failed with errorCode = $errorCode, errString = '$errString'")

            // If the user canceled or dismissed the dialog or if the dialog was dismissed via on pause, do not show error
            if (errorCode != ERROR_NEGATIVE_BUTTON && errorCode != ERROR_USER_CANCELED && errorCode != ERROR_CANCELED) {
                showError(getString(R.string.locked_screen_biometrics_unlock_failed_general_title))
            }
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            Logger.debug("The authentication succeeded with result = $result")

            val initializedBiometricUnlockCipher = result.cryptoObject?.cipher

            if (initializedBiometricUnlockCipher != null) {
                unlockRequestSendingJob?.cancel()
                unlockRequestSendingJob = launchUnlockRequestSending {
                    viewModel.unlockScreenWithBiometrics(initializedBiometricUnlockCipher)
                }
            } else {
                showError(getString(R.string.locked_screen_biometrics_unlock_failed_general_title))
            }
        }

        override fun onAuthenticationFailed() {
            // Don't do anything more, the prompt shows error
            Logger.debug("The authentication failed")
        }
    }

    companion object {
        private const val FORM_FIELD_PASSWORD = "FORM_FIELD_PASSWORD"

        fun newInstance() = LockedScreenFragment()
    }
}

private fun LockedScreenFragment.launchUnlockRequestSending(
    block: suspend () -> Result<*>
): Job {
    return launchRequestSending(
        handleSuccess = { popBackstack() },
        handleFailure = {
            val errorStringResourceId = when (it) {
                is DecryptMasterEncryptionKeyFailedException -> R.string.locked_screen_unlock_failed_wrong_master_password_title
                else -> R.string.locked_screen_unlock_failed_general_title
            }

            showError(getString(errorStringResourceId))
        }
    ) {
        block()
    }
}