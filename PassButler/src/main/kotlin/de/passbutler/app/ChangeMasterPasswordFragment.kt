package de.passbutler.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import de.passbutler.app.databinding.FragmentChangeMasterPasswordBinding
import de.passbutler.app.ui.FormFieldValidator
import de.passbutler.app.ui.FormValidationResult
import de.passbutler.app.ui.Keyboard
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.app.ui.bindVisibility
import de.passbutler.app.ui.onActionDone
import de.passbutler.app.ui.validateForm
import de.passbutler.common.DecryptMasterEncryptionKeyFailedException
import de.passbutler.common.UpdateUserFailedException
import de.passbutler.common.ui.RequestSending
import de.passbutler.common.ui.launchRequestSending

class ChangeMasterPasswordFragment : ToolBarFragment(), RequestSending {

    private val viewModel by userViewModelUsingViewModels<ChangeMasterPasswordViewModel>(userViewModelProvidingViewModel = { userViewModelProvidingViewModel })
    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

    private var binding: FragmentChangeMasterPasswordBinding? = null

    override fun getToolBarTitle() = getString(R.string.change_master_password_title)

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentChangeMasterPasswordBinding.inflate(inflater, container, false).also { binding ->
            setupTextViews(binding)
            setupInputFields(binding)
            setupChangeButton(binding)
        }

        return binding?.root
    }

    private fun setupInputFields(binding: FragmentChangeMasterPasswordBinding) {
        binding.textInputEditTextNewMasterPasswordConfirm.onActionDone {
            changeClicked(binding)
        }
    }

    private fun setupTextViews(binding: FragmentChangeMasterPasswordBinding) {
        viewModel.loggedInUserViewModel?.biometricUnlockEnabled?.let { biometricUnlockEnabledBindable ->
            binding.textViewSubheaderDisableBiometricHint.bindVisibility(viewLifecycleOwner, biometricUnlockEnabledBindable)
        }
    }

    private fun setupChangeButton(binding: FragmentChangeMasterPasswordBinding) {
        binding.buttonChange.setOnClickListener {
            changeClicked(binding)
        }
    }

    private fun changeClicked(binding: FragmentChangeMasterPasswordBinding) {
        val formValidationResult = validateForm(
            listOfNotNull(
                FormFieldValidator(
                    binding.textInputLayoutOldMasterPassword, binding.textInputEditTextOldMasterPassword, listOf(
                        FormFieldValidator.Rule({ it.isNullOrEmpty() }, getString(R.string.change_master_password_old_master_password_validation_error_empty))
                    )
                ),
                FormFieldValidator(
                    binding.textInputLayoutNewMasterPassword, binding.textInputEditTextNewMasterPassword, listOf(
                        FormFieldValidator.Rule({ it.isNullOrEmpty() }, getString(R.string.change_master_password_new_master_password_validation_error_empty))
                    )
                ),
                FormFieldValidator(
                    binding.textInputLayoutNewMasterPassword, binding.textInputEditTextNewMasterPassword, listOf(
                        FormFieldValidator.Rule(
                            { binding.textInputEditTextOldMasterPassword.text?.toString() == it },
                            getString(R.string.change_master_password_new_master_password_validation_error_equal)
                        )
                    )
                ),
                FormFieldValidator(
                    binding.textInputLayoutNewMasterPasswordConfirm, binding.textInputEditTextNewMasterPasswordConfirm, listOf(
                        FormFieldValidator.Rule(
                            { binding.textInputEditTextNewMasterPassword.text?.toString() != it },
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
                showInformation(getString(R.string.change_master_password_successful_message))
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

    override fun onStop() {
        Keyboard.hideKeyboard(context, this)
        super.onStop()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = ChangeMasterPasswordFragment()
    }
}
