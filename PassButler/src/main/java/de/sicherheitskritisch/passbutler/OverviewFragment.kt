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
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import de.sicherheitskritisch.passbutler.base.launchRequestSending
import de.sicherheitskritisch.passbutler.base.observe
import de.sicherheitskritisch.passbutler.base.relativeDateTime
import de.sicherheitskritisch.passbutler.base.signal
import de.sicherheitskritisch.passbutler.databinding.FragmentOverviewBinding
import de.sicherheitskritisch.passbutler.databinding.ListItemEntryBinding
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.VisibilityHideMode
import de.sicherheitskritisch.passbutler.ui.applyTint
import de.sicherheitskritisch.passbutler.ui.showError
import de.sicherheitskritisch.passbutler.ui.showFadeInOutAnimation
import de.sicherheitskritisch.passbutler.ui.showInformation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class OverviewFragment : BaseViewModelFragment<OverviewViewModel>(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.SLIDE

    private var binding: FragmentOverviewBinding? = null
    private var navigationHeaderView: View? = null
    private val navigationItemSelectedListener = NavigationItemSelectedListener()

    private val toolbarMenuIconSync
        get() = binding?.toolbar?.menu?.findItem(R.id.overview_menu_item_sync)

    private var synchronizeDataRequestSendingJob: Job? = null
    private var logoutRequestSendingJob: Job? = null

    private val webserviceRestoredSignal = signal {
        // Start sync a bit delayed after unlock to made progress UI better visible
        synchronizeData(startDelay = 500)
    }

    private val itemsChangedObserver = Observer<List<ItemViewModel>> { newItems ->
        if (newItems != null) {
            val adapter = binding?.layoutOverviewContent?.recyclerViewItemList?.adapter as? ItemAdapter
            adapter?.submitList(newItems)
        }
    }

    private val lastSuccessfulSyncChangedObserver = Observer<Date?> { newDate ->
        binding?.toolbar?.let { toolbar ->
            binding?.toolbar?.subtitle = if (viewModel.loggedInUserViewModel?.userType is UserType.Server) {
                val formattedLastSuccessfulSync = newDate?.relativeDateTime(toolbar.context) ?: getString(R.string.overview_last_sync_never)
                getString(R.string.overview_last_sync_subtitle, formattedLastSuccessfulSync)
            } else {
                null
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        viewModel = ViewModelProviders.of(this).get(OverviewViewModel::class.java)

        activity?.let {
            viewModel.rootViewModel = getRootViewModel(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.binding = DataBindingUtil.inflate<FragmentOverviewBinding>(inflater, R.layout.fragment_overview, container, false).also { binding ->
            binding.lifecycleOwner = viewLifecycleOwner

            setupToolBar(binding)
            setupDrawerLayout(binding)
            setupEntryList(binding)
        }

        return binding?.root
    }

    private fun setupToolBar(binding: FragmentOverviewBinding) {
        binding.toolbar.apply {
            title = getString(R.string.app_name)
            setupToolbarMenu(this)
        }
    }

    private fun setupToolbarMenu(toolbar: Toolbar) {
        toolbar.inflateMenu(R.menu.overview_menu)

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.overview_menu_item_sync -> {
                    synchronizeData()
                    true
                }
                else -> false
            }
        }

        toolbar.menu.findItem(R.id.overview_menu_item_sync).apply {
            val menuIconColor = resources.getColor(R.color.white, null)
            icon.applyTint(menuIconColor)
            isVisible = viewModel.loggedInUserViewModel?.userType is UserType.Server
        }
    }

    private fun synchronizeData(startDelay: Long = 0) {
        synchronizeDataRequestSendingJob?.cancel()
        synchronizeDataRequestSendingJob = launchRequestSending(
            handleSuccess = { showInformation(getString(R.string.overview_sync_successful_message)) },
            handleFailure = { showError(getString(R.string.overview_sync_failed_message)) },
            handleLoadingChanged = { isLoading ->
                toolbarMenuIconSync?.apply {
                    isEnabled = !isLoading

                    val menuIconTintColor = when (isLoading) {
                        true -> resources.getColor(R.color.whiteDisabled, null)
                        false -> resources.getColor(R.color.white, null)
                    }
                    icon?.applyTint(menuIconTintColor)
                }

                binding?.layoutOverviewContent?.progressBarRefreshing?.showFadeInOutAnimation(isLoading, VisibilityHideMode.INVISIBLE)
            }
        ) {
            delay(startDelay)
            viewModel.synchronizeData()
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

    private fun setupEntryList(binding: FragmentOverviewBinding) {
        binding.layoutOverviewContent.recyclerViewItemList.adapter = ItemAdapter(this)

        binding.layoutOverviewContent.floatingActionButtonAddEntry.setOnClickListener {
            showFragment(ItemDetailFragment.newInstance(null))
        }
    }

    override fun onStart() {
        super.onStart()

        viewModel.rootViewModel.webserviceRestored.addSignal(webserviceRestoredSignal)
        viewModel.loggedInUserViewModel?.itemViewModels?.observe(viewLifecycleOwner, itemsChangedObserver)
        viewModel.loggedInUserViewModel?.lastSuccessfulSync?.observe(viewLifecycleOwner, true, lastSuccessfulSyncChangedObserver)
    }

    override fun onStop() {
        viewModel.rootViewModel.webserviceRestored.removeSignal(webserviceRestoredSignal)

        super.onStop()
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
                    showFragment(SettingsFragment.newInstance())
                    true
                }
                R.id.drawer_menu_item_about -> {
                    showFragment(AboutFragment.newInstance())
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