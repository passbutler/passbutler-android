package de.passbutler.app

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import de.passbutler.app.base.BuildType
import de.passbutler.app.base.DebugConstants
import de.passbutler.app.base.launchRequestSending
import de.passbutler.app.database.RequestForbiddenException
import de.passbutler.app.databinding.FragmentRegisterLocalUserBinding
import de.passbutler.app.ui.FormFieldValidator
import de.passbutler.app.ui.FormValidationResult
import de.passbutler.app.ui.Keyboard
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.app.ui.showError
import de.passbutler.app.ui.showShortFeedback
import de.passbutler.app.ui.validateForm
import kotlinx.coroutines.Job

class RegisterLocalUserFragment : ToolBarFragment<RegisterLocalUserViewModel>() {

    private var formServerUrl: String? = null
    private var formMasterPassword: String? = null

    private var binding: FragmentRegisterLocalUserBinding? = null

    private var registerRequestSendingJob: Job? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)

        viewModel = ViewModelProvider(this).get(RegisterLocalUserViewModel::class.java)

        activity?.let {
            viewModel.rootViewModel = getRootViewModel(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        formServerUrl = savedInstanceState?.getString(FORM_FIELD_SERVERURL)
        formMasterPassword = savedInstanceState?.getString(FORM_FIELD_MASTER_PASSWORD)
    }

    override fun getToolBarTitle() = getString(R.string.register_local_user_title)

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate<FragmentRegisterLocalUserBinding>(inflater, R.layout.fragment_register_local_user, container, false).also { binding ->
            binding.lifecycleOwner = viewLifecycleOwner

            applyRestoredViewStates(binding)
        }

        return binding?.root
    }

    private fun applyRestoredViewStates(binding: FragmentRegisterLocalUserBinding) {
        formServerUrl?.let { binding.textInputEditTextServerurl.setText(it) }
        formMasterPassword?.let { binding.textInputEditTextMasterPassword.setText(it) }
    }

    override fun onStart() {
        super.onStart()

        binding?.let {
            setupRegisterButton(it)
            setupDebugPresetsButton(it)
        }
    }

    private fun setupRegisterButton(binding: FragmentRegisterLocalUserBinding) {
        binding.buttonRegister.setOnClickListener {
            registerClicked(binding)
        }
    }

    private fun registerClicked(binding: FragmentRegisterLocalUserBinding) {
        val formValidationResult = validateForm(
            listOfNotNull(
                FormFieldValidator(
                    binding.textInputLayoutServerurl, binding.textInputEditTextServerurl, listOfNotNull(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.form_serverurl_validation_error_empty)),
                        FormFieldValidator.Rule({ !URLUtil.isValidUrl(it) }, getString(R.string.form_serverurl_validation_error_invalid)),
                        FormFieldValidator.Rule({ !URLUtil.isHttpsUrl(it) }, getString(R.string.form_serverurl_validation_error_invalid_scheme)).takeIf { BuildType.isReleaseBuild }
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

                val serverUrl = binding.textInputEditTextServerurl.text?.toString()
                val masterPassword = binding.textInputEditTextMasterPassword.text?.toString()

                if (serverUrl != null && masterPassword != null) {
                    registerUser(serverUrl, masterPassword)
                }
            }
            is FormValidationResult.Invalid -> {
                formValidationResult.firstInvalidFormField.requestFocus()
            }
        }
    }

    private fun registerUser(serverUrl: String, masterPassword: String) {
        registerRequestSendingJob?.cancel()
        registerRequestSendingJob = launchRequestSending(
            handleSuccess = {
                showShortFeedback(getString(R.string.register_local_user_successful_message))
                popBackstack()
            },
            handleFailure = {
                val errorStringResourceId = when (it) {
                    is DecryptMasterEncryptionKeyFailedException -> R.string.register_local_user_failed_wrong_master_password_title
                    is RequestForbiddenException -> R.string.register_local_user_failed_forbidden_title
                    else -> R.string.register_local_user_failed_general_title
                }

                showError(getString(errorStringResourceId))
            }
        ) {
            viewModel.registerLocalUser(serverUrl, masterPassword)
        }
    }

    private fun removeFormFieldsFocus() {
        binding?.constraintLayoutRootContainer?.requestFocus()
    }

    private fun setupDebugPresetsButton(binding: FragmentRegisterLocalUserBinding) {
        if (BuildType.isDebugBuild) {
            binding.textViewHeader.setOnLongClickListener {
                binding.textInputEditTextServerurl.setText(DebugConstants.TEST_SERVERURL)
                binding.textInputEditTextMasterPassword.setText(DebugConstants.TEST_PASSWORD)
                true
            }
        }
    }

    override fun onStop() {
        // Always hide keyboard if fragment gets stopped
        Keyboard.hideKeyboard(context, this)

        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(FORM_FIELD_SERVERURL, binding?.textInputEditTextServerurl?.text?.toString())
        outState.putString(FORM_FIELD_MASTER_PASSWORD, binding?.textInputEditTextMasterPassword?.text?.toString())

        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val FORM_FIELD_SERVERURL = "FORM_FIELD_SERVERURL"
        private const val FORM_FIELD_MASTER_PASSWORD = "FORM_FIELD_MASTER_PASSWORD"

        fun newInstance() = RegisterLocalUserFragment()
    }
}