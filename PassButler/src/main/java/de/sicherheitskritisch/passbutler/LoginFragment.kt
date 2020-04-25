package de.sicherheitskritisch.passbutler

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import de.sicherheitskritisch.passbutler.base.BuildType
import de.sicherheitskritisch.passbutler.base.DebugConstants
import de.sicherheitskritisch.passbutler.ui.FormFieldValidator
import de.sicherheitskritisch.passbutler.ui.FormValidationResult
import de.sicherheitskritisch.passbutler.base.launchRequestSending
import de.sicherheitskritisch.passbutler.database.RequestUnauthorizedException
import de.sicherheitskritisch.passbutler.ui.validateForm
import de.sicherheitskritisch.passbutler.databinding.FragmentLoginBinding
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.Keyboard
import de.sicherheitskritisch.passbutler.ui.VisibilityHideMode
import de.sicherheitskritisch.passbutler.ui.showError
import de.sicherheitskritisch.passbutler.ui.showFadeInOutAnimation
import kotlinx.coroutines.Job

class LoginFragment : BaseViewModelFragment<LoginViewModel>() {

    private var formServerUrl: String? = null
    private var formUsername: String? = null
    private var formMasterPassword: String? = null

    private var binding: FragmentLoginBinding? = null

    private var loginRequestSendingJob: Job? = null

    private val isLocalLoginObserver = Observer<Boolean> { isLocalLoginValue ->
        val shouldShowServerUrl = !isLocalLoginValue
        binding?.textInputLayoutServerurl?.showFadeInOutAnimation(shouldShowServerUrl, VisibilityHideMode.INVISIBLE)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        viewModel = ViewModelProvider(this).get(LoginViewModel::class.java)

        activity?.let {
            viewModel.rootViewModel = getRootViewModel(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        formServerUrl = savedInstanceState?.getString(FORM_FIELD_SERVERURL)
        formUsername = savedInstanceState?.getString(FORM_FIELD_USERNAME)
        formMasterPassword = savedInstanceState?.getString(FORM_FIELD_MASTER_PASSWORD)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate<FragmentLoginBinding>(inflater, R.layout.fragment_login, container, false).also { binding ->
            binding.lifecycleOwner = viewLifecycleOwner
            binding.viewModel = viewModel

            applyRestoredViewStates(binding)
        }

        return binding?.root
    }

    private fun applyRestoredViewStates(binding: FragmentLoginBinding) {
        formServerUrl?.let { binding.textInputEditTextServerurl.setText(it) }
        formUsername?.let { binding.textInputEditTextUsername.setText(it) }
        formMasterPassword?.let { binding.textInputEditTextMasterPassword.setText(it) }
    }

    override fun onStart() {
        super.onStart()

        binding?.let {
            setupDebugPresetsButton(it)
            setupLocalLoginCheckbox()
            setupLoginButton(it)
        }
    }

    private fun setupDebugPresetsButton(binding: FragmentLoginBinding) {
        if (BuildType.isDebugBuild) {
            binding.imageViewLogo.setOnLongClickListener {
                binding.textInputEditTextServerurl.setText(DebugConstants.TEST_SERVERURL)
                binding.textInputEditTextUsername.setText(DebugConstants.TEST_USERNAME)
                binding.textInputEditTextMasterPassword.setText(DebugConstants.TEST_PASSWORD)
                binding.checkBoxLocalLogin.isChecked = false
                true
            }
        }
    }

    private fun setupLocalLoginCheckbox() {
        viewModel.isLocalLogin.observe(viewLifecycleOwner, isLocalLoginObserver)
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
                    binding.textInputLayoutServerurl, binding.textInputEditTextServerurl, listOfNotNull(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.form_serverurl_validation_error_empty)),
                        FormFieldValidator.Rule({ !URLUtil.isValidUrl(it) }, getString(R.string.form_serverurl_validation_error_invalid)),
                        FormFieldValidator.Rule({ !URLUtil.isHttpsUrl(it) }, getString(R.string.form_serverurl_validation_error_invalid_scheme)).takeIf { BuildType.isReleaseBuild }
                    )
                ).takeIf { !viewModel.isLocalLogin.value },
                FormFieldValidator(
                    binding.textInputLayoutUsername, binding.textInputEditTextUsername, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.login_username_validation_error_empty))
                    )
                ),
                FormFieldValidator(
                    binding.textInputLayoutMasterPassword, binding.textInputEditTextMasterPassword, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.form_master_password_validation_error_empty))
                    )
                )
            )
        )

        when (formValidationResult) {
            is FormValidationResult.Valid -> {
                // Remove focus and hide keyboard before unlock
                removeFormFieldsFocus()
                Keyboard.hideKeyboard(context, this)

                val serverUrl = binding.textInputEditTextServerurl.text?.toString()?.takeIf { !viewModel.isLocalLogin.value }
                val username = binding.textInputEditTextUsername.text?.toString()
                val masterPassword = binding.textInputEditTextMasterPassword.text?.toString()

                if (username != null && masterPassword != null) {
                    loginUser(serverUrl, username, masterPassword)
                }
            }
            is FormValidationResult.Invalid -> {
                formValidationResult.firstInvalidFormField.requestFocus()
            }
        }
    }

    private fun loginUser(serverUrl: String?, username: String, masterPassword: String) {
        loginRequestSendingJob?.cancel()
        loginRequestSendingJob = launchRequestSending(
            handleFailure = {
                val errorStringResourceId = when (it) {
                    is RequestUnauthorizedException -> R.string.login_failed_unauthorized_title
                    else -> R.string.login_failed_general_title
                }

                showError(getString(errorStringResourceId))
            },
            isCancellable = false
        ) {
            viewModel.loginUser(serverUrl, username, masterPassword)
        }
    }

    private fun removeFormFieldsFocus() {
        binding?.constraintLayoutRootContainer?.requestFocus()
    }

    override fun onStop() {
        // Always hide keyboard if fragment gets stopped
        Keyboard.hideKeyboard(context, this)

        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(FORM_FIELD_SERVERURL, binding?.textInputEditTextServerurl?.text?.toString())
        outState.putString(FORM_FIELD_USERNAME, binding?.textInputEditTextUsername?.text?.toString())
        outState.putString(FORM_FIELD_MASTER_PASSWORD, binding?.textInputEditTextMasterPassword?.text?.toString())

        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val FORM_FIELD_SERVERURL = "FORM_FIELD_SERVERURL"
        private const val FORM_FIELD_USERNAME = "FORM_FIELD_USERNAME"
        private const val FORM_FIELD_MASTER_PASSWORD = "FORM_FIELD_MASTER_PASSWORD"

        fun newInstance() = LoginFragment()
    }
}