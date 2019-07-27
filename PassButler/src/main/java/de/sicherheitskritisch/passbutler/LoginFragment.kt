package de.sicherheitskritisch.passbutler

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.snackbar.Snackbar
import de.sicherheitskritisch.passbutler.base.BuildType
import de.sicherheitskritisch.passbutler.base.FormFieldValidator
import de.sicherheitskritisch.passbutler.base.FormValidationResult
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.RequestSendingViewHandler
import de.sicherheitskritisch.passbutler.base.RequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.validateForm
import de.sicherheitskritisch.passbutler.databinding.FragmentLoginBinding
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.Keyboard
import de.sicherheitskritisch.passbutler.ui.VisibilityHideMode
import de.sicherheitskritisch.passbutler.ui.showFadeInOutAnimation
import de.sicherheitskritisch.passbutler.ui.showFragmentAsFirstScreen
import java.lang.ref.WeakReference

class LoginFragment : BaseViewModelFragment<LoginViewModel>(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.SLIDE

    private var binding: FragmentLoginBinding? = null
    private var loginRequestSendingViewHandler: LoginRequestSendingViewHandler? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        viewModel = ViewModelProviders.of(this).get(LoginViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        L.d("LoginFragment", "onCreate(): savedInstanceState = $savedInstanceState")

        loginRequestSendingViewHandler = LoginRequestSendingViewHandler(viewModel.loginRequestSendingViewModel, WeakReference(this)).apply {
            registerObservers()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate<FragmentLoginBinding>(inflater, R.layout.fragment_login, container, false).also { binding ->
            binding.lifecycleOwner = this
            binding.viewModel = viewModel

            restoreSavedInstance(binding, savedInstanceState)
            setupDebugLoginPresetsButton(binding)
            setupLocalLoginCheckbox(binding)
            setupLoginButton(binding)
        }

        return binding?.root
    }

    private fun restoreSavedInstance(binding: FragmentLoginBinding, savedInstanceState: Bundle?) {
        savedInstanceState?.getString(FORM_FIELD_SERVERURL)?.let { binding.textInputEditTextServerurl.setText(it) }
        savedInstanceState?.getString(FORM_FIELD_USERNAME)?.let { binding.textInputEditTextUsername.setText(it) }
        savedInstanceState?.getString(FORM_FIELD_PASSWORD)?.let { binding.textInputEditTextPassword.setText(it) }
    }

    @SuppressLint("SetTextI18n")
    private fun setupDebugLoginPresetsButton(binding: FragmentLoginBinding) {
        if (BuildType.isDebugBuild) {
            binding.imageViewLogo.setOnLongClickListener {
                binding.textInputEditTextServerurl.setText("http://10.0.0.20:5000")
                binding.textInputEditTextUsername.setText("testuser")
                binding.textInputEditTextPassword.setText("1234")
                binding.checkBoxLocalLogin.isChecked = false
                true
            }
        }
    }

    private fun setupLocalLoginCheckbox(binding: FragmentLoginBinding) {
        viewModel.isLocalLogin.observe(this, Observer { isLocalLoginValue ->
            val shouldShowServerUrl = isLocalLoginValue == false
            binding.textInputLayoutServerurl.showFadeInOutAnimation(shouldShowServerUrl, VisibilityHideMode.INVISIBLE)
        })
    }

    private fun setupLoginButton(binding: FragmentLoginBinding) {
        binding.buttonLogin.setOnClickListener {
            loginClicked(binding)
        }
    }

    private fun loginClicked(binding: FragmentLoginBinding) {
        val formValidationResult = validateForm(
            listOfNotNull(
                FormFieldValidator(
                    binding.textInputEditTextServerurl, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.login_serverurl_validation_error_empty)),
                        FormFieldValidator.Rule({ !URLUtil.isValidUrl(it) }, getString(R.string.login_serverurl_validation_error_invalid))
                    )
                ).takeIf { viewModel.isLocalLogin.value != true },
                FormFieldValidator(
                    binding.textInputEditTextUsername, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.login_username_validation_error_empty))
                    )
                ),
                FormFieldValidator(
                    binding.textInputEditTextPassword, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.login_password_validation_error_empty))
                    )
                )
            )
        )

        when (formValidationResult) {
            is FormValidationResult.Valid -> {
                // Remove focus before login to be sure keyboard is hidden
                removeFormFieldsFocus()

                val serverUrl = binding.textInputEditTextServerurl.text?.toString()
                val username = binding.textInputEditTextUsername.text?.toString()
                val password = binding.textInputEditTextPassword.text?.toString()

                if (serverUrl != null && username != null && password != null) {
                    viewModel.loginUser(serverUrl, username, password)
                }
            }
            is FormValidationResult.Invalid -> {
                formValidationResult.firstInvalidFormField.requestFocus()
            }
        }
    }

    private fun removeFormFieldsFocus() {
        binding?.constraintLayoutLoginScreenContainer?.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        binding?.let {
            outState.putString(FORM_FIELD_SERVERURL, it.textInputEditTextServerurl.text?.toString())
            outState.putString(FORM_FIELD_USERNAME, it.textInputEditTextUsername.text?.toString())
            outState.putString(FORM_FIELD_PASSWORD, it.textInputEditTextPassword.text?.toString())
        }
    }

    override fun onDestroyView() {
        Keyboard.hideKeyboard(context, this)
        super.onDestroyView()
    }

    override fun onDestroy() {
        loginRequestSendingViewHandler?.unregisterObservers()
        super.onDestroy()
    }

    private class LoginRequestSendingViewHandler(
        requestSendingViewModel: RequestSendingViewModel,
        private val fragmentWeakReference: WeakReference<LoginFragment>
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
            binding?.constraintLayoutLoginScreenContainer?.let {
                val loginFailedExceptionCause = (requestError as? UserManager.LoginFailedException)?.cause
                val userfacingErrorMessage = when (loginFailedExceptionCause) {
                    is UserManager.GetAuthTokenFailedException -> {
                        resources?.getString(R.string.login_failed_unauthorized_title)
                    }
                    else -> {
                        resources?.getString(R.string.login_failed_general_title)
                    }
                }

                userfacingErrorMessage?.let { snackbarMessage ->
                    Snackbar.make(it, snackbarMessage, Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        override fun onRequestFinishedSuccessfully() {
            val overviewFragment = OverviewFragment.newInstance()
            fragment?.showFragmentAsFirstScreen(overviewFragment)
        }
    }

    companion object {
        private const val FORM_FIELD_SERVERURL = "FORM_FIELD_SERVERURL"
        private const val FORM_FIELD_USERNAME = "FORM_FIELD_USERNAME"
        private const val FORM_FIELD_PASSWORD = "FORM_FIELD_PASSWORD"

        fun newInstance() = LoginFragment()
    }
}
