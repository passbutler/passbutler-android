package de.passbutler.app

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.passbutler.app.databinding.FragmentItemAuthorizationsDetailBinding
import de.passbutler.app.databinding.ListItemAuthorizationEntryBinding
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.common.database.models.ItemAuthorization
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

class ItemAuthorizationsDetailFragment : ToolBarFragment<ItemAuthorizationsDetailViewModel>() {

    private var binding: FragmentItemAuthorizationsDetailBinding? = null

    private val itemAuthorizationsObserver = Observer<List<ItemAuthorization>> { newItemAuthorizations ->
        Logger.debug("newItemAuthorizations.size = ${newItemAuthorizations.size}")

        val adapter = binding?.recyclerViewItemAuthorizations?.adapter as? ItemAuthorizationsAdapter
        adapter?.submitList(newItemAuthorizations)
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

class ItemAuthorizationsAdapter : ListAdapter<ItemAuthorization, ItemAuthorizationsAdapter.ItemAuthorizationViewHolder>(ItemAuthorizationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemAuthorizationViewHolder {
        val binding = DataBindingUtil.inflate<ListItemAuthorizationEntryBinding>(LayoutInflater.from(parent.context), R.layout.list_item_authorization_entry, parent, false)
        return ItemAuthorizationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemAuthorizationViewHolder, position: Int) {
        getItem(position).let { item ->
            holder.apply {
                itemView.tag = item
                bind(item)
            }
        }
    }

    class ItemAuthorizationViewHolder(
        private val binding: ListItemAuthorizationEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(itemAuthorization: ItemAuthorization) {
            binding.apply {
                // TODO: Username not id semantically
                textViewTitle.text = itemAuthorization.userId

                // TODO: Init switches
            }
        }
    }
}

private class ItemAuthorizationDiffCallback : DiffUtil.ItemCallback<ItemAuthorization>() {
    override fun areItemsTheSame(oldItem: ItemAuthorization, newItem: ItemAuthorization): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ItemAuthorization, newItem: ItemAuthorization): Boolean {
        return oldItem == newItem
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