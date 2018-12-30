package de.sicherheitskritisch.pass

import android.arch.lifecycle.Observer
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import de.sicherheitskritisch.pass.common.FormFieldValidator
import de.sicherheitskritisch.pass.common.FormValidationResult
import de.sicherheitskritisch.pass.common.showFadeAnimation
import de.sicherheitskritisch.pass.common.signal
import de.sicherheitskritisch.pass.common.validateForm
import de.sicherheitskritisch.pass.databinding.FragmentLoginBinding
import de.sicherheitskritisch.pass.ui.AnimatedFragment
import de.sicherheitskritisch.pass.ui.BaseViewModelFragment

class LoginFragment : BaseViewModelFragment<LoginViewModel>(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.SLIDE_HORIZONTAL

    private val requestFinishedSuccessfullySignal = signal {
        showFragmentAsFirstScreen(OverviewFragment.newInstance())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel?.requestFinishedSuccessfully?.addSignal(requestFinishedSuccessfullySignal)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = DataBindingUtil.inflate<FragmentLoginBinding>(inflater, R.layout.fragment_login, container, false)
        binding.setLifecycleOwner(this)
        binding.viewModel = viewModel

        binding.buttonLogin.setOnClickListener {
            loginClicked(binding)
        }

        viewModel?.isLoading?.observe(this, Observer<Boolean> {
            it?.let { shouldShowProgress ->
                binding.frameLayoutProgressContainer.showFadeAnimation(shouldShowProgress)
            }
        })

        return binding.root
    }

    private fun loginClicked(binding: FragmentLoginBinding) {
        val formValidationResult = validateForm(
            listOf(
                FormFieldValidator(
                    binding.editTextServerurl, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.login_serverurl_validation_error_empty)),
                        FormFieldValidator.Rule({ !URLUtil.isValidUrl(it) }, getString(R.string.login_serverurl_validation_error_invalid))
                    )
                ),
                FormFieldValidator(
                    binding.editTextUsername, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.login_username_validation_error_empty))
                    )
                ),
                FormFieldValidator(
                    binding.editTextPassword, listOf(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.login_password_validation_error_empty))
                    )
                )
            )
        )

        when (formValidationResult) {
            is FormValidationResult.Valid -> {
                val serverUrl = binding.editTextServerurl.text.toString()
                val username = binding.editTextUsername.text.toString()
                val password = binding.editTextPassword.text.toString()
                viewModel?.login(username, password)
            }
            is FormValidationResult.Invalid -> {
                formValidationResult.firstInvalidFormField.requestFocus()
            }
        }
    }

    override fun onDestroy() {
        viewModel?.requestFinishedSuccessfully?.removeSignal(requestFinishedSuccessfullySignal)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LoginFragment"

        fun newInstance(viewModel: LoginViewModel) = LoginFragment().also { it.viewModel = viewModel }
    }
}
