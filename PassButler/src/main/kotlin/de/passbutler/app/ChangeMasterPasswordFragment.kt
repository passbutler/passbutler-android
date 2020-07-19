package de.passbutler.app

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import de.passbutler.app.base.launchRequestSending
import de.passbutler.app.databinding.FragmentChangeMasterPasswordBinding
import de.passbutler.app.ui.FormFieldValidator
import de.passbutler.app.ui.FormValidationResult
import de.passbutler.app.ui.Keyboard
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.app.ui.showError
import de.passbutler.app.ui.showShortFeedback
import de.passbutler.app.ui.validateForm
import de.passbutler.app.ui.visible

class ChangeMasterPasswordFragment : ToolBarFragment() {

    private val viewModel by userViewModelUsingViewModels<ChangeMasterPasswordViewModel>(userViewModelProvidingViewModel = { userViewModelProvidingViewModel })
    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

    private var formOldMasterPassword: String? = null
    private var formNewMasterPassword: String? = null
    private var formNewMasterPasswordConfirm: String? = null

    private var binding: FragmentChangeMasterPasswordBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        formOldMasterPassword = savedInstanceState?.getString(FORM_FIELD_OLD_MASTER_PASSWORD)
        formNewMasterPassword = savedInstanceState?.getString(FORM_FIELD_NEW_MASTER_PASSWORD)
        formNewMasterPasswordConfirm = savedInstanceState?.getString(FORM_FIELD_NEW_MASTER_PASSWORD_CONFIRM)
    }

    override fun getToolBarTitle() = getString(R.string.change_master_password_title)

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentChangeMasterPasswordBinding.inflate(inflater).also { binding ->
            setupTextViews(binding)
            setupChangeButton(binding)

            applyRestoredViewStates(binding)
        }

        return binding?.root
    }

    private fun setupTextViews(binding: FragmentChangeMasterPasswordBinding) {
        binding.textViewSubheaderDisableBiometricHint.visible = viewModel.loggedInUserViewModel?.biometricUnlockEnabled?.value ?: false
    }

    private fun setupChangeButton(binding: FragmentChangeMasterPasswordBinding) {
        binding.buttonChange.setOnClickListener {
            registerClicked(binding)
        }
    }

    private fun registerClicked(binding: FragmentChangeMasterPasswordBinding) {
        val formValidationResult = validateForm(
            listOfNotNull(
                FormFieldValidator(
                    binding.textInputLayoutOldMasterPassword, binding.textInputEditTextOldMasterPassword, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.change_master_password_old_master_password_validation_error_empty))
                    )
                ),
                FormFieldValidator(
                    binding.textInputLayoutNewMasterPassword, binding.textInputEditTextNewMasterPassword, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.change_master_password_new_master_password_validation_error_empty))
                    )
                ),
                FormFieldValidator(
                    binding.textInputLayoutNewMasterPasswordConfirm, binding.textInputEditTextNewMasterPasswordConfirm, listOf(
                        FormFieldValidator.Rule(
                            { binding.textInputEditTextNewMasterPassword.text.toString() != it },
                            getString(R.string.change_master_password_new_master_password_confirm_validation_error_different)
                        )
                    )
                )
            )
        )

        when (formValidationResult) {
            is FormValidationResult.Valid -> {
                removeFormFieldsFocus()
                Keyboard.hideKeyboard(context, this)

                val oldMasterPassword = binding.textInputEditTextOldMasterPassword.text?.toString()
                val newMasterPassword = binding.textInputEditTextNewMasterPassword.text?.toString()

                if (oldMasterPassword != null && newMasterPassword != null) {
                    changeMasterPassword(oldMasterPassword, newMasterPassword)
                }
            }
            is FormValidationResult.Invalid -> {
                formValidationResult.firstInvalidFormField.requestFocus()
            }
        }
    }

    private fun changeMasterPassword(oldMasterPassword: String, newMasterPassword: String) {
        launchRequestSending(
            handleSuccess = {
                showShortFeedback(getString(R.string.change_master_password_successful_message))
                popBackstack()
            },
            handleFailure = {
                val errorStringResourceId = when (it) {
                    is DecryptMasterEncryptionKeyFailedException -> R.string.change_master_password_failed_wrong_master_password_title
                    is UpdateUserFailedException -> R.string.change_master_password_failed_update_user_failed_title
                    else -> R.string.change_master_password_failed_general_title
                }

                showError(getString(errorStringResourceId))
            }
        ) {
            viewModel.changeMasterPassword(oldMasterPassword, newMasterPassword)
        }
    }

    private fun removeFormFieldsFocus() {
        binding?.constraintLayoutRootContainer?.requestFocus()
    }

    private fun applyRestoredViewStates(binding: FragmentChangeMasterPasswordBinding) {
        formOldMasterPassword?.let { binding.textInputEditTextOldMasterPassword.setText(it) }
    }

    override fun onStop() {
        Keyboard.hideKeyboard(context, this)

        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(FORM_FIELD_OLD_MASTER_PASSWORD, binding?.textInputEditTextOldMasterPassword?.text?.toString())
        outState.putString(FORM_FIELD_NEW_MASTER_PASSWORD, binding?.textInputEditTextNewMasterPassword?.text?.toString())
        outState.putString(FORM_FIELD_NEW_MASTER_PASSWORD_CONFIRM, binding?.textInputEditTextNewMasterPasswordConfirm?.text?.toString())

        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val FORM_FIELD_OLD_MASTER_PASSWORD = "FORM_FIELD_OLD_MASTER_PASSWORD"
        private const val FORM_FIELD_NEW_MASTER_PASSWORD = "FORM_FIELD_NEW_MASTER_PASSWORD"
        private const val FORM_FIELD_NEW_MASTER_PASSWORD_CONFIRM = "FORM_FIELD_NEW_MASTER_PASSWORD_CONFIRM"

        fun newInstance() = ChangeMasterPasswordFragment()
    }
}