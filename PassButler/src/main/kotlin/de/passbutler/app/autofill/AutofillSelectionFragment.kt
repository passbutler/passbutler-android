package de.passbutler.app.autofill

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import de.passbutler.app.ItemEntry
import de.passbutler.app.ItemEntryAdapter
import de.passbutler.app.R
import de.passbutler.app.UserViewModelProvidingViewModel
import de.passbutler.app.base.addLifecycleObserver
import de.passbutler.app.databinding.FragmentAutofillSelectionBinding
import de.passbutler.app.sorted
import de.passbutler.app.ui.BaseFragment
import de.passbutler.app.ui.visible
import de.passbutler.common.ItemViewModel
import de.passbutler.common.base.BindableObserver
import de.passbutler.common.unlockedItemData

class AutofillSelectionFragment : BaseFragment() {

    private val loggedInUserViewModel
        get() = userViewModelProvidingViewModel.loggedInUserViewModel

    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

    private val autofillMainActivity
        get() = requireActivity() as AutofillMainActivity

    private val structureParserResult
        get() = autofillMainActivity.structureParserResult

    private var binding: FragmentAutofillSelectionBinding? = null

    private val itemViewModelsObserver: BindableObserver<List<ItemViewModel>> = { newUnfilteredItemViewModels ->
        val newItemViewModels = newUnfilteredItemViewModels.filter { !it.deleted }
        val relevantAutoSelectItemViewModels = newItemViewModels.filterAutoSelectRelevantItems()

        if (relevantAutoSelectItemViewModels.isNotEmpty()) {
            autofillMainActivity.itemWasSelected(relevantAutoSelectItemViewModels)
        } else {
            val adapter = binding?.recyclerViewItemList?.adapter as? ItemEntryAdapter

            val newItemEntries = newItemViewModels
                .map { ItemEntry(it) }
                .sorted()

            adapter?.submitList(newItemEntries)

            val showEmptyScreen = newItemEntries.isEmpty()
            binding?.layoutEmptyScreen?.root?.visible = showEmptyScreen
        }
    }

    private fun List<ItemViewModel>.filterAutoSelectRelevantItems(): List<ItemViewModel> {
        return filter {
            val autofillTarget = structureParserResult.webDomain ?: structureParserResult.applicationId
            autofillTarget != null && it.unlockedItemData.url.contains(autofillTarget)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentAutofillSelectionBinding.inflate(inflater).also { binding ->
            setupToolBar(binding)
            setupEntryList(binding)
        }

        loggedInUserViewModel?.itemViewModels?.addLifecycleObserver(viewLifecycleOwner, true, itemViewModelsObserver)

        return binding?.root
    }

    private fun setupToolBar(binding: FragmentAutofillSelectionBinding) {
        binding.toolbar.apply {
            title = getString(R.string.autofill_selection_title)

            subtitle = when {
                structureParserResult.webDomain != null -> getString(R.string.autofill_selection_subtitle_website, structureParserResult.webDomain)
                structureParserResult.applicationId != null -> getString(R.string.autofill_selection_subtitle_app, structureParserResult.applicationId)
                else -> null
            }
        }
    }

    private fun setupEntryList(binding: FragmentAutofillSelectionBinding) {
        binding.recyclerViewItemList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = ItemEntryAdapter { entry ->
                autofillMainActivity.itemWasSelected(listOf(entry.itemViewModel))
            }
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