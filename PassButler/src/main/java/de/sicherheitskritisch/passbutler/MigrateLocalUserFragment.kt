package de.sicherheitskritisch.passbutler

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import de.sicherheitskritisch.passbutler.base.BuildType
import de.sicherheitskritisch.passbutler.base.FormFieldValidator
import de.sicherheitskritisch.passbutler.base.FormValidationResult
import de.sicherheitskritisch.passbutler.base.launchRequestSending
import de.sicherheitskritisch.passbutler.base.validateForm
import de.sicherheitskritisch.passbutler.database.RequestUnauthorizedException
import de.sicherheitskritisch.passbutler.databinding.FragmentMigrateLocalUserBinding
import de.sicherheitskritisch.passbutler.ui.Keyboard
import de.sicherheitskritisch.passbutler.ui.ToolBarFragment
import de.sicherheitskritisch.passbutler.ui.showError
import kotlinx.coroutines.Job

class MigrateLocalUserFragment : ToolBarFragment<MigrateLocalUserViewModel>() {

    private var formServerUrl: String? = null

    private var binding: FragmentMigrateLocalUserBinding? = null

    private var migrateRequestSendingJob: Job? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)

        viewModel = ViewModelProvider(this).get(MigrateLocalUserViewModel::class.java)

        activity?.let {
            viewModel.rootViewModel = getRootViewModel(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        formServerUrl = savedInstanceState?.getString(FORM_FIELD_SERVERURL)
    }

    override fun getToolBarTitle() = getString(R.string.migrate_local_user_title)

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate<FragmentMigrateLocalUserBinding>(inflater, R.layout.fragment_migrate_local_user, container, false).also { binding ->
            binding.lifecycleOwner = viewLifecycleOwner

            applyRestoredViewStates(binding)
        }

        return binding?.root
    }

    private fun applyRestoredViewStates(binding: FragmentMigrateLocalUserBinding) {
        formServerUrl?.let { binding.textInputEditTextServerurl.setText(it) }
    }

    override fun onStart() {
        super.onStart()

        binding?.let {
            setupMigrateButton(it)
        }
    }

    private fun setupMigrateButton(binding: FragmentMigrateLocalUserBinding) {
        binding.buttonMigrate.setOnClickListener {
            migrateClicked(binding)
        }
    }

    private fun migrateClicked(binding: FragmentMigrateLocalUserBinding) {
        val formValidationResult = validateForm(
            listOfNotNull(
                FormFieldValidator(
                    binding.textInputLayoutServerurl, binding.textInputEditTextServerurl, listOfNotNull(
                        FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.form_serverurl_validation_error_empty)),
                        FormFieldValidator.Rule({ !URLUtil.isValidUrl(it) }, getString(R.string.form_serverurl_validation_error_invalid)),
                        FormFieldValidator.Rule({ !URLUtil.isHttpsUrl(it) }, getString(R.string.form_serverurl_validation_error_invalid_scheme)).takeIf { BuildType.isReleaseBuild }
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

                // TODO: null should not get validated
                if (serverUrl != null) {
                    migrateUser(serverUrl)
                }
            }
            is FormValidationResult.Invalid -> {
                formValidationResult.firstInvalidFormField.requestFocus()
            }
        }
    }

    private fun migrateUser(serverUrl: String) {
        migrateRequestSendingJob?.cancel()
        migrateRequestSendingJob = launchRequestSending(
            handleSuccess = {
                // TODO: Snackbar
                popBackstack()
            },
            handleFailure = {
                val errorStringResourceId = when (it.cause) {
                    is RequestUnauthorizedException -> R.string.migrate_local_user_failed_unauthorized_title
                    else -> R.string.migrate_local_user_failed_general_title
                }

                showError(getString(errorStringResourceId))
            }
        ) {
            viewModel.migrateLocalUser(serverUrl)
        }
    }

    private fun removeFormFieldsFocus() {
        binding?.constraintLayoutMigrateLocalUserScreenContainer?.requestFocus()
    }

    override fun onStop() {
        // Always hide keyboard if fragment gets stopped
        Keyboard.hideKeyboard(context, this)

        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(FORM_FIELD_SERVERURL, binding?.textInputEditTextServerurl?.text?.toString())

        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val FORM_FIELD_SERVERURL = "FORM_FIELD_SERVERURL"

        fun newInstance() = MigrateLocalUserFragment()
    }
}