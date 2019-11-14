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
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewHandler
import de.sicherheitskritisch.passbutler.base.FormFieldValidator
import de.sicherheitskritisch.passbutler.base.FormValidationResult
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.RequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.validateForm
import de.sicherheitskritisch.passbutler.crypto.BiometricAuthenticationCallbackExecutor
import de.sicherheitskritisch.passbutler.databinding.FragmentLockedScreenBinding
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.Keyboard
import de.sicherheitskritisch.passbutler.ui.showError
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class LockedScreenFragment : BaseViewModelFragment<RootViewModel>(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.FADE

    private var formPassword: String? = null

    private var binding: FragmentLockedScreenBinding? = null
    private var unlockRequestSendingViewHandler: UnlockRequestSendingViewHandler? = null

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

        unlockRequestSendingViewHandler = UnlockRequestSendingViewHandler(viewModel.unlockScreenRequestSendingViewModel, WeakReference(this))

        return binding?.root
    }

    private fun applyRestoredViewStates(binding: FragmentLockedScreenBinding) {
        formPassword?.let { binding.textInputEditTextPassword.setText(it) }
    }

    override fun onStart() {
        super.onStart()

        binding?.let {
            setupUnlockWithPasswordButton(it)
            setupUnlockWithBiometricsButton(it)
        }

        unlockRequestSendingViewHandler?.registerObservers()
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
                    binding.textInputEditTextPassword, listOf(
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
                    viewModel.unlockScreenWithPassword(password)
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
            try {
                val initializedBiometricUnlockCipher = viewModel.initializeBiometricUnlockCipher()

                activity?.let { activity ->
                    val biometricAuthenticationCallback = BiometricAuthenticationCallback()
                    val biometricPrompt = BiometricPrompt(activity, biometricCallbackExecutor, biometricAuthenticationCallback)
                    val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.locked_screen_biometrics_prompt_title))
                        .setDescription(getString(R.string.locked_screen_biometrics_prompt_description))
                        .setNegativeButtonText(getString(R.string.locked_screen_biometrics_prompt_cancel_button_text))
                        .build()

                    val cryptoObject = BiometricPrompt.CryptoObject(initializedBiometricUnlockCipher)
                    biometricPrompt.authenticate(biometricPromptInfo, cryptoObject)
                }

            } catch (e: Exception) {
                L.w("LockedScreenFragment", "showBiometricPrompt(): The biometric authentication failed!", e)
                showError(getString(R.string.locked_screen_biometrics_unlock_failed_missing_key_title))
            }
        }
    }

    private fun removeFormFieldsFocus() {
        binding?.constraintLayoutLockedScreenContainer?.requestFocus()
    }

    override fun onResume() {
        super.onResume()

        // The states may changed when user had the app in the background
        viewModel.updateBiometricUnlockAvailability()
    }

    override fun onStop() {
        unlockRequestSendingViewHandler?.unregisterObservers()

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
            L.d("LockedScreenFragment", "onAuthenticationError(): errorCode = $errorCode, errString = '$errString'")

            // If the user canceled or dismissed the dialog or if the dialog was dismissed via on pause, do not show error
            if (errorCode != ERROR_NEGATIVE_BUTTON && errorCode != ERROR_USER_CANCELED && errorCode != ERROR_CANCELED) {
                showError(getString(R.string.locked_screen_biometrics_unlock_failed_general_title))
            }
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            L.d("LockedScreenFragment", "onAuthenticationSucceeded(): result = $result")

            val initializedBiometricUnlockCipher = result.cryptoObject?.cipher

            if (initializedBiometricUnlockCipher != null) {
                viewModel.unlockScreenWithBiometrics(initializedBiometricUnlockCipher)
            } else {
                showError(getString(R.string.locked_screen_biometrics_unlock_failed_general_title))
            }
        }

        override fun onAuthenticationFailed() {
            // Don't do anything more, the prompt shows error
            L.d("LockedScreenFragment", "onAuthenticationFailed()")
        }
    }

    private class UnlockRequestSendingViewHandler(
        requestSendingViewModel: RequestSendingViewModel,
        fragmentWeakReference: WeakReference<LockedScreenFragment>
    ) : DefaultRequestSendingViewHandler<LockedScreenFragment>(requestSendingViewModel, fragmentWeakReference) {

        override fun requestErrorMessageResourceId(requestError: Throwable): Int {
            return when ((requestError as? UserViewModel.UnlockFailedException)?.cause) {
                is UserViewModel.DecryptMasterEncryptionKeyFailedException -> R.string.locked_screen_unlock_failed_wrong_master_password_title
                else -> R.string.locked_screen_unlock_failed_general_title
            }
        }

        override fun handleRequestFinishedSuccessfully() {
            fragment?.launch {
                fragment?.popBackstack()
            }
        }
    }

    companion object {
        private const val FORM_FIELD_PASSWORD = "FORM_FIELD_PASSWORD"

        fun newInstance() = LockedScreenFragment()
    }
}
