package de.passbutler.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.passbutler.app.databinding.FragmentRecycleBinBinding
import de.passbutler.app.databinding.ListItemEntryBinding
import de.passbutler.app.ui.ListItemIdentifiableDiffCallback
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.app.ui.addLifecycleObserver
import de.passbutler.app.ui.visible
import de.passbutler.common.ItemViewModel
import de.passbutler.common.base.BindableObserver
import de.passbutler.common.ui.ListItemIdentifiable
import de.passbutler.common.ui.RequestSending
import de.passbutler.common.ui.launchRequestSending
import org.tinylog.kotlin.Logger

class RecycleBinFragment : ToolBarFragment(), RequestSending {

    private val viewModel by userViewModelUsingViewModels<RecycleBinViewModel>(userViewModelProvidingViewModel = { userViewModelProvidingViewModel })
    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

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
    }

    override fun getToolBarTitle() = getString(R.string.recycle_bin_title)

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentRecycleBinBinding.inflate(inflater, container, false).also { binding ->
            setupEntryList(binding)
            setupEmptyScreen(binding)
        }

        viewModel.loggedInUserViewModel?.itemViewModels?.addLifecycleObserver(viewLifecycleOwner, true, itemViewModelsObserver)

        return binding?.root
    }

    private fun setupEntryList(binding: FragmentRecycleBinBinding) {
        binding.recyclerViewItems.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = RecycleBinItemEntryAdapter { entry ->
                restoreItem(entry)
            }
        }
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

    companion object {
        fun newInstance() = RecycleBinFragment()
    }
}

class RecycleBinItemEntryAdapter(
    private val entryRestoreCallback: (ItemEntry) -> Unit
) : ListAdapter<ListItemIdentifiable, RecycleBinItemEntryAdapter.ItemEntryViewHolder>(ListItemIdentifiableDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemEntryViewHolder {
        val binding = ListItemEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemEntryViewHolder(binding, entryRestoreCallback)
    }

    override fun onBindViewHolder(holder: ItemEntryViewHolder, position: Int) {
        (getItem(position) as? ItemEntry)?.let { item ->
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
                textViewTitle.text = entry.itemViewModel.title
                textViewSubtitle.text = entry.itemViewModel.subtitle

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
