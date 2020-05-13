package de.passbutler.app

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.passbutler.app.base.launchRequestSending
import de.passbutler.app.databinding.FragmentItemdetailBinding
import de.passbutler.app.ui.Keyboard
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.app.ui.showError
import de.passbutler.app.ui.showInformation
import de.passbutler.app.ui.showShortFeedback
import kotlinx.coroutines.Job

class ItemDetailFragment : ToolBarFragment<ItemEditingViewModel>() {

    private var formTitle: String? = null
    private var formPassword: String? = null

    private var toolbarMenuSaveItem: MenuItem? = null
    private var toolbarMenuDeleteItem: MenuItem? = null

    private var binding: FragmentItemdetailBinding? = null

    private val titleObserver = Observer<String> {
        updateToolbarTitle()
    }

    private val isNewEntryObserver = Observer<Boolean> {
        updateToolbarTitle()
        updateToolbarMenuItems()
    }

    private var saveRequestSendingJob: Job? = null
    private var deleteRequestSendingJob: Job? = null

    override fun getToolBarTitle(): String {
        return if (viewModel.isNewEntry.value) {
            getString(R.string.itemdetail_title_new)
        } else {
            getString(R.string.itemdetail_title_edit, viewModel.title.value)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        activity?.let {
            val rootViewModel = getRootViewModel(it)
            val loggedInUserViewModel = rootViewModel.loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException

            val itemId = arguments?.getString(ARGUMENT_ITEM_ID)
            val itemEditingViewModel = loggedInUserViewModel.itemViewModels.value.find { itemViewModel -> itemViewModel.id == itemId }?.createEditingViewModel()
                ?: loggedInUserViewModel.createNewItemEditingViewModel()

            val factory = ItemEditingViewModelFactory(itemEditingViewModel)

            // Use actual fragment (not the activity) for provider because we want always want to get a new `ItemEditingViewModel`
            viewModel = ViewModelProvider(this, factory).get(ItemEditingViewModel::class.java)
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
        toolbarMenuDeleteItem = toolbar.menu.findItem(R.id.item_detail_menu_item_delete)

        updateToolbarMenuItems()

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_detail_menu_item_save -> {
                    saveClicked()
                    true
                }
                R.id.item_detail_menu_item_delete -> {
                    deleteClicked()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateToolbarMenuItems() {
        toolbarMenuSaveItem?.isVisible = viewModel.isModificationAllowed.value
        toolbarMenuDeleteItem?.isVisible = viewModel.isModificationAllowed.value && !viewModel.isNewEntry.value
    }

    private fun saveClicked() {
        Keyboard.hideKeyboard(context, this)

        saveRequestSendingJob?.cancel()
        saveRequestSendingJob = launchRequestSending(
            handleSuccess = { showShortFeedback(getString(R.string.itemdetail_save_successful_message)) },
            handleFailure = { showError(getString(R.string.itemdetail_save_failed_general_title)) }
        ) {
            viewModel.save()
        }
    }

    private fun deleteClicked() {
        Keyboard.hideKeyboard(context, this)

        deleteRequestSendingJob?.cancel()
        deleteRequestSendingJob = launchRequestSending(
            handleSuccess = {
                popBackstack()
                showInformation(getString(R.string.itemdetail_delete_successful_message))
            },
            handleFailure = { showError(getString(R.string.itemdetail_delete_failed_general_title)) }
        ) {
            viewModel.delete()
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate<FragmentItemdetailBinding>(inflater, R.layout.fragment_itemdetail, container, false).also { binding ->
            binding.lifecycleOwner = viewLifecycleOwner
            binding.viewModel = viewModel

            applyRestoredViewStates(binding)
        }

        viewModel.title.observe(viewLifecycleOwner, titleObserver)
        viewModel.isNewEntry.observe(viewLifecycleOwner, isNewEntryObserver)

        return binding?.root
    }

    private fun applyRestoredViewStates(binding: FragmentItemdetailBinding) {
        formTitle?.let { binding.editTextTitle.setText(it) }
        formPassword?.let { binding.editTextPassword.setText(it) }
    }

    override fun onStop() {
        // Always hide keyboard if fragment gets stopped
        Keyboard.hideKeyboard(context, this)

        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(FORM_FIELD_TITLE, binding?.editTextTitle?.text?.toString())
        outState.putString(FORM_FIELD_PASSWORD, binding?.editTextPassword?.text?.toString())

        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARGUMENT_ITEM_ID = "ARGUMENT_ITEM_ID"

        private const val FORM_FIELD_TITLE = "FORM_FIELD_TITLE"
        private const val FORM_FIELD_PASSWORD = "FORM_FIELD_PASSWORD"

        fun newInstance(itemId: String?) = ItemDetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARGUMENT_ITEM_ID, itemId)
            }
        }
    }
}

class ItemEditingViewModelFactory(
    private val itemEditingViewModel: ItemEditingViewModel
) : ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return itemEditingViewModel as T
    }
}