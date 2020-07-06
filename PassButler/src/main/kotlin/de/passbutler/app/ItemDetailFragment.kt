package de.passbutler.app

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputLayout
import de.passbutler.app.base.DependentNonNullValueGetterLiveData
import de.passbutler.app.base.DependentOptionalValueGetterLiveData
import de.passbutler.app.base.formattedDateTime
import de.passbutler.app.base.launchRequestSending
import de.passbutler.app.base.observe
import de.passbutler.app.databinding.FragmentItemdetailBinding
import de.passbutler.app.ui.FormFieldValidator
import de.passbutler.app.ui.FormValidationResult
import de.passbutler.app.ui.Keyboard
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.app.ui.showError
import de.passbutler.app.ui.showInformation
import de.passbutler.app.ui.validateForm
import org.tinylog.kotlin.Logger
import java.util.*

class ItemDetailFragment : ToolBarFragment() {

    val viewModel by viewModels<ItemEditingViewModel> {
        val itemId = arguments?.getString(ARGUMENT_ITEM_ID)
        ItemEditingViewModelFactory(userViewModelProvidingViewModel, itemId)
    }

    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

    private var formTitle: String? = null
    private var formUsername: String? = null
    private var formPassword: String? = null
    private var formUrl: String? = null
    private var formNotes: String? = null

    private var toolbarMenuSaveItem: MenuItem? = null

    private var binding: FragmentItemdetailBinding? = null

    private val itemAuthorizationDescription by lazy {
        DependentOptionalValueGetterLiveData(viewModel.isItemAuthorizationAllowed, viewModel.isItemModificationAllowed, viewModel.ownerUsername, viewModel.itemAuthorizationModifiedDate) {
            val itemOwnerUsername = viewModel.ownerUsername.value
            val itemAuthorizationModifiedDate = viewModel.itemAuthorizationModifiedDate.value?.formattedDateTime

            when {
                viewModel.isItemAuthorizationAllowed.value -> getString(R.string.itemdetail_authorizations_description_owned_item)
                viewModel.isItemModificationAllowed.value && itemOwnerUsername != null && itemAuthorizationModifiedDate != null -> getString(
                    R.string.itemdetail_authorizations_description_shared_item,
                    itemOwnerUsername,
                    itemAuthorizationModifiedDate
                )
                !viewModel.isItemModificationAllowed.value && itemOwnerUsername != null && itemAuthorizationModifiedDate != null -> getString(
                    R.string.itemdetail_authorizations_description_shared_readonly_item,
                    itemOwnerUsername,
                    itemAuthorizationModifiedDate
                )
                else -> null
            }
        }
    }

    private val itemAuthorizationDescriptionObserver = Observer<String?> {
        updateManageAuthorizationsSection()
    }

    private val isItemModified by lazy {
        DependentNonNullValueGetterLiveData(
            viewModel.title,
            viewModel.username,
            viewModel.password,
            viewModel.url,
            viewModel.notes
        ) {
            listOf(
                viewModel.title,
                viewModel.username,
                viewModel.password,
                viewModel.url,
                viewModel.notes
            ).any { it.isModified }
        }
    }

    private val isItemModifiedObserver = Observer<Boolean> {
        updateToolbarMenuItems()
    }

    private val itemIdObserver = Observer<String?> {
        binding?.informationItemId?.textViewValue?.text = it
    }

    private val itemModifiedDateObserver = Observer<Date?> {
        binding?.informationItemModified?.textViewValue?.text = it?.formattedDateTime
    }

    private val itemCreatedDateObserver = Observer<Date?> {
        binding?.informationItemCreated?.textViewValue?.text = it?.formattedDateTime
    }

    private val isNewItemObserver = Observer<Boolean> {
        Logger.debug("isNewItem = $it")
        updateToolbarTitle()
        updateToolbarMenuItems()
    }

