package de.passbutler.app

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
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
import de.passbutler.app.databinding.FragmentItemdetailBinding
import de.passbutler.app.ui.FormFieldValidator
import de.passbutler.app.ui.FormValidationResult
import de.passbutler.app.ui.Keyboard
import de.passbutler.app.ui.PasswordGeneratorDialogBuilder
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.app.ui.addLifecycleObserver
import de.passbutler.app.ui.bindInput
import de.passbutler.app.ui.bindTextAndVisibility
import de.passbutler.app.ui.bindVisibility
import de.passbutler.app.ui.setTextWithClickablePart
import de.passbutler.app.ui.showConfirmDialog
import de.passbutler.app.ui.validateForm
import de.passbutler.common.ItemEditingViewModel.Companion.NOTES_MAXIMUM_CHARACTERS
import de.passbutler.common.LoggedInUserViewModelUninitializedException
import de.passbutler.common.base.DependentValueGetterBindable
import de.passbutler.common.base.formattedDateTime
import de.passbutler.common.ui.RequestSending
import de.passbutler.common.ui.launchRequestSending

class ItemDetailFragment : ToolBarFragment(), RequestSending {

    private val viewModel
        get() = viewModelWrapper.itemEditingViewModel

    private val viewModelWrapper by viewModels<ItemEditingViewModelWrapper> {
        val itemId = arguments?.getString(ARGUMENT_ITEM_ID)
        ItemEditingViewModelFactory(userViewModelProvidingViewModel, itemId)
    }

    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

    private var toolbarMenuSaveItem: MenuItem? = null
    private var binding: FragmentItemdetailBinding? = null

