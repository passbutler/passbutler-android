package de.passbutler.app

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.passbutler.app.base.launchRequestSending
import de.passbutler.app.databinding.FragmentItemAuthorizationsDetailBinding
import de.passbutler.app.databinding.ListItemAuthorizationEntryBinding
import de.passbutler.app.databinding.ListItemAuthorizationHeaderBinding
import de.passbutler.app.ui.FormFieldValidator
import de.passbutler.app.ui.FormValidationResult
import de.passbutler.app.ui.Keyboard
import de.passbutler.app.ui.ListItemIdentifiable
import de.passbutler.app.ui.ListItemIdentifiableDiffCallback
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.app.ui.showError
import de.passbutler.app.ui.validateForm
import de.passbutler.common.base.addAllIfNotNull
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

class ItemAuthorizationsDetailFragment : ToolBarFragment<ItemAuthorizationsDetailViewModel>() {

    private var binding: FragmentItemAuthorizationsDetailBinding? = null

    private val itemAuthorizationsObserver = Observer<List<ItemAuthorizationViewModel>> { newItemAuthorizationViewModels ->
        Logger.debug("newItemAuthorizationViewModels.size = ${newItemAuthorizationViewModels.size}")

        val adapter = binding?.recyclerViewItemAuthorizations?.adapter as? ItemAuthorizationsAdapter
        adapter?.submitList(newItemAuthorizationViewModels)
    }

    override fun getToolBarTitle(): String {
        return getString(R.string.itemauthorizations_title)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        activity?.let {
            val rootViewModel = getRootViewModel(it)
            val loggedInUserViewModel = rootViewModel.loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException

            val itemId = arguments?.getString(ARGUMENT_ITEM_ID) ?: throw IllegalArgumentException("The given item id is null!")
            val itemAuthorizationsDetailViewModel = ItemAuthorizationsDetailViewModel(itemId, loggedInUserViewModel, loggedInUserViewModel.localRepository)
            val factory = ItemAuthorizationsDetailViewModelFactory(itemAuthorizationsDetailViewModel)

            // Use actual fragment (not the activity) for provider because we want always want to get a new `ItemEditingViewModel`
            viewModel = ViewModelProvider(this, factory).get(ItemAuthorizationsDetailViewModel::class.java)
        }
    }

    override fun setupToolbarMenu(toolbar: Toolbar) {
        toolbar.inflateMenu(R.menu.item_authorizations_detail_menu)

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
        val binding = DataBindingUtil.inflate<FragmentItemAuthorizationsDetailBinding>(inflater, R.layout.fragment_item_authorizations_detail, container, false).also { binding ->
            binding.lifecycleOwner = viewLifecycleOwner
        }

        setupItemAuthorizationsList(binding)

        this.binding = binding
        return binding?.root
    }

    private fun setupItemAuthorizationsList(binding: FragmentItemAuthorizationsDetailBinding) {
        binding.recyclerViewItemAuthorizations.apply {
            val linearLayoutManager = LinearLayoutManager(context)

            layoutManager = linearLayoutManager
            adapter = ItemAuthorizationsAdapter()

            // TODO: More lighter decoration for android.R.attr.listDivider / R.attr.recyclerViewStyle
            val dividerItemDecoration = DividerItemDecoration(context, linearLayoutManager.orientation)
            addItemDecoration(dividerItemDecoration)
        }

        viewModel.itemAuthorizations.observe(viewLifecycleOwner, itemAuthorizationsObserver)

        launch {
            viewModel.initializeItemAuthorizations()
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
        return when (viewType) {
            ListItemType.HEADER.ordinal -> {
                val binding = DataBindingUtil.inflate<ListItemAuthorizationHeaderBinding>(LayoutInflater.from(parent.context), R.layout.list_item_authorization_header, parent, false)
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = DataBindingUtil.inflate<ListItemAuthorizationEntryBinding>(LayoutInflater.from(parent.context), R.layout.list_item_authorization_entry, parent, false)
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
            binding.apply {
                // TODO: Set `lifecycleOwner`?
                viewModel = itemAuthorizationViewModel
                executePendingBindings()
            }
        }
    }

    enum class ListItemType {
        HEADER,
        NORMAL
    }
}

class ItemAuthorizationsDetailViewModelFactory(
    private val itemAuthorizationsDetailViewModel: ItemAuthorizationsDetailViewModel
) : ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return itemAuthorizationsDetailViewModel as T
    }
}