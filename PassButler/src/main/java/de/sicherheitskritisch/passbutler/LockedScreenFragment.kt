package de.sicherheitskritisch.passbutler

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricPrompt
import androidx.databinding.DataBindingUtil
import com.google.android.material.snackbar.Snackbar
import de.sicherheitskritisch.passbutler.base.BuildType
import de.sicherheitskritisch.passbutler.base.FormFieldValidator
import de.sicherheitskritisch.passbutler.base.FormValidationResult
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.RequestSendingViewHandler
import de.sicherheitskritisch.passbutler.base.RequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.validateForm
import de.sicherheitskritisch.passbutler.crypto.Biometrics
import de.sicherheitskritisch.passbutler.databinding.FragmentLockedScreenBinding
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.Keyboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.Executor

// TODO: Put logic of to dedicated `LockedScreenViewModel` instead of `RootViewModel`?
class LockedScreenFragment : BaseViewModelFragment<RootViewModel>(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.FADE

    // TODO: Also check if master password was encrypted with biometrics
    val biometricsButtonVisible: Boolean
        get() = Biometrics.isHardwareCapable && Biometrics.hasEnrolledBiometrics

    private var binding: FragmentLockedScreenBinding? = null
    private var unlockRequestSendingViewHandler: UnlockRequestSendingViewHandler? = null

    private val biometricCallbackExecutor by lazy {
        BiometricAuthenticationCallbackExecutor()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        activity?.let {
            viewModel = getRootViewModel(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        unlockRequestSendingViewHandler = UnlockRequestSendingViewHandler(viewModel.unlockScreenRequestSendingViewModel, WeakReference(this)).apply {
            registerObservers()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate<FragmentLockedScreenBinding>(inflater, R.layout.fragment_locked_screen, container, false).also { binding ->
            binding.lifecycleOwner = this
            binding.fragment = this
            binding.viewModel = viewModel

            restoreSavedInstance(binding, savedInstanceState)
            setupDebugUnlockPresets(binding)
            setupUnlockWithPasswordButton(binding)
            setupUnlockWithBiometricsButton(binding)
        }

        return binding?.root
    }

    private fun restoreSavedInstance(binding: FragmentLockedScreenBinding, savedInstanceState: Bundle?) {
        savedInstanceState?.getString(FORM_FIELD_PASSWORD)?.let { binding.textInputEditTextPassword.setText(it) }
    }

    @SuppressLint("SetTextI18n")
    private fun setupDebugUnlockPresets(binding: FragmentLockedScreenBinding) {
        if (BuildType.isDebugBuild) {
            binding.textInputEditTextPassword.setText("1234")
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
                    binding.textInputEditTextPassword, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.locked_screen_password_validation_error_empty))
                    )
                )
            )
        )

        when (formValidationResult) {
            is FormValidationResult.Valid -> {
                // Remove focus before unlock to be sure keyboard is hidden
                removeFormFieldsFocus()

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
        binding.buttonUnlockBiometrics.setOnClickListener {
            unlockWithBiometricsClicked()
        }
    }

    private fun unlockWithBiometricsClicked() {
        // Remove focus before unlock to be sure keyboard is hidden
        removeFormFieldsFocus()

        showBiometricPrompt()
    }

    private fun showBiometricPrompt() {
        // TODO: Put business logic to viewmodel, also do not block UI thread with IO
        val masterPasswordEncryptionKeyCipher = Biometrics.obtainEncryptionKeyInstance()

        if (Biometrics.initializeEncryptionKey(masterPasswordEncryptionKeyCipher)) {
            activity?.let { activity ->
                val authenticationCallback = BiometricAuthenticationCallback()
                val biometricPrompt = BiometricPrompt(activity, biometricCallbackExecutor, authenticationCallback)

                val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.locked_screen_biometrics_prompt_title))
                    .setSubtitle(getString(R.string.locked_screen_biometrics_prompt_subtitle))
                    .setNegativeButtonText(getString(R.string.locked_screen_biometrics_prompt_cancel_button_text))
                    .build()

                val cryptoObject = BiometricPrompt.CryptoObject(masterPasswordEncryptionKeyCipher)
                biometricPrompt.authenticate(biometricPromptInfo, cryptoObject)
            }
        } else {
            // TODO: error, fingerprint not valid, must be re-added
        }
    }

    private fun removeFormFieldsFocus() {
        binding?.constraintLayoutLockedScreenContainer?.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        binding?.let {
            outState.putString(FORM_FIELD_PASSWORD, it.textInputEditTextPassword.text?.toString())
        }
    }

    override fun onDestroyView() {
        Keyboard.hideKeyboard(context, this)
        super.onDestroyView()
    }

    override fun onDestroy() {
        unlockRequestSendingViewHandler?.unregisterObservers()
        super.onDestroy()
    }

    override fun onHandleBackPress(): Boolean {
        // Do not allow pop fragment via backpress
        return true
    }

    private class UnlockRequestSendingViewHandler(
        requestSendingViewModel: RequestSendingViewModel,
        private val fragmentWeakReference: WeakReference<LockedScreenFragment>
    ) : RequestSendingViewHandler(requestSendingViewModel) {

        private val fragment
            get() = fragmentWeakReference.get()

        private val binding
            get() = fragment?.binding

        private val resources
            get() = fragment?.resources

        override fun onIsLoadingChanged(isLoading: Boolean) {
            if (isLoading) {
                fragment?.showProgress()
            } else {
                fragment?.hideProgress()
            }
        }

        override fun onRequestErrorChanged(requestError: Throwable) {
            binding?.constraintLayoutLockedScreenContainer?.let {
                resources?.getString(R.string.locked_screen_unlock_failed_general_title)?.let { snackbarMessage ->
                    Snackbar.make(it, snackbarMessage, Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        override fun onRequestFinishedSuccessfully() {
            fragment?.popBackstack()
        }
    }

    private inner class BiometricAuthenticationCallbackExecutor : Executor {
        override fun execute(runnable: Runnable) {
            launch(Dispatchers.IO) {
                runnable.run()
            }
        }
    }

    // TODO: Move to ViewModel?
    private class BiometricAuthenticationCallback : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            L.d("LockedScreenFragment", "onAuthenticationError(): errorCode = $errorCode, errString = '$errString'")
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            L.d("LockedScreenFragment", "onAuthenticationSucceeded(): result = $result")

            // TODO: Decrypt master password and unlock
            val masterPasswordEncryptionKeyCipher = result.cryptoObject?.cipher
        }

        override fun onAuthenticationFailed() {
            L.d("LockedScreenFragment", "onAuthenticationFailed()")
        }
    }

    companion object {
        private const val FORM_FIELD_PASSWORD = "FORM_FIELD_PASSWORD"

        fun newInstance() = LockedScreenFragment()
    }
}