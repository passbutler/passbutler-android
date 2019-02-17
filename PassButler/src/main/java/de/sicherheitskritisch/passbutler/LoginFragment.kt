package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import de.sicherheitskritisch.passbutler.common.FormFieldValidator
import de.sicherheitskritisch.passbutler.common.FormValidationResult
import de.sicherheitskritisch.passbutler.common.L
import de.sicherheitskritisch.passbutler.common.RequestSendingViewHandler
import de.sicherheitskritisch.passbutler.common.RequestSendingViewModel
import de.sicherheitskritisch.passbutler.common.showFadeInAnimation
import de.sicherheitskritisch.passbutler.common.validateForm
import de.sicherheitskritisch.passbutler.databinding.FragmentLoginBinding
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import java.lang.ref.WeakReference

class LoginFragment : BaseViewModelFragment<LoginViewModel>(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.SLIDE_HORIZONTAL

    private var binding: FragmentLoginBinding? = null
    private var loginRequestSendingViewHandler: LoginRequestSendingViewHandler? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        activity?.let {
            viewModel = ViewModelProviders.of(it).get(LoginViewModel::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        L.d("LoginFragment", "onCreate(): savedInstanceState = $savedInstanceState")

        loginRequestSendingViewHandler = LoginRequestSendingViewHandler(viewModel, WeakReference(this)).apply {
            registerObservers()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate<FragmentLoginBinding>(inflater, R.layout.fragment_login, container, false).also { binding ->
            binding.lifecycleOwner = this
            binding.viewModel = viewModel

            binding.imageViewLogo.setOnLongClickListener {
                loginDemoClicked(binding)
                true
            }

            binding.buttonLogin.setOnClickListener {
                loginClicked(binding)
            }

            savedInstanceState?.getString(FORM_FIELD_SERVERURL)?.let { binding.editTextServerurl.setText(it) }
            savedInstanceState?.getString(FORM_FIELD_USERNAME)?.let { binding.editTextUsername.setText(it) }
            savedInstanceState?.getString(FORM_FIELD_PASSWORD)?.let { binding.editTextPassword.setText(it) }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        binding?.let {
            outState.putString(FORM_FIELD_SERVERURL, it.editTextServerurl.text.toString())
            outState.putString(FORM_FIELD_USERNAME, it.editTextUsername.text.toString())
            outState.putString(FORM_FIELD_PASSWORD, it.editTextPassword.text.toString())
        }
    }

    override fun onDestroy() {
        loginRequestSendingViewHandler?.unregisterObservers()
        super.onDestroy()
    }

    private class LoginRequestSendingViewHandler(
        requestSendingViewModel: RequestSendingViewModel,
        private val fragmentWeakReference: WeakReference<LoginFragment>
    ) : RequestSendingViewHandler(requestSendingViewModel) {

        private val binding
            get() = fragmentWeakReference.get()?.binding

        private val resources
            get() = fragmentWeakReference.get()?.resources

        override fun onIsLoadingChanged(isLoading: Boolean) {
            /*
             * Use fade-in animation only because when the `LoginFragment` is replaced by the `RootFragment`,
             * the animation state of the progress view is reset while fragment transition is running,
             * which causes a ugly progress bar restart.
             */
            binding?.frameLayoutProgressContainer?.showFadeInAnimation(isLoading)
        }

        override fun onRequestErrorChanged(requestError: Exception) {
            binding?.constraintLayoutLoginScreenContainer?.let {
                resources?.getString(R.string.login_failed_title)?.let { snackbarMessage ->
                    Snackbar.make(it, snackbarMessage, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val FORM_FIELD_SERVERURL = "FORM_FIELD_SERVERURL"
        private const val FORM_FIELD_USERNAME = "FORM_FIELD_USERNAME"
        private const val FORM_FIELD_PASSWORD = "FORM_FIELD_PASSWORD"

        fun newInstance() = LoginFragment()
    }
}
