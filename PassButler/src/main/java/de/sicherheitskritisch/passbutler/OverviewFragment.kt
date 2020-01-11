package de.sicherheitskritisch.passbutler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.launchRequestSending
import de.sicherheitskritisch.passbutler.base.observe
import de.sicherheitskritisch.passbutler.base.relativeDateTime
import de.sicherheitskritisch.passbutler.databinding.FragmentOverviewBinding
import de.sicherheitskritisch.passbutler.databinding.ListItemEntryBinding
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.VisibilityHideMode
import de.sicherheitskritisch.passbutler.ui.showError
import de.sicherheitskritisch.passbutler.ui.showFadeInOutAnimation
import de.sicherheitskritisch.passbutler.ui.showFragmentModally
import de.sicherheitskritisch.passbutler.ui.showInformation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class OverviewFragment : BaseViewModelFragment<OverviewViewModel>() {

    private var binding: FragmentOverviewBinding? = null
    private var navigationHeaderView: View? = null
    private val navigationItemSelectedListener = NavigationItemSelectedListener()

    private val isSynchronizationVisible
        get() = viewModel.loggedInUserViewModel?.isServerUserType ?: false

    private val isSynchronizationPossible
        get() = viewModel.loggedInUserViewModel?.isSynchronizationPossible?.value ?: false

    private var synchronizeDataRequestSendingJob: Job? = null
    private var logoutRequestSendingJob: Job? = null

    private val itemViewModelsChangedObserver = Observer<List<ItemViewModel>> { newItemViewModels ->
        L.d("OverviewFragment", "itemViewModelsChangedObserver(): newItemViewModels.size = ${newItemViewModels.size}")

        val adapter = binding?.layoutOverviewContent?.recyclerViewItemList?.adapter as? ItemAdapter
        adapter?.submitList(newItemViewModels)
    }

    private val lastSuccessfulSyncChangedObserver = Observer<Date?> { newDate ->
        binding?.toolbar?.let { toolbar ->
            binding?.toolbar?.subtitle = if (isSynchronizationVisible) {
                val formattedLastSuccessfulSync = newDate?.relativeDateTime(toolbar.context) ?: getString(R.string.overview_last_sync_never)
                getString(R.string.overview_last_sync_subtitle, formattedLastSuccessfulSync)
            } else {
                null
            }
        }
    }

    private val synchronizationPossibleChangedObserver = Observer<Boolean> { isPossible ->
        if (isPossible) {
            synchronizeData(userTriggered = false)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        viewModel = ViewModelProviders.of(this).get(OverviewViewModel::class.java)

        activity?.let {
            val loggedInUserViewModel = getRootViewModel(it).loggedInUserViewModel
            L.d("OverviewFragment", "onAttach(): Apply loggedInUserViewModel = $loggedInUserViewModel to viewModel = $viewModel")
            viewModel.loggedInUserViewModel = loggedInUserViewModel
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.binding = DataBindingUtil.inflate<FragmentOverviewBinding>(inflater, R.layout.fragment_overview, container, false).also { binding ->
            binding.lifecycleOwner = viewLifecycleOwner

            setupToolBar(binding)
            setupDrawerLayout(binding)
            setupSwipeRefreshLayout(binding)
            setupEntryList(binding)
        }

        return binding?.root
    }

    private fun setupToolBar(binding: FragmentOverviewBinding) {
        binding.toolbar.apply {
            title = getString(R.string.app_name)
        }
    }

    private fun setupDrawerLayout(binding: FragmentOverviewBinding) {
        binding.drawerLayout.apply {
            val toggle = ActionBarDrawerToggle(
                activity,
                this,
                binding.toolbar,
                R.string.drawer_open_description,
                R.string.drawer_close_description
            )
            addDrawerListener(toggle)
            toggle.syncState()
        }

        binding.navigationView.apply {
            setNavigationItemSelectedListener(navigationItemSelectedListener)
        }

        navigationHeaderView = binding.navigationView.inflateHeaderView(R.layout.main_drawer_header)
        navigationHeaderView?.findViewById<TextView>(R.id.textView_drawer_header_subtitle)?.also { subtitleView ->
            subtitleView.text = viewModel.loggedInUserViewModel?.username
        }
    }

    private fun setupSwipeRefreshLayout(binding: FragmentOverviewBinding) {
        val swipeRefreshLayout = binding.layoutOverviewContent.swipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener {
            if (isSynchronizationPossible) {
                synchronizeData(userTriggered = true)
            } else {
                // Immediately stop refreshing if is not possible
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun synchronizeData(userTriggered: Boolean) {
        synchronizeDataRequestSendingJob?.cancel()
        synchronizeDataRequestSendingJob = launchRequestSending(
            handleSuccess = {
                // Only show user feedback if it was user triggered to avoid confusing the user
                if (userTriggered) {
                    showInformation(getString(R.string.overview_sync_successful_message))
                }
            },
            handleFailure = {
                // Only show user feedback if it was user triggered to avoid confusing the user
                if (userTriggered) {
                    showError(getString(R.string.overview_sync_failed_message))
                }
            },
            handleLoadingChanged = { isLoading ->
                binding?.layoutOverviewContent?.progressBarRefreshing?.showFadeInOutAnimation(isLoading, VisibilityHideMode.INVISIBLE)

                if (!isLoading) {
                    binding?.layoutOverviewContent?.swipeRefreshLayout?.isRefreshing = false
                }
            }
        ) {
            viewModel.synchronizeData()
        }
    }

    private fun setupEntryList(binding: FragmentOverviewBinding) {
        binding.layoutOverviewContent.recyclerViewItemList.adapter = ItemAdapter(this)

        binding.layoutOverviewContent.floatingActionButtonAddEntry.setOnClickListener {
            showFragment(ItemDetailFragment.newInstance(null))
        }
    }

    override fun onStart() {
        super.onStart()

        viewModel.itemViewModels.observe(viewLifecycleOwner, true, itemViewModelsChangedObserver)
        viewModel.loggedInUserViewModel?.lastSuccessfulSync?.observe(viewLifecycleOwner, true, lastSuccessfulSyncChangedObserver)
        viewModel.loggedInUserViewModel?.isSynchronizationPossible?.observe(viewLifecycleOwner, true, synchronizationPossibleChangedObserver)
    }

    override fun onHandleBackPress(): Boolean {
        return if (binding?.drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
            binding?.drawerLayout?.closeDrawer(GravityCompat.START)
            true
        } else {
            super.onHandleBackPress()
        }
    }

    private inner class NavigationItemSelectedListener : NavigationView.OnNavigationItemSelectedListener {

        override fun onNavigationItemSelected(item: MenuItem): Boolean {
            val shouldCloseDrawer = when (item.itemId) {
                R.id.drawer_menu_item_overview -> {
                    // Do nothing except close drawer
                    true
                }
                R.id.drawer_menu_item_settings -> {
                    showFragmentModally(SettingsFragment.newInstance())
                    true
                }
                R.id.drawer_menu_item_about -> {
                    showFragmentModally(AboutFragment.newInstance())
                    true
                }
                R.id.drawer_menu_item_logout -> {
                    logoutUser()
                    true
                }
                R.id.drawer_menu_item_homepage -> {
                    startExternalUriIntent(URL_HOMEPAGE)
                    false
                }
                R.id.drawer_menu_item_googleplay -> {
                    startExternalUriIntent(URL_GOOGLE_PLAY)
                    false
                }
                else -> true
            }

            if (shouldCloseDrawer) {
                closeDrawerDelayed()
            }

            return true
        }

        private fun logoutUser() {
            logoutRequestSendingJob?.cancel()
            logoutRequestSendingJob = launchRequestSending(
                handleFailure = { showError(getString(R.string.overview_logout_failed_title)) }
            ) {
                viewModel.logoutUser()
            }
        }

        /**
         * Closes drawer a little delayed to make show new fragment transaction more pretty
         */
        private fun closeDrawerDelayed() {
            launch {
                delay(100)
                binding?.drawerLayout?.closeDrawer(GravityCompat.START)
            }
        }

        private fun startExternalUriIntent(uriString: String) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.data = Uri.parse(uriString)
            startActivity(intent)
        }
    }

    companion object {
        private const val URL_HOMEPAGE = "https://sicherheitskritisch.de"
        private const val URL_GOOGLE_PLAY = "market://details?id=de.sicherheitskritisch.passbutler"

        fun newInstance() = OverviewFragment()
    }
}

class ItemAdapter(private val overviewFragment: OverviewFragment) : ListAdapter<ItemViewModel, ItemAdapter.EntryViewHolder>(ItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val binding = DataBindingUtil.inflate<ListItemEntryBinding>(LayoutInflater.from(parent.context), R.layout.list_item_entry, parent, false)
        return EntryViewHolder(binding, overviewFragment)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        getItem(position).let { item ->
            holder.apply {
                itemView.tag = item
                bind(item)
            }
        }
    }

    class EntryViewHolder(
        private val binding: ListItemEntryBinding,
        private val overviewFragment: OverviewFragment
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(itemViewModel: ItemViewModel) {
            binding.apply {
                lifecycleOwner = overviewFragment.viewLifecycleOwner
                viewModel = itemViewModel

                executePendingBindings()

                root.setOnClickListener {
                    overviewFragment.showFragment(ItemDetailFragment.newInstance(itemViewModel.id))
                }
            }
        }
    }
}

private class ItemDiffCallback : DiffUtil.ItemCallback<ItemViewModel>() {
    override fun areItemsTheSame(oldItem: ItemViewModel, newItem: ItemViewModel): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ItemViewModel, newItem: ItemViewModel): Boolean {
        return oldItem == newItem
    }
}