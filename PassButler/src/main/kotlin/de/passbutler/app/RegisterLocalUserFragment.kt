package de.passbutler.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import androidx.fragment.app.activityViewModels
import de.passbutler.app.base.BuildInformationProvider
import de.passbutler.app.base.DebugConstants
import de.passbutler.app.databinding.FragmentRegisterLocalUserBinding
import de.passbutler.app.ui.FormFieldValidator
import de.passbutler.app.ui.FormValidationResult
import de.passbutler.app.ui.Keyboard
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.app.ui.onActionDone
import de.passbutler.app.ui.validateForm
import de.passbutler.common.DecryptMasterEncryptionKeyFailedException
import de.passbutler.common.base.BuildType
import de.passbutler.common.database.RequestConflictedException
import de.passbutler.common.database.RequestForbiddenException
import de.passbutler.common.database.RequestUnauthorizedException
import de.passbutler.common.ui.RequestSending
import de.passbutler.common.ui.launchRequestSending

class RegisterLocalUserFragment : ToolBarFragment(), RequestSending {

    private val viewModel by userViewModelUsingViewModels<RegisterLocalUserViewModel>(userViewModelProvidingViewModel = { userViewModelProvidingViewModel })
    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

    private var binding: FragmentRegisterLocalUserBinding? = null

    override fun getToolBarTitle() = getString(R.string.register_local_user_title)

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentRegisterLocalUserBinding.inflate(inflater, container, false).also { binding ->
            setupDebugPresetsButton(binding)
            setupInputFields(binding)
            setupRegisterButton(binding)
        }

        return binding?.root
    }

    private fun setupDebugPresetsButton(binding: FragmentRegisterLocalUserBinding) {
        if (BuildInformationProvider.buildType == BuildType.Debug) {
            binding.textViewHeadline.setOnLongClickListener {
                binding.textInputEditTextServerurl.setText(DebugConstants.TEST_SERVERURL)
                binding.textInputEditTextInvitationCode.setText(DebugConstants.TEST_INVITATION_CODE)
                binding.textInputEditTextMasterPassword.setText(DebugConstants.TEST_PASSWORD)
                true
            }
        }
    }

    private fun setupInputFields(binding: FragmentRegisterLocalUserBinding) {
        binding.textInputEditTextMasterPassword.onActionDone {
            registerClicked(binding)
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
                        FormFieldValidator.Rule({ it.isNullOrEmpty() }, getString(R.string.form_serverurl_validation_error_empty)),
                        FormFieldValidator.Rule({ !URLUtil.isNetworkUrl(it) }, getString(R.string.form_serverurl_validation_error_invalid)),
                        FormFieldValidator.Rule({ !URLUtil.isHttpsUrl(it) }, getString(R.string.form_serverurl_validation_error_invalid_scheme))
                            .takeIf { BuildInformationProvider.buildType == BuildType.Release }
                    )
                ),
                FormFieldValidator(
                    binding.textInputLayoutInvitationCode, binding.textInputEditTextInvitationCode, listOf(
                        FormFieldValidator.Rule({ it.isNullOrEmpty() }, getString(R.string.register_local_user_invitation_code_validation_error_empty))
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
                showInformation(getString(R.string.register_local_user_successful_message))
                popBackstack()
            },
            handleFailure = {
                val errorStringResourceId = when (it) {
                    is DecryptMasterEncryptionKeyFailedException -> R.string.register_local_user_failed_wrong_master_password_title
                    is RequestUnauthorizedException -> R.string.register_local_user_failed_unauthorized_title
                    is RequestForbiddenException -> R.string.register_local_user_failed_forbidden_title
                    is RequestConflictedException -> R.string.register_local_user_failed_username_existing_title
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

    override fun onStop() {
        Keyboard.hideKeyboard(context, this)
        super.onStop()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = RegisterLocalUserFragment()
    }
}
