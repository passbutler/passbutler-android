package de.sicherheitskritisch.passbutler

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewHandler
import de.sicherheitskritisch.passbutler.base.RequestSendingViewModel
import de.sicherheitskritisch.passbutler.databinding.FragmentItemdetailBinding
import de.sicherheitskritisch.passbutler.ui.ToolBarFragment
import de.sicherheitskritisch.passbutler.ui.showInformation
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class ItemDetailFragment : ToolBarFragment<ItemEditingViewModel>() {

    private var saveRequestHandler: SaveViewHandler? = null

    override fun getToolBarTitle() = getString(R.string.itemdetail_title, viewModel.title.value)

    override fun onAttach(context: Context) {
        super.onAttach(context)

        activity?.let {
            val rootViewModel = getRootViewModel(it)
            val loggedInUserViewModel = rootViewModel.loggedInUserViewModel
            val userManager = loggedInUserViewModel?.userManager ?: throw IllegalStateException("The user manager is null!")

            val itemId = arguments?.getString(ARGUMENT_ITEM_ID)
            val itemViewModel = loggedInUserViewModel.itemViewModels.value?.find { itemViewModel -> itemViewModel.id == itemId }?.createEditingViewModel()
                ?: ItemEditingViewModel(ItemModel.Creating(loggedInUserViewModel), userManager)

            val factory = ItemEditingViewModelFactory(itemViewModel)

            // Use actual fragment (not the activity) for provider because we want always want to get a new `ItemEditingViewModel`
            viewModel = ViewModelProviders.of(this, factory).get(ItemEditingViewModel::class.java)
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = DataBindingUtil.inflate<FragmentItemdetailBinding>(inflater, R.layout.fragment_itemdetail, container, false).also { binding ->
            binding.lifecycleOwner = viewLifecycleOwner
            binding.viewModel = viewModel

            // TODO: Handle view rotation

            binding.editTextTitle.addTextChangedListener(object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    viewModel.title.value = s?.toString() ?: ""
                    updateToolbarTitle()
                }
            })

            binding.editTextPassword.addTextChangedListener(object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    viewModel.password.value = s?.toString() ?: ""
                }
            })

            binding.buttonSave.setOnClickListener {
                viewModel.save()
            }
        }

        saveRequestHandler = SaveViewHandler(viewModel.saveRequestSendingViewModel, WeakReference(this))

        return binding?.root
    }

    override fun onStart() {
        super.onStart()

        saveRequestHandler?.registerObservers()
    }

    override fun onStop() {
        saveRequestHandler?.unregisterObservers()

        super.onStop()
    }

    override fun onDestroy() {
        viewModel.cancelJobs()
        super.onDestroy()
    }

    private class SaveViewHandler(
        requestSendingViewModel: RequestSendingViewModel,
        fragmentWeakReference: WeakReference<ItemDetailFragment>
    ) : DefaultRequestSendingViewHandler<ItemDetailFragment>(requestSendingViewModel, fragmentWeakReference) {

        override fun requestErrorMessageResourceId(requestError: Throwable) = R.string.itemdetail_save_failed_general_title

        override fun handleRequestFinishedSuccessfully() {
            fragment?.launch {
                fragment?.showInformation(resources?.getString(R.string.itemdetail_save_successful_message))
            }
        }
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

open class SimpleTextWatcher : TextWatcher {
    override fun afterTextChanged(s: Editable?) {
        // Implement if needed
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        // Implement if needed
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        // Implement if needed
    }
}