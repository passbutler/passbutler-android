package de.passbutler.app

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import androidx.fragment.app.activityViewModels
import de.passbutler.app.base.BuildInformationProvider
import de.passbutler.app.base.DebugConstants
import de.passbutler.app.base.launchRequestSending
import de.passbutler.app.databinding.FragmentRegisterLocalUserBinding
import de.passbutler.app.ui.FormFieldValidator
import de.passbutler.app.ui.FormValidationResult
import de.passbutler.app.ui.Keyboard
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.app.ui.showError
import de.passbutler.app.ui.showShortFeedback
import de.passbutler.app.ui.validateForm
import de.passbutler.common.DecryptMasterEncryptionKeyFailedException
import de.passbutler.common.base.BuildType
import de.passbutler.common.database.RequestForbiddenException

class RegisterLocalUserFragment : ToolBarFragment() {

    private val viewModel by userViewModelUsingViewModels<RegisterLocalUserViewModel>(userViewModelProvidingViewModel = { userViewModelProvidingViewModel })
    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

    private var formServerUrl: String? = null
    private var formInvitationCode: String? = null
    private var formMasterPassword: String? = null

    private var binding: FragmentRegisterLocalUserBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        formServerUrl = savedInstanceState?.getString(FORM_FIELD_SERVERURL)
        formInvitationCode = savedInstanceState?.getString(FORM_FIELD_INVITATION_CODE)
        formMasterPassword = savedInstanceState?.getString(FORM_FIELD_MASTER_PASSWORD)
    }

    override fun getToolBarTitle() = getString(R.string.register_local_user_title)

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentRegisterLocalUserBinding.inflate(inflater).also { binding ->
            setupRegisterButton(binding)
            setupDebugPresetsButton(binding)

            applyRestoredViewStates(binding)
        }

        return binding?.root
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
                        FormFieldValidator.Rule({ !URLUtil.isHttpsUrl(it) }, getString(R.string.form_serverurl_validation_error_invalid_scheme))
                            .takeIf { BuildInformationProvider.buildType == BuildType.Release }
                    )
                ),
                FormFieldValidator(
                    binding.textInputLayoutInvitationCode, binding.textInputEditTextInvitationCode, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.register_local_user_invitation_code_validation_error_empty))
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
                removeFormFieldsFocus()
                Keyboard.hideKeyboard(context, this)

                val serverUrl = binding.textInputEditTextServerurl.text?.toString()
                val invitationCode = binding.textInputEditTextInvitationCode.text?.toString()
                val masterPassword = binding.textInputEditTextMasterPassword.text?.toString()

                if (serverUrl != null && invitationCode != null && masterPassword != null) {
                    registerUser(serverUrl, invitationCode, masterPassword)
                }
            }
            is FormValidationResult.Invalid -> {
                formValidationResult.firstInvalidFormField.requestFocus()
            }
        }
    }

    private fun registerUser(serverUrl: String, invitationCode: String, masterPassword: String) {
        launchRequestSending(
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
            viewModel.registerLocalUser(serverUrl, invitationCode, masterPassword)
        }
    }

    private fun removeFormFieldsFocus() {
        binding?.constraintLayoutRootContainer?.requestFocus()
    }

    private fun setupDebugPresetsButton(binding: FragmentRegisterLocalUserBinding) {
        if (BuildInformationProvider.buildType == BuildType.Debug) {
            binding.textViewHeader.setOnLongClickListener {
                binding.textInputEditTextServerurl.setText(DebugConstants.TEST_SERVERURL)
                binding.textInputEditTextInvitationCode.setText(DebugConstants.TEST_INVITATION_CODE)
                binding.textInputEditTextMasterPassword.setText(DebugConstants.TEST_PASSWORD)
                true
            }
        }
    }

    private fun applyRestoredViewStates(binding: FragmentRegisterLocalUserBinding) {
        formServerUrl?.let { binding.textInputEditTextServerurl.setText(it) }
        formInvitationCode?.let { binding.textInputEditTextInvitationCode.setText(it) }
        formMasterPassword?.let { binding.textInputEditTextMasterPassword.setText(it) }
    }

    override fun onStop() {
        Keyboard.hideKeyboard(context, this)

        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(FORM_FIELD_SERVERURL, binding?.textInputEditTextServerurl?.text?.toString())
        outState.putString(FORM_FIELD_INVITATION_CODE, binding?.textInputEditTextInvitationCode?.text?.toString())
        outState.putString(FORM_FIELD_MASTER_PASSWORD, binding?.textInputEditTextMasterPassword?.text?.toString())

        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val FORM_FIELD_SERVERURL = "FORM_FIELD_SERVERURL"
        private const val FORM_FIELD_INVITATION_CODE = "FORM_FIELD_INVITATION_CODE"
        private const val FORM_FIELD_MASTER_PASSWORD = "FORM_FIELD_MASTER_PASSWORD"

        fun newInstance() = RegisterLocalUserFragment()
    }
}