package de.passbutler.app.autofill

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import de.passbutler.app.ItemEntry
import de.passbutler.app.ItemEntryAdapter
import de.passbutler.app.R
import de.passbutler.app.UserViewModelProvidingViewModel
import de.passbutler.app.databinding.FragmentAutofillSelectionBinding
import de.passbutler.app.ui.BaseFragment
import de.passbutler.app.ui.addLifecycleObserver
import de.passbutler.app.ui.setupWithFilterableAdapter
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

    private var toolbarMenuSearchView: SearchView? = null
    private var binding: FragmentAutofillSelectionBinding? = null

    private val itemViewModelsObserver: BindableObserver<List<ItemViewModel>> = { newUnfilteredItemViewModels ->
        val newItemViewModels = newUnfilteredItemViewModels.filter { !it.deleted }
        val relevantAutoSelectItemViewModels = newItemViewModels.filterAutoSelectRelevantItems()

        if (relevantAutoSelectItemViewModels.isNotEmpty()) {
            autofillMainActivity.itemWasSelected(relevantAutoSelectItemViewModels)
        } else {
            val adapter = binding?.recyclerViewItems?.adapter as? ItemEntryAdapter

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
            autofillTarget?.isNotEmpty() == true && it.unlockedItemData.url.contains(autofillTarget)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentAutofillSelectionBinding.inflate(inflater, container, false).also { binding ->
            setupToolBar(binding)
            setupEntryList(binding)
            setupEmptyScreen(binding)
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

            setupToolbarMenu(this)
        }
    }

    private fun setupToolbarMenu(toolbar: Toolbar) {
        toolbar.inflateMenu(R.menu.item_search_menu)

        toolbarMenuSearchView = (toolbar.menu.findItem(R.id.item_search_menu_item_search)?.actionView as SearchView).apply {
            queryHint = getString(R.string.general_search)
        }
    }

    private fun setupEntryList(binding: FragmentAutofillSelectionBinding) {
        val listAdapter = ItemEntryAdapter(
            entryClickedCallback = { entry -> autofillMainActivity.itemWasSelected(listOf(entry.itemViewModel)) }
        )

        binding.recyclerViewItems.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = listAdapter
        }

        toolbarMenuSearchView?.setupWithFilterableAdapter(listAdapter)
    }

    private fun setupEmptyScreen(binding: FragmentAutofillSelectionBinding) {
        binding.layoutEmptyScreen.apply {
            imageViewIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.icon_list_24dp, root.context.theme))
            textViewTitle.text = getString(R.string.overview_empty_screen_title)
            textViewDescription.text = getString(R.string.overview_empty_screen_description)
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
