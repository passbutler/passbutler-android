package de.passbutler.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.passbutler.app.base.addLifecycleObserver
import de.passbutler.app.base.launchRequestSending
import de.passbutler.app.databinding.FragmentItemAuthorizationsDetailBinding
import de.passbutler.app.databinding.ListItemAuthorizationEntryBinding
import de.passbutler.app.databinding.ListItemAuthorizationHeaderBinding
import de.passbutler.app.ui.ListItemIdentifiable
import de.passbutler.app.ui.ListItemIdentifiableDiffCallback
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.app.ui.showError
import de.passbutler.common.base.BindableObserver
import de.passbutler.common.base.addAllIfNotNull
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

class ItemAuthorizationsDetailFragment : ToolBarFragment() {

    private val viewModel by viewModels<ItemAuthorizationsDetailViewModel> {
        val itemId = arguments?.getString(ARGUMENT_ITEM_ID) ?: throw IllegalArgumentException("The given item id is null!")
        ItemAuthorizationsDetailViewModelFactory(userViewModelProvidingViewModel, itemId)
    }

    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

    private var toolbarMenuSaveItem: MenuItem? = null
    private var binding: FragmentItemAuthorizationsDetailBinding? = null

    private val itemAuthorizationsObserver: BindableObserver<List<ItemAuthorizationViewModel>> = { newItemAuthorizationViewModels ->
        Logger.debug("newItemAuthorizationViewModels.size = ${newItemAuthorizationViewModels.size}")

        val adapter = binding?.recyclerViewItemAuthorizations?.adapter as? ItemAuthorizationsAdapter
        adapter?.submitList(newItemAuthorizationViewModels)
    }

    override fun getToolBarTitle(): String {
        return getString(R.string.itemauthorizations_title)
    }

    override fun setupToolbarMenu(toolbar: Toolbar) {
        toolbar.inflateMenu(R.menu.item_authorizations_detail_menu)

        toolbarMenuSaveItem = toolbar.menu.findItem(R.id.item_authorizations_detail_menu_item_save)

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_authorizations_detail_menu_item_save -> {
                    saveClicked()
                    true
                }
                else -> false
            }
        }
    }

    private fun saveClicked() {
        launchRequestSending(
            handleFailure = { showError(getString(R.string.itemauthorizations_save_failed_general_title)) }
        ) {
            viewModel.save()
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentItemAuthorizationsDetailBinding.inflate(inflater).also { binding ->
            setupItemAuthorizationsList(binding)
        }

        viewModel.anyItemAuthorizationWasModified.addLifecycleObserver(viewLifecycleOwner, true) {
            toolbarMenuSaveItem?.isEnabled = it
        }

        return binding?.root
    }

    private fun setupItemAuthorizationsList(binding: FragmentItemAuthorizationsDetailBinding) {
        binding.recyclerViewItemAuthorizations.apply {
            val linearLayoutManager = LinearLayoutManager(context)

            layoutManager = linearLayoutManager
            adapter = ItemAuthorizationsAdapter()

            val dividerItemDecoration = DividerItemDecoration(context, linearLayoutManager.orientation)
            addItemDecoration(dividerItemDecoration)
        }

        viewModel.itemAuthorizationViewModels.addLifecycleObserver(viewLifecycleOwner, false, itemAuthorizationsObserver)

        launch {
            viewModel.initializeItemAuthorizationViewModels()
        }
    }

    companion object {
        private const val ARGUMENT_ITEM_ID = "ARGUMENT_ITEM_ID"

        fun newInstance(itemId: String) = ItemAuthorizationsDetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARGUMENT_ITEM_ID, itemId)
            }
        }
    }
}

class ItemAuthorizationsAdapter : ListAdapter<ListItemIdentifiable, RecyclerView.ViewHolder>(ListItemIdentifiableDiffCallback()) {

    override fun submitList(normalList: List<ListItemIdentifiable>?) {
        val newSubmittedList = mutableListOf<ListItemIdentifiable>().apply {
            add(0, HeaderListItem())
            addAllIfNotNull(normalList)
        }

        super.submitList(newSubmittedList)
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> ListItemType.HEADER.ordinal
            else -> ListItemType.NORMAL.ordinal
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            ListItemType.HEADER.ordinal -> {
                val binding = ListItemAuthorizationHeaderBinding.inflate(layoutInflater, parent, false)
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ListItemAuthorizationEntryBinding.inflate(layoutInflater, parent, false)
                ItemAuthorizationViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> {
                // No need to update static header
            }
            is ItemAuthorizationViewHolder -> {
                (getItem(position) as? ItemAuthorizationViewModel)?.let { item ->
                    holder.apply {
                        itemView.tag = item
                        bind(item)
                    }
                }
            }
        }
    }

    class HeaderListItem : ListItemIdentifiable {
        override val listItemId = "DUMMY_HEADER_ID"
    }

    class HeaderViewHolder(
        binding: ListItemAuthorizationHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root)

    class ItemAuthorizationViewHolder(
        private val binding: ListItemAuthorizationEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(itemAuthorizationViewModel: ItemAuthorizationViewModel) {
            bindTitle(binding, itemAuthorizationViewModel)
            bindReadSwitch(binding, itemAuthorizationViewModel)
            bindWriteSwitch(binding, itemAuthorizationViewModel)
        }

        private fun bindTitle(binding: ListItemAuthorizationEntryBinding, itemAuthorizationViewModel: ItemAuthorizationViewModel) {
            binding.textViewTitle.text = itemAuthorizationViewModel.username
        }

        private fun bindReadSwitch(binding: ListItemAuthorizationEntryBinding, itemAuthorizationViewModel: ItemAuthorizationViewModel) {
            binding.apply {
                switchRead.isChecked = itemAuthorizationViewModel.isReadAllowed.value
                switchRead.setOnCheckedChangeListener { _, isChecked ->
                    itemAuthorizationViewModel.isReadAllowed.value = isChecked

                    // If no read access is given, write access is meaningless
                    if (!isChecked) {
                        switchWrite.isChecked = false
                    }
                }
            }
        }

        private fun bindWriteSwitch(binding: ListItemAuthorizationEntryBinding, itemAuthorizationViewModel: ItemAuthorizationViewModel) {
            binding.apply {
                switchWrite.isChecked = itemAuthorizationViewModel.isWriteAllowed.value
                switchWrite.setOnCheckedChangeListener { _, isChecked ->
                    itemAuthorizationViewModel.isWriteAllowed.value = isChecked

                    // If write access is given, read access is implied
                    if (isChecked) {
                        switchRead.isChecked = true
                    }
                }
            }
        }
    }

    enum class ListItemType {
        HEADER,
        NORMAL
    }
}

class ItemAuthorizationsDetailViewModelFactory(
    private val userViewModelProvidingViewModel: UserViewModelProvidingViewModel,
    private val itemId: String
) : ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val loggedInUserViewModel = userViewModelProvidingViewModel.loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        val itemAuthorizationsDetailViewModel = ItemAuthorizationsDetailViewModel(itemId, loggedInUserViewModel, loggedInUserViewModel.localRepository)

        return (itemAuthorizationsDetailViewModel as T)
    }
}