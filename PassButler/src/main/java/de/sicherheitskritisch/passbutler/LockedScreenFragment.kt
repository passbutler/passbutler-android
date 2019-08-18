package de.sicherheitskritisch.passbutler

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricPrompt
import androidx.databinding.DataBindingUtil
import com.google.android.material.snackbar.Snackbar
import de.sicherheitskritisch.passbutler.UserViewModel.Companion.BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME
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
import de.sicherheitskritisch.passbutler.ui.BaseFragment
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.Keyboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.Executor

class LockedScreenFragment : BaseViewModelFragment<RootViewModel>(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.FADE

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
            binding.userViewModel = viewModel.loggedInUserViewModel

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
        binding.buttonUnlockBiometric.setOnClickListener {
            unlockWithBiometricsClicked()
        }
    }

    private fun unlockWithBiometricsClicked() {
        // Remove focus before unlock to be sure keyboard is hidden
        removeFormFieldsFocus()

        showBiometricPrompt()
    }

    private fun showBiometricPrompt() {
        launch(Dispatchers.IO) {
            try {
                val masterPasswordEncryptionKeyCipher = Biometrics.obtainKeyInstance()
                Biometrics.initializeKeyForDecryption(BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME, masterPasswordEncryptionKeyCipher)

                withContext(Dispatchers.Main) {
                    activity?.let { activity ->
                        val biometricAuthenticationCallback = BiometricAuthenticationCallback()
                        val biometricPrompt = BiometricPrompt(activity, biometricCallbackExecutor, biometricAuthenticationCallback)
                        val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle(getString(R.string.locked_screen_biometrics_prompt_title))
                            .setSubtitle(getString(R.string.locked_screen_biometrics_prompt_subtitle))
                            .setNegativeButtonText(getString(R.string.locked_screen_biometrics_prompt_cancel_button_text))
                            .build()

                        val cryptoObject = BiometricPrompt.CryptoObject(masterPasswordEncryptionKeyCipher)
                        biometricPrompt.authenticate(biometricPromptInfo, cryptoObject)
                    }
                }
            } catch (e: Exception) {
                if (e is Biometrics.InitializeKeyFailedException && e.cause is KeyPermanentlyInvalidatedException) {
                    L.d("LockedScreenFragment", "showBiometricPrompt(): The biometric authentication failed because key state is invalidated - disable biometric unlock!")
                    viewModel.loggedInUserViewModel?.disableBiometricUnlock()
                } else {
                    L.w("LockedScreenFragment", "showBiometricPrompt(): The biometric authentication failed!", e)
                }

                withContext(Dispatchers.Main) {
                    showError(binding?.root, getString(R.string.locked_screen_biometrics_unlock_failed_missing_key_title))
                }
            }
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

    private inner class BiometricAuthenticationCallbackExecutor : Executor {
        override fun execute(runnable: Runnable) {
            launch(Dispatchers.IO) {
                runnable.run()
            }
        }
    }

    private inner class BiometricAuthenticationCallback : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            L.d("LockedScreenFragment", "onAuthenticationError(): errorCode = $errorCode, errString = '$errString'")

            // TODO: Handle error here ok?
            launch {
                showError(binding?.root, getString(R.string.locked_screen_biometrics_unlock_failed_general_title))
            }
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            L.d("LockedScreenFragment", "onAuthenticationSucceeded(): result = $result")

            val masterPasswordDecryptionKey = result.cryptoObject?.cipher

            if (masterPasswordDecryptionKey != null) {
                viewModel.unlockScreenWithBiometrics(masterPasswordDecryptionKey)
            } else {
                launch {
                    showError(binding?.root, getString(R.string.locked_screen_biometrics_unlock_failed_general_title))
                }
            }
        }

        override fun onAuthenticationFailed() {
            L.d("LockedScreenFragment", "onAuthenticationFailed()")
            // Don't do anything more, the prompt shows error
        }
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
            resources?.getString(R.string.locked_screen_unlock_failed_general_title)?.let { snackbarMessage ->
                fragment?.showError(binding?.root, snackbarMessage)
            }
        }

        override fun onRequestFinishedSuccessfully() {
            fragment?.popBackstack()
        }
    }

    companion object {
        private const val FORM_FIELD_PASSWORD = "FORM_FIELD_PASSWORD"

        fun newInstance() = LockedScreenFragment()
    }
}

// TODO: Move to `BaseFragment`?
fun BaseFragment.showError(rootView: View?, errorMessage: String) {
    rootView?.let {
        Snackbar.make(it, errorMessage, Snackbar.LENGTH_SHORT).show()
    }
}
