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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputLayout
import de.passbutler.app.base.addLifecycleObserver
import de.passbutler.app.base.bindEnabled
import de.passbutler.app.base.bindInput
import de.passbutler.app.base.bindTextAndVisibility
import de.passbutler.app.base.bindVisibility
import de.passbutler.app.base.formattedDateTime
import de.passbutler.app.base.launchRequestSending
import de.passbutler.app.databinding.FragmentItemdetailBinding
import de.passbutler.app.ui.FormFieldValidator
import de.passbutler.app.ui.FormValidationResult
import de.passbutler.app.ui.Keyboard
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.app.ui.showError
import de.passbutler.app.ui.showInformation
import de.passbutler.app.ui.validateForm
import de.passbutler.common.base.BindableObserver
import de.passbutler.common.base.DependentValueGetterBindable

class ItemDetailFragment : ToolBarFragment() {

    private val viewModel by viewModels<ItemEditingViewModel> {
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
        DependentValueGetterBindable(viewModel.isItemAuthorizationAllowed, viewModel.isItemModificationAllowed, viewModel.ownerUsername, viewModel.itemAuthorizationModifiedDate) {
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

    private val isItemModified by lazy {
        DependentValueGetterBindable(
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

    private val isItemModifiedObserver: BindableObserver<Boolean> = {
        updateToolbarMenuItems()
    }

    private val isNewItemObserver: BindableObserver<Boolean> = {
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
        binding = FragmentItemdetailBinding.inflate(inflater).also { binding ->
            setupItemFields(binding)
            setupItemAuthorizationsSection(binding)
            setupInformationView(binding)
            setupDeleteItemButton(binding)

            binding.groupExistingItemViews.bindVisibility(viewLifecycleOwner, viewModel.isNewItem) { isNewItem ->
                !isNewItem
            }

            applyRestoredViewStates(binding)
        }

        isItemModified.addLifecycleObserver(viewLifecycleOwner, true, isItemModifiedObserver)
        viewModel.isNewItem.addLifecycleObserver(viewLifecycleOwner, false, isNewItemObserver)

        return binding?.root
    }

    private fun setupItemFields(binding: FragmentItemdetailBinding) {
        binding.textInputLayoutTitle.bindEnabled(viewLifecycleOwner, viewModel.isItemModificationAllowed)
        binding.textInputEditTextTitle.bindInput(viewModel.title)

        setupPasswordField(binding)

        binding.textInputLayoutUsername.bindEnabled(viewLifecycleOwner, viewModel.isItemModificationAllowed)
        binding.textInputEditTextUsername.bindInput(viewModel.username)

        binding.textInputLayoutUrl.bindEnabled(viewLifecycleOwner, viewModel.isItemModificationAllowed)
        binding.textInputEditTextUrl.bindInput(viewModel.url)

        binding.textInputLayoutNotes.bindEnabled(viewLifecycleOwner, viewModel.isItemModificationAllowed)
        binding.textInputEditTextNotes.bindInput(viewModel.notes)
    }

    private fun setupPasswordField(binding: FragmentItemdetailBinding) {
        binding.textInputLayoutPassword.bindEnabled(viewLifecycleOwner, viewModel.isItemModificationAllowed)
        binding.textInputEditTextPassword.bindInput(viewModel.password)

        // Be sure, the `inputType` is set first to make `END_ICON_PASSWORD_TOGGLE` as `endIconMode` work properly
        if (viewModel.hidePasswordsEnabled) {
            binding.textInputEditTextPassword.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
            binding.textInputLayoutPassword.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        } else {
            binding.textInputEditTextPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            binding.textInputLayoutPassword.endIconMode = TextInputLayout.END_ICON_NONE
        }
    }

    private fun setupItemAuthorizationsSection(binding: FragmentItemdetailBinding) {
        binding.buttonManageAuthorizations.isEnabled = viewModel.isItemAuthorizationAvailable
        binding.buttonManageAuthorizations.bindVisibility(viewLifecycleOwner, viewModel.isNewItem, viewModel.isItemAuthorizationAllowed) { isNewItem, isItemAuthorizationAllowed ->
            !isNewItem && isItemAuthorizationAllowed
        }

        if (viewModel.isItemAuthorizationAvailable) {
            binding.buttonManageAuthorizations.setOnClickListener {
                viewModel.id.value?.let { itemId ->
                    showFragment(ItemAuthorizationsDetailFragment.newInstance(itemId))
                }
            }
        }

        binding.textViewAuthorizationsFooter.bindVisibility(viewLifecycleOwner, viewModel.isNewItem, viewModel.isItemAuthorizationAllowed) { isNewItem, isItemAuthorizationAllowed ->
            !isNewItem && isItemAuthorizationAllowed && !viewModel.isItemAuthorizationAvailable
        }

        binding.textViewAuthorizationsDescription.bindTextAndVisibility(viewLifecycleOwner, itemAuthorizationDescription)
    }

    private fun setupInformationView(binding: FragmentItemdetailBinding) {
        binding.informationItemId.textViewTitle.text = getString(R.string.itemdetail_id_title)
        binding.informationItemId.textViewValue.typeface = Typeface.MONOSPACE
        binding.informationItemId.textViewValue.bindTextAndVisibility(viewLifecycleOwner, viewModel.id)

        binding.informationItemModified.textViewTitle.text = getString(R.string.itemdetail_modified_title)
        binding.informationItemModified.textViewValue.bindTextAndVisibility(viewLifecycleOwner, viewModel.modified) {
            it?.formattedDateTime
        }

        binding.informationItemCreated.textViewTitle.text = getString(R.string.itemdetail_created_title)
        binding.informationItemCreated.textViewValue.bindTextAndVisibility(viewLifecycleOwner, viewModel.created) {
            it?.formattedDateTime
        }
    }

    private fun setupDeleteItemButton(binding: FragmentItemdetailBinding) {
        binding.buttonDeleteItem.bindEnabled(viewLifecycleOwner, viewModel.isItemModificationAllowed)

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

    override fun onStop() {
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