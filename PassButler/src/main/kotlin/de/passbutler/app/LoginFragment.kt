package de.passbutler.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import androidx.fragment.app.activityViewModels
import de.passbutler.app.base.BuildInformationProvider
import de.passbutler.app.base.DebugConstants
import de.passbutler.app.databinding.FragmentLoginBinding
import de.passbutler.app.ui.BaseFragment
import de.passbutler.app.ui.FormFieldValidator
import de.passbutler.app.ui.FormValidationResult
import de.passbutler.app.ui.Keyboard
import de.passbutler.app.ui.VisibilityHideMode
import de.passbutler.app.ui.showFadeInOutAnimation
import de.passbutler.app.ui.validateForm
import de.passbutler.common.base.BuildType
import de.passbutler.common.database.RequestUnauthorizedException
import de.passbutler.common.ui.RequestSending
import de.passbutler.common.ui.launchRequestSending

class LoginFragment : BaseFragment(), RequestSending {

    private val viewModel by userViewModelUsingActivityViewModels<RootViewModel>(userViewModelProvidingViewModel = { userViewModelProvidingViewModel })
    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

    private var formServerUrl: String? = null
    private var formUsername: String? = null
    private var formMasterPassword: String? = null
    private var formLocalLogin: Boolean? = null

    private var binding: FragmentLoginBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        formServerUrl = savedInstanceState?.getString(FORM_FIELD_SERVERURL)
        formUsername = savedInstanceState?.getString(FORM_FIELD_USERNAME)
        formMasterPassword = savedInstanceState?.getString(FORM_FIELD_MASTER_PASSWORD)
        formLocalLogin = savedInstanceState?.getBoolean(FORM_FIELD_LOCAL_LOGIN)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentLoginBinding.inflate(inflater, container, false).also { binding ->
            setupDebugPresetsButton(binding)
            setupLocalLoginCheckbox(binding)
            setupLoginButton(binding)

            applyRestoredViewStates(binding)
        }

        return binding?.root
    }

    private fun setupDebugPresetsButton(binding: FragmentLoginBinding) {
        if (BuildInformationProvider.buildType == BuildType.Debug) {
            binding.imageViewLogo.setOnLongClickListener {
                binding.textInputEditTextServerurl.setText(DebugConstants.TEST_SERVERURL)
                binding.textInputEditTextUsername.setText(DebugConstants.TEST_USERNAME)
                binding.textInputEditTextMasterPassword.setText(DebugConstants.TEST_PASSWORD)
                binding.checkBoxLocalLogin.isChecked = false
                true
            }
        }
    }

    private fun setupLocalLoginCheckbox(binding: FragmentLoginBinding) {
        binding.checkBoxLocalLogin.setOnCheckedChangeListener { _, isLocalLogin ->
            val shouldShowServerUrl = !isLocalLogin
            binding.textInputLayoutServerurl.showFadeInOutAnimation(shouldShowServerUrl, VisibilityHideMode.INVISIBLE)
        }
    }

    private fun setupLoginButton(binding: FragmentLoginBinding) {
        binding.buttonLogin.setOnClickListener {
            loginClicked(binding)
        }
    }

    private fun loginClicked(binding: FragmentLoginBinding) {
        val isLocalLogin = binding.checkBoxLocalLogin.isChecked
        val formValidationResult = validateForm(
            listOfNotNull(
                FormFieldValidator(
                    binding.textInputLayoutServerurl, binding.textInputEditTextServerurl, listOfNotNull(
                        FormFieldValidator.Rule({ it.isNullOrEmpty() }, getString(R.string.form_serverurl_validation_error_empty)),
                        FormFieldValidator.Rule({ !URLUtil.isNetworkUrl(it) }, getString(R.string.form_serverurl_validation_error_invalid)),
                        FormFieldValidator.Rule({ !URLUtil.isHttpsUrl(it) }, getString(R.string.form_serverurl_validation_error_invalid_scheme))
                            .takeIf { BuildInformationProvider.buildType == BuildType.Release }
                    )
                ).takeIf { !isLocalLogin },
                FormFieldValidator(
                    binding.textInputLayoutUsername, binding.textInputEditTextUsername, listOf(
                        FormFieldValidator.Rule({ it.isNullOrEmpty() }, getString(R.string.login_username_validation_error_empty))
                    )
                ),
                FormFieldValidator(
                    binding.textInputLayoutMasterPassword, binding.textInputEditTextMasterPassword, listOf(
                        FormFieldValidator.Rule({ it.isNullOrEmpty() }, getString(R.string.form_master_password_validation_error_empty))
                    )
                )
            )
        )

        when (formValidationResult) {
            is FormValidationResult.Valid -> {
                removeFormFieldsFocus()
                Keyboard.hideKeyboard(context, this)

                val serverUrl = binding.textInputEditTextServerurl.text?.toString()?.takeIf { !isLocalLogin }
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

    private fun removeFormFieldsFocus() {
        binding?.constraintLayoutRootContainer?.requestFocus()
    }

    private fun loginUser(serverUrl: String?, username: String, masterPassword: String) {
        launchRequestSending(
            handleFailure = {
                val errorStringResourceId = when (it) {
                    is RequestUnauthorizedException -> R.string.login_failed_unauthorized_title
                    else -> R.string.login_failed_general_title
                }

                showError(getString(errorStringResourceId))
            },
            isCancellable = false
        ) {
            viewModel.loginVault(serverUrl, username, masterPassword)
        }
    }

    private fun applyRestoredViewStates(binding: FragmentLoginBinding) {
        formServerUrl?.let { binding.textInputEditTextServerurl.setText(it) }
        formUsername?.let { binding.textInputEditTextUsername.setText(it) }
        formMasterPassword?.let { binding.textInputEditTextMasterPassword.setText(it) }
        formLocalLogin?.let { binding.checkBoxLocalLogin.isChecked = it }
    }

    override fun onStop() {
        Keyboard.hideKeyboard(context, this)

        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(FORM_FIELD_SERVERURL, binding?.textInputEditTextServerurl?.text?.toString())
        outState.putString(FORM_FIELD_USERNAME, binding?.textInputEditTextUsername?.text?.toString())
        outState.putString(FORM_FIELD_MASTER_PASSWORD, binding?.textInputEditTextMasterPassword?.text?.toString())
        outState.putBoolean(FORM_FIELD_LOCAL_LOGIN, binding?.checkBoxLocalLogin?.isChecked ?: false)

        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val FORM_FIELD_SERVERURL = "FORM_FIELD_SERVERURL"
        private const val FORM_FIELD_USERNAME = "FORM_FIELD_USERNAME"
        private const val FORM_FIELD_MASTER_PASSWORD = "FORM_FIELD_MASTER_PASSWORD"
        private const val FORM_FIELD_LOCAL_LOGIN = "FORM_FIELD_LOCAL_LOGIN"

        fun newInstance() = LoginFragment()
    }
}