    override fun getToolBarTitle(): String {
        return if (viewModel.isNewItem.value) {
            getString(R.string.itemdetail_title_new)
        } else {
            getString(R.string.itemdetail_title_edit)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        formTitle = savedInstanceState?.getString(FORM_FIELD_TITLE)
        formPassword = savedInstanceState?.getString(FORM_FIELD_PASSWORD)
    }

    override fun setupToolbarMenu(toolbar: Toolbar) {
        toolbar.inflateMenu(R.menu.item_detail_menu)

        toolbarMenuSaveItem = toolbar.menu.findItem(R.id.item_detail_menu_item_save)

        updateToolbarMenuItems()

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_detail_menu_item_save -> {
                    saveClicked()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateToolbarMenuItems() {
        toolbarMenuSaveItem?.isVisible = viewModel.isItemModificationAllowed.value
        toolbarMenuSaveItem?.isEnabled = isItemModified.value
    }

    private fun saveClicked() {
        binding?.let { binding ->
            val formValidationResult = validateForm(
                listOfNotNull(
                    FormFieldValidator(
                        binding.textInputLayoutTitle, binding.textInputEditTextTitle, listOf(
                            FormFieldValidator.Rule({ TextUtils.isEmpty(it) }, getString(R.string.itemdetail_title_validation_error_empty))
                        )
                    )
                )
            )

            when (formValidationResult) {
                is FormValidationResult.Valid -> {
                    Keyboard.hideKeyboard(context, this)

                    launchRequestSending(
                        handleFailure = { showError(getString(R.string.itemdetail_save_failed_general_title)) }
                    ) {
                        viewModel.save()
                    }

                    Unit
                }
                is FormValidationResult.Invalid -> {
                    formValidationResult.firstInvalidFormField.requestFocus()

                    Unit
                }
            }
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate<FragmentItemdetailBinding>(inflater, R.layout.fragment_itemdetail, container, false).also { binding ->
            binding.lifecycleOwner = viewLifecycleOwner
            binding.viewModel = viewModel

            setupPasswordField(binding)
            setupInformationView(binding)
            setupDeleteItemButton(binding)

            applyRestoredViewStates(binding)
        }

        isItemModified.observe(viewLifecycleOwner, true, isItemModifiedObserver)
        itemAuthorizationDescription.observe(viewLifecycleOwner, true, itemAuthorizationDescriptionObserver)

        viewModel.id.observe(viewLifecycleOwner, itemIdObserver)
        viewModel.modified.observe(viewLifecycleOwner, itemModifiedDateObserver)
        viewModel.created.observe(viewLifecycleOwner, itemCreatedDateObserver)
        viewModel.isNewItem.observe(viewLifecycleOwner, isNewItemObserver)

        return binding?.root
    }

    private fun setupPasswordField(binding: FragmentItemdetailBinding) {
        if (viewModel.hidePasswordsEnabled) {
            binding.textInputLayoutPassword.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            binding.textInputEditTextPassword.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            binding.textInputLayoutPassword.endIconMode = TextInputLayout.END_ICON_NONE
            binding.textInputEditTextPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
    }

    private fun setupInformationView(binding: FragmentItemdetailBinding) {
        binding.informationItemId.textViewValue.typeface = Typeface.MONOSPACE
    }

    private fun setupDeleteItemButton(binding: FragmentItemdetailBinding) {
        binding.buttonDeleteItem.setOnClickListener {
            deleteClicked()
        }
    }

    private fun deleteClicked() {
        Keyboard.hideKeyboard(context, this)

        launchRequestSending(
            handleSuccess = {
                popBackstack()
                showInformation(getString(R.string.itemdetail_delete_successful_message))
            },
            handleFailure = { showError(getString(R.string.itemdetail_delete_failed_general_title)) }
        ) {
            viewModel.delete()
        }
    }

    private fun applyRestoredViewStates(binding: FragmentItemdetailBinding) {
        formTitle?.let { binding.textInputEditTextTitle.setText(it) }
        formUsername?.let { binding.textInputEditTextUsername.setText(it) }
        formPassword?.let { binding.textInputEditTextPassword.setText(it) }
        formUrl?.let { binding.textInputEditTextUrl.setText(it) }
        formNotes?.let { binding.textInputEditTextNotes.setText(it) }
    }

    private fun updateManageAuthorizationsSection() {
        binding?.textViewAuthorizationsDescription?.text = itemAuthorizationDescription.value

        if (viewModel.isItemAuthorizationAvailable) {
            binding?.buttonManageAuthorizations?.setOnClickListener {
                viewModel.id.value?.let { itemId ->
                    showFragment(ItemAuthorizationsDetailFragment.newInstance(itemId))
                }
            }
        }
    }

    override fun onStop() {
        // Always hide keyboard if fragment gets stopped
        Keyboard.hideKeyboard(context, this)

        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(FORM_FIELD_TITLE, binding?.textInputEditTextTitle?.text?.toString())
        outState.putString(FORM_FIELD_USERNAME, binding?.textInputEditTextUsername?.text?.toString())
        outState.putString(FORM_FIELD_PASSWORD, binding?.textInputEditTextPassword?.text?.toString())
        outState.putString(FORM_FIELD_URL, binding?.textInputEditTextUrl?.text?.toString())
        outState.putString(FORM_FIELD_NOTES, binding?.textInputEditTextNotes?.text?.toString())

        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARGUMENT_ITEM_ID = "ARGUMENT_ITEM_ID"

        private const val FORM_FIELD_TITLE = "FORM_FIELD_TITLE"
        private const val FORM_FIELD_USERNAME = "FORM_FIELD_USERNAME"
        private const val FORM_FIELD_PASSWORD = "FORM_FIELD_PASSWORD"
        private const val FORM_FIELD_URL = "FORM_FIELD_URL"
        private const val FORM_FIELD_NOTES = "FORM_FIELD_NOTES"

        fun newInstance(itemId: String?) = ItemDetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARGUMENT_ITEM_ID, itemId)
            }
        }
    }
}

class ItemEditingViewModelFactory(
    private val userViewModelProvidingViewModel: UserViewModelProvidingViewModel,
    private val itemId: String?
) : ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val loggedInUserViewModel = userViewModelProvidingViewModel.loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        val itemEditingViewModel = loggedInUserViewModel.itemViewModels.value.find { itemViewModel -> itemViewModel.id == itemId }?.createEditingViewModel()
            ?: loggedInUserViewModel.createNewItemEditingViewModel()

        return (itemEditingViewModel as T)
    }
}