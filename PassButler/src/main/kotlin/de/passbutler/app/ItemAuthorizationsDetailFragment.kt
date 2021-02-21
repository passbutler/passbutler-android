package de.passbutler.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.passbutler.app.databinding.FragmentItemAuthorizationsDetailBinding
import de.passbutler.app.databinding.ListItemAuthorizationEntryBinding
import de.passbutler.app.databinding.ListItemAuthorizationHeaderBinding
import de.passbutler.app.ui.ListItemIdentifiableDiffCallback
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.app.ui.addLifecycleObserver
import de.passbutler.app.ui.visible
import de.passbutler.common.ItemAuthorizationEditingViewModel
import de.passbutler.common.ItemAuthorizationsDetailViewModel
import de.passbutler.common.LoggedInUserViewModelUninitializedException
import de.passbutler.common.base.BindableObserver
import de.passbutler.common.base.addAllIfNotNull
import de.passbutler.common.ui.ListItemIdentifiable
import de.passbutler.common.ui.RequestSending
import de.passbutler.common.ui.launchRequestSending
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger
import java.util.*

class ItemAuthorizationsDetailFragment : ToolBarFragment(), RequestSending {

    private val viewModel
        get() = viewModelWrapper.itemAuthorizationsDetailViewModel

    private val viewModelWrapper by viewModels<ItemAuthorizationsDetailViewModelWrapper> {
        val itemId = arguments?.getString(ARGUMENT_ITEM_ID) ?: throw IllegalArgumentException("The given item id is null!")
        ItemAuthorizationsDetailViewModelFactory(userViewModelProvidingViewModel, itemId)
    }

    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

    private var toolbarMenuSaveItem: MenuItem? = null
    private var binding: FragmentItemAuthorizationsDetailBinding? = null

    private val itemAuthorizationsObserver: BindableObserver<List<ItemAuthorizationEditingViewModel>> = { newItemAuthorizationEditingViewModels ->
        Logger.debug("newItemAuthorizationEditingViewModels.size = ${newItemAuthorizationEditingViewModels.size}")

        val newItemAuthorizationEntries = newItemAuthorizationEditingViewModels
            .map { ItemAuthorizationEntry(it) }
            .sorted()

        val adapter = binding?.recyclerViewItemAuthorizations?.adapter as? ItemAuthorizationsAdapter
        adapter?.submitList(newItemAuthorizationEntries)

        val showEmptyScreen = newItemAuthorizationEntries.isEmpty()
        binding?.layoutEmptyScreen?.root?.visible = showEmptyScreen
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
        binding = FragmentItemAuthorizationsDetailBinding.inflate(inflater, container, false).also { binding ->
            setupItemAuthorizationsList(binding)
            setupEmptyScreen(binding)
        }

        viewModel.itemAuthorizationEditingViewModelsModified.addLifecycleObserver(viewLifecycleOwner, true) {
            toolbarMenuSaveItem?.isEnabled = it
        }

        viewModel.itemAuthorizationEditingViewModels.addLifecycleObserver(viewLifecycleOwner, false, itemAuthorizationsObserver)

        launch {
            viewModel.initializeItemAuthorizationEditingViewModels()
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
    }

    private fun setupEmptyScreen(binding: FragmentItemAuthorizationsDetailBinding) {
        binding.layoutEmptyScreen.apply {
            imageViewIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.icon_account_circle_24dp, root.context.theme))
            textViewTitle.text = getString(R.string.itemauthorizations_empty_screen_title)
            textViewDescription.text = getString(R.string.itemauthorizations_empty_screen_description)
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
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
                (getItem(position) as? ItemAuthorizationEntry)?.let { item ->
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

        fun bind(entry: ItemAuthorizationEntry) {
            bindTitle(binding, entry)
            bindReadSwitch(binding, entry)
            bindWriteSwitch(binding, entry)
        }

        private fun bindTitle(binding: ListItemAuthorizationEntryBinding, entry: ItemAuthorizationEntry) {
            binding.textViewTitle.text = entry.itemAuthorizationEditingViewModel.username
        }

        private fun bindReadSwitch(binding: ListItemAuthorizationEntryBinding, entry: ItemAuthorizationEntry) {
            binding.apply {
                switchRead.isChecked = entry.itemAuthorizationEditingViewModel.isReadAllowed.value
                switchRead.setOnCheckedChangeListener { _, isChecked ->
                    entry.itemAuthorizationEditingViewModel.isReadAllowed.value = isChecked

                    // If no read access is given, write access is meaningless
                    if (!isChecked) {
                        switchWrite.isChecked = false
                    }
                }
            }
        }

        private fun bindWriteSwitch(binding: ListItemAuthorizationEntryBinding, entry: ItemAuthorizationEntry) {
            binding.apply {
                switchWrite.isChecked = entry.itemAuthorizationEditingViewModel.isWriteAllowed.value
                switchWrite.setOnCheckedChangeListener { _, isChecked ->
                    entry.itemAuthorizationEditingViewModel.isWriteAllowed.value = isChecked

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

class ItemAuthorizationEntry(val itemAuthorizationEditingViewModel: ItemAuthorizationEditingViewModel) : ListItemIdentifiable, Comparable<ItemAuthorizationEntry> {
    override val listItemId: String
        get() = when (itemAuthorizationModel) {
            is ItemAuthorizationEditingViewModel.ItemAuthorizationModel.Provisional -> itemAuthorizationModel.itemAuthorizationId
            is ItemAuthorizationEditingViewModel.ItemAuthorizationModel.Existing -> itemAuthorizationModel.itemAuthorization.id
        }

    private val itemAuthorizationModel = itemAuthorizationEditingViewModel.itemAuthorizationModel

    override fun compareTo(other: ItemAuthorizationEntry): Int {
        return compareValuesBy(this, other, { it.itemAuthorizationEditingViewModel.username.toLowerCase(Locale.getDefault()) })
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
        val itemAuthorizationsDetailViewModelWrapper = ItemAuthorizationsDetailViewModelWrapper(itemAuthorizationsDetailViewModel)

        return (itemAuthorizationsDetailViewModelWrapper as T)
    }
}
