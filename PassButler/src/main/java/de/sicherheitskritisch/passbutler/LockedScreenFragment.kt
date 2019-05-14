package de.sicherheitskritisch.passbutler

import android.annotation.SuppressLint
import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.sicherheitskritisch.passbutler.base.BuildType
import de.sicherheitskritisch.passbutler.base.FormFieldValidator
import de.sicherheitskritisch.passbutler.base.FormValidationResult
import de.sicherheitskritisch.passbutler.base.RequestSendingViewHandler
import de.sicherheitskritisch.passbutler.base.RequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.validateForm
import de.sicherheitskritisch.passbutler.databinding.FragmentLockedScreenBinding
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.Keyboard
import java.lang.ref.WeakReference

class LockedScreenFragment : BaseViewModelFragment<RootViewModel>(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.FADE

    private var binding: FragmentLockedScreenBinding? = null
    private var unlockRequestSendingViewHandler: UnlockRequestSendingViewHandler? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        activity?.let {
            viewModel = getRootViewModel(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        unlockRequestSendingViewHandler = UnlockRequestSendingViewHandler(viewModel.unlockScreenRequestSendingViewModel, WeakReference(this)).apply {
            registerObservers()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate<FragmentLockedScreenBinding>(inflater, R.layout.fragment_locked_screen, container, false).also { binding ->
            binding.lifecycleOwner = this
            binding.viewModel = viewModel

            restoreSavedInstance(binding, savedInstanceState)
            setupDebugUnlockPresets(binding)
            setupUnlockButton(binding)
        }

        return binding?.root
    }

    private fun restoreSavedInstance(binding: FragmentLockedScreenBinding, savedInstanceState: Bundle?) {
        savedInstanceState?.getString(FORM_FIELD_PASSWORD)?.let { binding.textInputEditTextPassword.setText(it) }
    }

    @SuppressLint("SetTextI18n")
    private fun setupDebugUnlockPresets(binding: FragmentLockedScreenBinding) {
        if (BuildType.isDebugBuild) {
            binding.textInputEditTextPassword.setText("1234")
        }
    }

    private fun setupUnlockButton(binding: FragmentLockedScreenBinding) {
        binding.buttonUnlock.setOnClickListener {
            unlockWithPasswordClicked(binding)
        }
    }

    private fun unlockWithPasswordClicked(binding: FragmentLockedScreenBinding) {
        val formValidationResult = validateForm(
            listOf(
                FormFieldValidator(
                    binding.textInputEditTextPassword, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.locked_screen_password_validation_error_empty))
                    )
                )
            )
        )

        when (formValidationResult) {
            is FormValidationResult.Valid -> {
                // Remove focus before unlock to be sure keyboard is hidden
                removeFormFieldsFocus()

                val password = binding.textInputEditTextPassword.text.toString()
                viewModel.unlockScreen(password)
            }
            is FormValidationResult.Invalid -> {
                formValidationResult.firstInvalidFormField.requestFocus()
            }
        }
    }

    private fun removeFormFieldsFocus() {
        binding?.constraintLayoutLockedScreenContainer?.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        binding?.let {
            outState.putString(FORM_FIELD_PASSWORD, it.textInputEditTextPassword.text.toString())
        }
    }

    override fun onDestroyView() {
        Keyboard.hideKeyboard(context, this)
        super.onDestroyView()
    }

    override fun onDestroy() {
        unlockRequestSendingViewHandler?.unregisterObservers()
        super.onDestroy()
    }

    override fun onHandleBackPress(): Boolean {
        // Do not allow pop fragment via backpress
        return true
    }

    private class UnlockRequestSendingViewHandler(
        requestSendingViewModel: RequestSendingViewModel,
        private val fragmentWeakReference: WeakReference<LockedScreenFragment>
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
            binding?.constraintLayoutLockedScreenContainer?.let {
                resources?.getString(R.string.locked_screen_unlock_wrong_password_title)?.let { snackbarMessage ->
                    Snackbar.make(it, snackbarMessage, Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        override fun onRequestFinishedSuccessfully() {
            fragment?.popBackstack()
        }
    }

    companion object {
        private const val FORM_FIELD_PASSWORD = "FORM_FIELD_PASSWORD"

        fun newInstance() = LockedScreenFragment()
    }
}