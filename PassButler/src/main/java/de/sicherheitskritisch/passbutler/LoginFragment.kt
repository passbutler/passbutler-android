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
import androidx.lifecycle.ViewModelProviders
import de.sicherheitskritisch.passbutler.base.BuildType
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewHandler
import de.sicherheitskritisch.passbutler.base.FormFieldValidator
import de.sicherheitskritisch.passbutler.base.FormValidationResult
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.RequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.observe
import de.sicherheitskritisch.passbutler.base.validateForm
import de.sicherheitskritisch.passbutler.database.AuthWebservice
import de.sicherheitskritisch.passbutler.databinding.FragmentLoginBinding
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.Keyboard
import de.sicherheitskritisch.passbutler.ui.VisibilityHideMode
import de.sicherheitskritisch.passbutler.ui.showFadeInOutAnimation
import de.sicherheitskritisch.passbutler.ui.showFragmentAsFirstScreen
import kotlinx.coroutines.launch
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
        viewModel.isLocalLogin.observe(this) { isLocalLoginValue ->
            val shouldShowServerUrl = !isLocalLoginValue
            binding.textInputLayoutServerurl.showFadeInOutAnimation(shouldShowServerUrl, VisibilityHideMode.INVISIBLE)
        }
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
                    binding.textInputEditTextServerurl, listOfNotNull(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.login_serverurl_validation_error_empty)),
                        FormFieldValidator.Rule({ !URLUtil.isValidUrl(it) }, getString(R.string.login_serverurl_validation_error_invalid)),
                        FormFieldValidator.Rule({ !URLUtil.isHttpsUrl(it) }, getString(R.string.login_serverurl_validation_error_invalid_scheme)).takeIf { BuildType.isReleaseBuild }
                    )
                ).takeIf { !viewModel.isLocalLogin.value },
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
                // Remove focus and hide keyboard before unlock
                removeFormFieldsFocus()
                Keyboard.hideKeyboard(context, this)

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
        fragmentWeakReference: WeakReference<LoginFragment>
    ) : DefaultRequestSendingViewHandler<LoginFragment>(requestSendingViewModel, fragmentWeakReference) {

        override fun requestErrorMessageResourceId(requestError: Throwable): Int {
            val loginFailedExceptionCause = (requestError as? UserManager.LoginFailedException)?.cause

            return when (loginFailedExceptionCause) {
                is AuthWebservice.GetAuthTokenFailedException -> R.string.login_failed_unauthorized_title
                else -> R.string.login_failed_general_title
            }
        }

        override fun onRequestFinishedSuccessfully() {
            fragment?.launch {
                val overviewFragment = OverviewFragment.newInstance()
                fragment?.showFragmentAsFirstScreen(overviewFragment)
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
