package de.passbutler.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import de.passbutler.app.base.BuildInformationProvider
import de.passbutler.app.base.DebugConstants
import de.passbutler.app.databinding.FragmentCreateLocalUserWizardBinding
import de.passbutler.app.databinding.LayoutCreateLocalUserWizardStep1Binding
import de.passbutler.app.databinding.LayoutCreateLocalUserWizardStep2Binding
import de.passbutler.app.ui.BaseFragment
import de.passbutler.app.ui.FormFieldValidator
import de.passbutler.app.ui.FormValidationResult
import de.passbutler.app.ui.Keyboard
import de.passbutler.app.ui.bindInput
import de.passbutler.app.ui.onActionDone
import de.passbutler.app.ui.onActionNext
import de.passbutler.app.ui.showFadeInOutAnimation
import de.passbutler.app.ui.validateForm
import de.passbutler.common.base.BuildType
import de.passbutler.common.ui.RequestSending
import de.passbutler.common.ui.launchRequestSending

class CreateLocalUserWizardFragment : BaseFragment(), RequestSending {

    private val viewModel by viewModels<CreateLocalUserWizardViewModel>()
    private val rootViewModel by userViewModelUsingActivityViewModels<RootViewModel>(userViewModelProvidingViewModel = { userViewModelProvidingViewModel })
    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

    private var binding: FragmentCreateLocalUserWizardBinding? = null

    private var currentStep: Step = Step.Username

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savedInstanceState?.getInt(BUNDLE_CURRENT_STEP)?.let { Step.values().getOrNull(it) }?.let { restoredStep ->
            currentStep = restoredStep
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): ScrollView? {
        binding = FragmentCreateLocalUserWizardBinding.inflate(inflater, container, false).apply {
            setupDebugPresetsButton()
            showStep(currentStep)

            layoutStep1.setupStep1()
            layoutStep2.setupStep2()

            setupBackButton()
        }

        return binding?.root
    }

    private fun FragmentCreateLocalUserWizardBinding.setupDebugPresetsButton() {
        if (BuildInformationProvider.buildType == BuildType.Debug) {
            layoutHeader.root.setOnLongClickListener {
                viewModel.username.value = DebugConstants.TEST_USERNAME
                viewModel.masterPassword.value = DebugConstants.TEST_PASSWORD
                true
            }
        }
    }

    private fun FragmentCreateLocalUserWizardBinding.showStep(step: Step) {
        when (step) {
            Step.Username -> {
                layoutStep1.root.showFadeInOutAnimation(true)
                layoutStep1.textInputEditTextUsername.requestFocus()

                layoutStep2.root.showFadeInOutAnimation(false)
            }
            Step.MasterPassword -> {
                layoutStep1.root.showFadeInOutAnimation(false)

                layoutStep2.root.showFadeInOutAnimation(true)
                layoutStep2.textInputEditTextMasterPassword.requestFocus()
            }
        }

        currentStep = step
    }

    private fun LayoutCreateLocalUserWizardStep1Binding.setupStep1() {
        textViewStepTitle.text = getString(R.string.create_local_user_step_headline, 1, 2)

        textInputEditTextUsername.bindInput(viewLifecycleOwner, viewModel.username)
        textInputEditTextUsername.onActionNext {
            confirmStep1ButtonClicked()
        }

        buttonNext.setOnClickListener {
            confirmStep1ButtonClicked()
        }
    }

    private fun LayoutCreateLocalUserWizardStep1Binding.confirmStep1ButtonClicked() {
        val formValidationResult = validateForm(
            listOfNotNull(
                FormFieldValidator(
                    textInputLayoutUsername, textInputEditTextUsername, listOf(
                        FormFieldValidator.Rule({ it.isNullOrEmpty() }, getString(R.string.form_username_validation_error_empty))
                    )
                )
            )
        )

        when (formValidationResult) {
            is FormValidationResult.Valid -> {
                binding?.showStep(Step.MasterPassword)
            }
            is FormValidationResult.Invalid -> {
                formValidationResult.firstInvalidFormField.requestFocus()
            }
        }
    }

    private fun LayoutCreateLocalUserWizardStep2Binding.setupStep2() {
        textViewStepTitle.text = getString(R.string.create_local_user_step_headline, 2, 2)

        textInputEditTextMasterPassword.bindInput(viewLifecycleOwner, viewModel.masterPassword)
        textInputEditTextMasterPasswordConfirm.onActionDone {
            confirmStep2ButtonClicked()
        }

        buttonDone.setOnClickListener {
            confirmStep2ButtonClicked()
        }
    }

    private fun LayoutCreateLocalUserWizardStep2Binding.confirmStep2ButtonClicked() {
        val formValidationResult = validateForm(
            listOfNotNull(
                FormFieldValidator(
                    textInputLayoutMasterPassword, textInputEditTextMasterPassword, listOf(
                        FormFieldValidator.Rule({ it.isNullOrEmpty() }, getString(R.string.form_master_password_validation_error_empty))
                    )
                ),
                FormFieldValidator(
                    textInputLayoutMasterPasswordConfirm, textInputEditTextMasterPasswordConfirm, listOf(
                        FormFieldValidator.Rule(
                            { textInputEditTextMasterPassword.text?.toString() != it },
                            getString(R.string.create_local_user_step_master_password_confirm_validation_error_different)
                        )
                    )
                )
            )
        )

        when (formValidationResult) {
            is FormValidationResult.Valid -> {
                removeFormFieldsFocus()
                Keyboard.hideKeyboard(context, this@CreateLocalUserWizardFragment)
                createLocalUser()
            }
            is FormValidationResult.Invalid -> {
                formValidationResult.firstInvalidFormField.requestFocus()
            }
        }
    }

    private fun LayoutCreateLocalUserWizardStep2Binding.removeFormFieldsFocus() {
        root.requestFocus()
    }

    private fun createLocalUser() {
        val serverUrl = null
        val username = viewModel.username.value
        val masterPassword = viewModel.masterPassword.value

        if (username != null && masterPassword != null) {
            launchRequestSending(
                handleFailure = {
                    showError(getString(R.string.create_local_user_failed_general_title))
                },
                isCancellable = false
            ) {
                rootViewModel.loginVault(serverUrl, username, masterPassword)
            }
        }
    }

    private fun FragmentCreateLocalUserWizardBinding.setupBackButton() {
        buttonBack.setOnClickListener {
            when (currentStep) {
                Step.Username -> {
                    popBackstack()
                }
                Step.MasterPassword -> {
                    showStep(Step.Username)
                }
            }
        }
    }

    override fun onHandleBackPress(): Boolean {
        return when (currentStep) {
            Step.Username -> {
                super.onHandleBackPress()
            }
            Step.MasterPassword -> {
                binding?.showStep(Step.Username)
                true
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(BUNDLE_CURRENT_STEP, currentStep.ordinal)

        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private enum class Step {
        Username,
        MasterPassword
    }

    companion object {
        private const val BUNDLE_CURRENT_STEP = "BUNDLE_CURRENT_STEP"

        fun newInstance() = CreateLocalUserWizardFragment()
    }
}
