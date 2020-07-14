package de.passbutler.app.autofill

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.passbutler.app.ItemViewModel
import de.passbutler.app.R
import de.passbutler.app.UserViewModelProvidingViewModel
import de.passbutler.app.base.observe
import de.passbutler.app.databinding.FragmentAutofillSelectionBinding
import de.passbutler.app.databinding.ListItemEntryBinding
import de.passbutler.app.ui.BaseFragment
import de.passbutler.app.ui.ListItemIdentifiable
import de.passbutler.app.ui.ListItemIdentifiableDiffCallback
import de.passbutler.app.ui.visible
import de.passbutler.app.unlockedItemData
import org.tinylog.kotlin.Logger

class AutofillSelectionFragment : BaseFragment() {

    private val loggedInUserViewModel
        get() = userViewModelProvidingViewModel.loggedInUserViewModel

    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

    private val autofillMainActivity
        get() = requireActivity() as AutofillMainActivity

    private val structureParserResult
        get() = autofillMainActivity.structureParserResult

    private val autofillTarget
        get() = structureParserResult.webDomain ?: structureParserResult.applicationId

    private var binding: FragmentAutofillSelectionBinding? = null

    private val itemViewModelsObserver = Observer<List<ItemViewModel>> { newUnfilteredItemViewModels ->
        val newItemViewModels = newUnfilteredItemViewModels.filter { !it.deleted }
        val relevantItemViewModels = newItemViewModels.filter {
            val autofillTarget = autofillTarget
            autofillTarget != null && it.unlockedItemData.url.contains(autofillTarget)
        }

        // TODO: Allow manual selection
        if (relevantItemViewModels.isNotEmpty()) {
            autofillMainActivity.itemWasSelected(relevantItemViewModels)
        } else {
            val adapter = binding?.recyclerViewItemList?.adapter as? AutofillSelectionItemAdapter
            adapter?.submitList(newItemViewModels)

            val showEmptyScreen = newItemViewModels.isEmpty()
            binding?.layoutEmptyScreen?.root?.visible = showEmptyScreen
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentAutofillSelectionBinding.inflate(inflater).also { binding ->
            setupToolBar(binding)
            setupEntryList(binding)
        }

        loggedInUserViewModel?.itemViewModels?.observe(viewLifecycleOwner, true, itemViewModelsObserver)

        return binding?.root
    }

    private fun setupToolBar(binding: FragmentAutofillSelectionBinding) {
        binding.toolbar.apply {
            title = getString(R.string.autofill_selection_title)
            subtitle = autofillTarget?.let {
                getString(R.string.autofill_selection_subtitle, it)
            }
        }
    }

    private fun setupEntryList(binding: FragmentAutofillSelectionBinding) {
        binding.recyclerViewItemList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = AutofillSelectionItemAdapter(autofillMainActivity)
        }
    }

    override fun onDestroyView() {
        binding = null

        super.onDestroyView()
    }

    companion object {
        fun newInstance() = AutofillSelectionFragment()
    }
}

class AutofillSelectionItemAdapter(
    private val autofillMainActivity: AutofillMainActivity
) : ListAdapter<ListItemIdentifiable, AutofillSelectionItemAdapter.ItemViewHolder>(ListItemIdentifiableDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ListItemEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewHolder(binding, autofillMainActivity)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        (getItem(position) as? ItemViewModel)?.let { item ->
            holder.apply {
                itemView.tag = item
                bind(item)
            }
        }
    }

    class ItemViewHolder(
        private val binding: ListItemEntryBinding,
        private val autofillMainActivity: AutofillMainActivity
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(itemViewModel: ItemViewModel) {
            binding.apply {
                textViewTitle.text = itemViewModel.title.value
                textViewSubtitle.text = itemViewModel.subtitle

                root.setOnClickListener {
                    autofillMainActivity.itemWasSelected(listOf(itemViewModel))
                }
            }
        }
    }
}