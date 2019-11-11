package de.sicherheitskritisch.passbutler

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import de.sicherheitskritisch.passbutler.base.launchRequestSending
import de.sicherheitskritisch.passbutler.databinding.FragmentItemdetailBinding
import de.sicherheitskritisch.passbutler.ui.Keyboard
import de.sicherheitskritisch.passbutler.ui.ToolBarFragment
import de.sicherheitskritisch.passbutler.ui.applyTint
import de.sicherheitskritisch.passbutler.ui.showError

class ItemDetailFragment : ToolBarFragment<ItemEditingViewModel>() {

    private var binding: FragmentItemdetailBinding? = null

    private val titleObserver = Observer<String> {
        updateToolbarTitle()
    }

    override fun getToolBarTitle(): String {
        return if (viewModel.isNewEntry) {
            getString(R.string.itemdetail_title_new)
        } else {
            getString(R.string.itemdetail_title_edit, viewModel.title.value)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        activity?.let {
            val rootViewModel = getRootViewModel(it)
            val loggedInUserViewModel = rootViewModel.loggedInUserViewModel
            val userManager = loggedInUserViewModel?.userManager ?: throw IllegalStateException("The user manager is null!")

            val itemId = arguments?.getString(ARGUMENT_ITEM_ID)
            val itemViewModel = loggedInUserViewModel.itemViewModels.value?.find { itemViewModel -> itemViewModel.id == itemId }?.createEditingViewModel()
                ?: ItemEditingViewModel(ItemModel.New(loggedInUserViewModel), userManager)

            val factory = ItemEditingViewModelFactory(itemViewModel)

            // Use actual fragment (not the activity) for provider because we want always want to get a new `ItemEditingViewModel`
            viewModel = ViewModelProviders.of(this, factory).get(ItemEditingViewModel::class.java)
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate<FragmentItemdetailBinding>(inflater, R.layout.fragment_itemdetail, container, false).also { binding ->
            binding.lifecycleOwner = viewLifecycleOwner
            binding.viewModel = viewModel

            // TODO: Handle view rotation
        }

        return binding?.root
    }

    override fun setupToolbarMenu(toolbar: Toolbar) {
        toolbar.inflateMenu(R.menu.item_detail_menu)

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_detail_menu_item_save -> {
                    saveClicked()
                    true
                }
                else -> false
            }
        }

        toolbar.menu.findItem(R.id.item_detail_menu_item_save).apply {
            val menuIconColor = resources.getColor(R.color.white, null)
            icon.applyTint(menuIconColor)
        }
    }

    private fun saveClicked() {
        launchRequestSending(
            handleSuccess = { popBackstack() },
            handleFailure = { showError(getString(R.string.itemdetail_save_failed_general_title)) }
        ) {
            viewModel.save()
        }
    }

    override fun onStart() {
        super.onStart()

        viewModel.title.observe(viewLifecycleOwner, titleObserver)
    }

    override fun onStop() {
        // Always hide keyboard if fragment gets stopped
        Keyboard.hideKeyboard(context, this)

        super.onStop()
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
    private val itemEditingViewModel: ItemEditingViewModel
) : ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return itemEditingViewModel as T
    }
}