package de.sicherheitskritisch.pass

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import de.sicherheitskritisch.pass.common.FormFieldValidator
import de.sicherheitskritisch.pass.common.FormValidationResult
import de.sicherheitskritisch.pass.common.RequestSendingViewHandler
import de.sicherheitskritisch.pass.common.RequestSendingViewModel
import de.sicherheitskritisch.pass.common.showFadeAnimation
import de.sicherheitskritisch.pass.common.validateForm
import de.sicherheitskritisch.pass.databinding.FragmentLoginBinding
import de.sicherheitskritisch.pass.ui.AnimatedFragment
import de.sicherheitskritisch.pass.ui.BaseViewModelFragment

class LoginFragment : BaseViewModelFragment<LoginViewModel>(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.SLIDE_HORIZONTAL

    private var binding: FragmentLoginBinding? = null
    private var loginRequestSendingViewHandler: LoginRequestSendingViewHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loginRequestSendingViewHandler = LoginRequestSendingViewHandler(viewModel).apply {
            registerObservers()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.binding = DataBindingUtil.inflate<FragmentLoginBinding>(inflater, R.layout.fragment_login, container, false).also { binding ->
            binding.setLifecycleOwner(this)
            binding.viewModel = viewModel

            binding.imageViewLogo.setOnLongClickListener {
                loginDemoClicked(binding)
                true
            }

            binding.buttonLogin.setOnClickListener {
                loginClicked(binding)
            }
        }

        return binding?.root
    }

    private fun loginDemoClicked(binding: FragmentLoginBinding) {
        // Clean form field errors first to be sure everything looks clean if the progress shows up
        listOf(binding.editTextServerurl, binding.editTextUsername, binding.editTextPassword).forEach { formField ->
            formField.error = null
        }

        // Remove focus for the same reason
        removeFormFieldsFocus()

        viewModel.loginDemoUser()
    }

    private fun loginClicked(binding: FragmentLoginBinding) {
        val formValidationResult = validateForm(
            listOf(
                FormFieldValidator(
                    binding.editTextServerurl, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.login_serverurl_validation_error_empty)),
                        FormFieldValidator.Rule({ !URLUtil.isValidUrl(it) }, getString(R.string.login_serverurl_validation_error_invalid))
                    )
                ),
                FormFieldValidator(
                    binding.editTextUsername, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.login_username_validation_error_empty))
                    )
                ),
                FormFieldValidator(
                    binding.editTextPassword, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.login_password_validation_error_empty))
                    )
                )
            )
        )

        when (formValidationResult) {
            is FormValidationResult.Valid -> {
                // Remove focus before login to be sure keyboard is hidden
                removeFormFieldsFocus()

                val serverUrl = binding.editTextServerurl.text.toString()
                val username = binding.editTextUsername.text.toString()
                val password = binding.editTextPassword.text.toString()
                viewModel.loginUser(serverUrl, username, password)
            }
            is FormValidationResult.Invalid -> {
                formValidationResult.firstInvalidFormField.requestFocus()
            }
        }
    }

    /**
     * Removes focus of all form fields.
     */
    private fun removeFormFieldsFocus() {
        binding?.constraintLayoutLoginScreenContainer?.requestFocus()
    }

    override fun onDestroy() {
        loginRequestSendingViewHandler?.unregisterObservers()
        super.onDestroy()
    }

    private inner class LoginRequestSendingViewHandler(requestSendingViewModel: RequestSendingViewModel) : RequestSendingViewHandler(requestSendingViewModel) {
        override fun onIsLoadingChanged(isLoading: Boolean) {
            binding?.frameLayoutProgressContainer?.showFadeAnimation(isLoading)
        }

        override fun onRequestErrorChanged(requestError: Exception) {
            // TODO: Show snackbar
        }
    }

    companion object {
        private const val TAG = "LoginFragment"

        fun newInstance(viewModel: LoginViewModel) = LoginFragment().also { it.viewModel = viewModel }
    }
}