    private val itemAuthorizationDescription by lazy {
        DependentValueGetterBindable(viewModel.isItemAuthorizationAllowed, viewModel.isItemModificationAllowed, viewModel.ownerUsername, viewModel.itemAuthorizationModifiedDate) {
            val itemOwnerUsername = viewModel.ownerUsername.value
            val itemAuthorizationModifiedDate = viewModel.itemAuthorizationModifiedDate.value?.formattedDateTime()

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

    override fun getToolBarTitle(): String {
        return if (viewModel.isNewItem.value) {
            getString(R.string.itemdetail_title_new)
        } else {
            getString(R.string.itemdetail_title_edit)
        }
    }

    override fun setupToolbarMenu(toolbar: Toolbar) {
        toolbar.inflateMenu(R.menu.item_detail_menu)

        toolbarMenuSaveItem = toolbar.menu.findItem(R.id.item_detail_menu_item_save)

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

    private fun saveClicked() {
        binding?.let { binding ->
            val formValidationResult = validateForm(
                listOfNotNull(
                    FormFieldValidator(
                        binding.textInputLayoutTitle, binding.textInputEditTextTitle, listOf(
                            FormFieldValidator.Rule({ it.isNullOrEmpty() }, getString(R.string.itemdetail_title_validation_error_empty))
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
        binding = FragmentItemdetailBinding.inflate(inflater, container, false).also { binding ->
            setupItemFields(binding)
            setupItemAuthorizationsSection(binding)
            setupInformationView(binding)
            setupDeleteSection(binding)

            binding.groupExistingItemViews.bindVisibility(viewLifecycleOwner, viewModel.isNewItem) { isNewItem ->
                !isNewItem
            }
        }

        isItemModified.addLifecycleObserver(viewLifecycleOwner, false) {
            updateToolbarMenuItems()
        }

        viewModel.isNewItem.addLifecycleObserver(viewLifecycleOwner, true) {
            updateToolbarTitle()
            updateToolbarMenuItems()
        }

        return binding?.root
    }

    private fun setupItemFields(binding: FragmentItemdetailBinding) {
        binding.textInputEditTextTitle.bindInput(viewLifecycleOwner, viewModel.title)

        setupPasswordField(binding)
        setupPasswordGeneratorText(binding)

        binding.textInputEditTextUsername.bindInput(viewLifecycleOwner, viewModel.username)
        binding.textInputEditTextUrl.bindInput(viewLifecycleOwner, viewModel.url)

        setupNotesField(binding)
    }

    private fun setupPasswordField(binding: FragmentItemdetailBinding) {
        binding.textInputEditTextPassword.bindInput(viewLifecycleOwner, viewModel.password)

        // Be sure, the `inputType` is set first to make `END_ICON_PASSWORD_TOGGLE` as `endIconMode` work properly
        if (viewModel.hidePasswordsEnabled) {
            binding.textInputEditTextPassword.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
            binding.textInputLayoutPassword.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        } else {
            binding.textInputEditTextPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            binding.textInputLayoutPassword.endIconMode = TextInputLayout.END_ICON_NONE
        }
    }

    private fun setupPasswordGeneratorText(binding: FragmentItemdetailBinding) {
        val generateWord = getString(R.string.itemdetail_password_generator_generate_word)
        val formattedText = getString(R.string.itemdetail_password_generator_text, generateWord)

        binding.textViewPasswordGeneratorText.setTextWithClickablePart(formattedText, generateWord) {
            showPasswordGeneratorDialog(binding)
        }
    }

    private fun showPasswordGeneratorDialog(binding: FragmentItemdetailBinding) {
        val passwordGeneratorDialog = PasswordGeneratorDialogBuilder(
            presentingFragment = this,
            positiveClickAction = { newPassword ->
                viewModel.password.value = newPassword
                binding.textInputEditTextPassword.requestFocus()
            }
        )

        passwordGeneratorDialog.show()
    }

    private fun setupNotesField(binding: FragmentItemdetailBinding) {
        binding.textInputLayoutNotes.isCounterEnabled = true
        binding.textInputLayoutNotes.counterMaxLength = NOTES_MAXIMUM_CHARACTERS

        binding.textInputEditTextNotes.bindInput(viewLifecycleOwner, viewModel.notes)
    }

    private fun setupItemAuthorizationsSection(binding: FragmentItemdetailBinding) {
        binding.textViewAuthorizationsDescription.bindTextAndVisibility(viewLifecycleOwner, itemAuthorizationDescription)

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

        binding.textViewAuthorizationsFooterTeaser.bindVisibility(viewLifecycleOwner, viewModel.isNewItem, viewModel.isItemAuthorizationAllowed) { isNewItem, isItemAuthorizationAllowed ->
            !isNewItem && isItemAuthorizationAllowed && !viewModel.isItemAuthorizationAvailable
        }
    }

    private fun setupInformationView(binding: FragmentItemdetailBinding) {
        binding.informationItemId.textViewTitle.text = getString(R.string.itemdetail_id_title)
        binding.informationItemId.textViewValue.typeface = Typeface.MONOSPACE
        binding.informationItemId.textViewValue.bindTextAndVisibility(viewLifecycleOwner, viewModel.id)

        binding.informationItemModified.textViewTitle.text = getString(R.string.itemdetail_modified_title)
        binding.informationItemModified.textViewValue.bindTextAndVisibility(viewLifecycleOwner, viewModel.modified) {
            it?.formattedDateTime()
        }

        binding.informationItemCreated.textViewTitle.text = getString(R.string.itemdetail_created_title)
        binding.informationItemCreated.textViewValue.bindTextAndVisibility(viewLifecycleOwner, viewModel.created) {
            it?.formattedDateTime()
        }
    }

    private fun setupDeleteSection(binding: FragmentItemdetailBinding) {
        binding.groupDeleteSectionViews.bindVisibility(viewLifecycleOwner, viewModel.isNewItem, viewModel.isItemModificationAllowed) { isNewItem, isItemModificationAllowed ->
            !isNewItem && isItemModificationAllowed
        }

        binding.buttonDeleteItem.setOnClickListener {
            deleteItemClicked()
        }
    }

    private fun deleteItemClicked() {
        Keyboard.hideKeyboard(context, this)

        showConfirmDialog(
            title = getString(R.string.itemdetail_delete_confirmation_title),
            positiveActionTitle = getString(R.string.itemdetail_delete_confirmation_button_title),
            positiveClickAction = {
                deleteItem()
            }
        )
    }

    private fun deleteItem() {
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

    private fun updateToolbarMenuItems() {
        toolbarMenuSaveItem?.isVisible = viewModel.isItemModificationAllowed.value
        toolbarMenuSaveItem?.isEnabled = isItemModified.value
    }

    override fun onHandleBackPress(): Boolean {
        return if (isItemModified.value) {
            showDiscardChangesConfirmDialog {
                super.onHandleBackPress()
            }

            true
        } else {
            super.onHandleBackPress()
        }
    }

    override fun onBackIconClicked() {
        if (isItemModified.value) {
            showDiscardChangesConfirmDialog {
                super.onBackIconClicked()
            }
        } else {
            super.onBackIconClicked()
        }
    }

    override fun onStop() {
        Keyboard.hideKeyboard(context, this)
        super.onStop()
    }

    override fun onDestroyView() {
        toolbarMenuSaveItem = null
        binding = null

        super.onDestroyView()
    }

    private fun showDiscardChangesConfirmDialog(positiveClickAction: () -> Unit) {
        showConfirmDialog(
            title = getString(R.string.itemdetail_discard_changes_confirmation_title),
            message = getString(R.string.itemdetail_discard_changes_confirmation_message),
            positiveActionTitle = getString(R.string.general_discard),
            positiveClickAction = positiveClickAction
        )
    }

    companion object {
        private const val ARGUMENT_ITEM_ID = "ARGUMENT_ITEM_ID"

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
        val itemEditingViewModelWrapper = ItemEditingViewModelWrapper(itemEditingViewModel)

        return (itemEditingViewModelWrapper as T)
    }
}
