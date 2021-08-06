package de.passbutler.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.passbutler.app.databinding.FragmentRecycleBinBinding
import de.passbutler.app.databinding.ListItemEntryBinding
import de.passbutler.app.ui.FilterableListAdapter
import de.passbutler.app.ui.ListItemIdentifiableDiffCallback
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.app.ui.VerticalSpaceItemDecoration
import de.passbutler.app.ui.addLifecycleObserver
import de.passbutler.app.ui.setupWithFilterableAdapter
import de.passbutler.app.ui.visible
import de.passbutler.common.ItemViewModel
import de.passbutler.common.base.BindableObserver
import de.passbutler.common.ui.RequestSending
import de.passbutler.common.ui.launchRequestSending
import org.tinylog.kotlin.Logger

class RecycleBinFragment : ToolBarFragment(), RequestSending {

    private val viewModel by userViewModelUsingViewModels<RecycleBinViewModel>(userViewModelProvidingViewModel = { userViewModelProvidingViewModel })
    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

    private var toolbarMenuSearchView: SearchView? = null
    private var binding: FragmentRecycleBinBinding? = null

    private val itemViewModelsObserver: BindableObserver<List<ItemViewModel>> = { newUnfilteredItemViewModels ->
        // Only show deleted items
        val newItemViewModels = newUnfilteredItemViewModels.filter { it.deleted }
        Logger.debug("newItemViewModels.size = ${newItemViewModels.size}")

        val adapter = binding?.recyclerViewItems?.adapter as? RecycleBinItemEntryAdapter

        val newItemEntries = newItemViewModels
            .map { ItemEntry(it) }
            .sorted()

        adapter?.submitList(newItemEntries)

        val showEmptyScreen = newItemEntries.isEmpty()
        binding?.layoutEmptyScreen?.root?.visible = showEmptyScreen

        // Update list according to new list
        toolbarMenuSearchView?.query?.takeIf { it.isNotEmpty() }?.let { currentText ->
            adapter?.filter?.filter(currentText)
        }
    }

    override fun getToolBarTitle() = getString(R.string.recycle_bin_title)

    override fun setupToolbarMenu(toolbar: Toolbar) {
        toolbar.inflateMenu(R.menu.item_search_menu)

        toolbarMenuSearchView = (toolbar.menu.findItem(R.id.item_search_menu_item_search)?.actionView as SearchView).apply {
            queryHint = getString(R.string.general_search)
        }

        toolbar.setOnClickListener {
            binding?.recyclerViewItems?.scrollToPosition(0)
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentRecycleBinBinding.inflate(inflater, container, false).also { binding ->
            setupEntryList(binding)
            setupEmptyScreen(binding)
        }

        viewModel.loggedInUserViewModel?.itemViewModels?.addLifecycleObserver(viewLifecycleOwner, true, itemViewModelsObserver)

        return binding?.root
    }

    private fun setupEntryList(binding: FragmentRecycleBinBinding) {
        val listAdapter = RecycleBinItemEntryAdapter { entry ->
            restoreItem(entry)
        }

        binding.recyclerViewItems.apply {
            val linearLayoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)

            layoutManager = linearLayoutManager
            adapter = listAdapter

            val verticalSpaceItemDecoration = VerticalSpaceItemDecoration(context)
            addItemDecoration(verticalSpaceItemDecoration)
        }

        toolbarMenuSearchView?.setupWithFilterableAdapter(listAdapter)
    }

    private fun restoreItem(entry: ItemEntry) {
        val itemEditingViewModel = entry.itemViewModel.createEditingViewModel()

        launchRequestSending(
            handleSuccess = { showInformation(getString(R.string.recycle_bin_restore_successful_message)) },
            handleFailure = { showError(getString(R.string.recycle_bin_restore_failed_general_title)) }
        ) {
            itemEditingViewModel.restore()
        }
    }

    private fun setupEmptyScreen(binding: FragmentRecycleBinBinding) {
        binding.layoutEmptyScreen.apply {
            imageViewIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.icon_list_24dp, root.context.theme))
            textViewTitle.text = getString(R.string.recycle_bin_empty_screen_title)
            textViewDescription.text = getString(R.string.recycle_bin_empty_screen_description)
        }
    }

    override fun onDestroyView() {
        toolbarMenuSearchView = null
        binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = RecycleBinFragment()
    }
}

private class RecycleBinItemEntryAdapter(
    private val entryRestoreCallback: (ItemEntry) -> Unit
) : FilterableListAdapter<ItemEntry, RecycleBinItemEntryAdapter.ItemEntryViewHolder>(ListItemIdentifiableDiffCallback(), createItemEntryFilterPredicate()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemEntryViewHolder {
        val binding = ListItemEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemEntryViewHolder(binding, entryRestoreCallback)
    }

    override fun onBindViewHolder(holder: ItemEntryViewHolder, position: Int) {
        getItem(position)?.let { item ->
            holder.apply {
                itemView.tag = item
                bind(item)
            }
        }
    }

    class ItemEntryViewHolder(
        private val binding: ListItemEntryBinding,
        private val entryRestoreCallback: (ItemEntry) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: ItemEntry) {
            binding.apply {
                textViewTitle.text = entry.title
                textViewSubtitle.text = entry.subtitle

                root.setOnCreateContextMenuListener { menu, _, _ ->
                    menu.add(R.string.recycle_bin_item_context_menu_restore).setOnMenuItemClickListener {
                        entryRestoreCallback.invoke(entry)
                        true
                    }
                }
            }
        }
    }
}
