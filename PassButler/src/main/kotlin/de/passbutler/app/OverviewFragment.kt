package de.passbutler.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import de.passbutler.app.base.launchRequestSending
import de.passbutler.app.base.observe
import de.passbutler.app.base.relativeDateTime
import de.passbutler.app.databinding.FragmentOverviewBinding
import de.passbutler.app.databinding.ListItemEntryBinding
import de.passbutler.app.ui.BaseViewModelFragment
import de.passbutler.app.ui.FragmentPresenting
import de.passbutler.app.ui.ListItemIdentifiable
import de.passbutler.app.ui.ListItemIdentifiableDiffCallback
import de.passbutler.app.ui.VisibilityHideMode
import de.passbutler.app.ui.showError
import de.passbutler.app.ui.showFadeInOutAnimation
import de.passbutler.app.ui.showFragmentModally
import de.passbutler.app.ui.showInformation
import de.passbutler.app.ui.visible
import de.passbutler.common.Webservices
import de.passbutler.common.base.BindableObserver
import de.passbutler.common.database.models.LoggedInStateStorage
import de.passbutler.common.database.models.UserType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

class OverviewFragment : BaseViewModelFragment<OverviewViewModel>() {

    private var binding: FragmentOverviewBinding? = null
    private var navigationHeaderSubtitleView: TextView? = null
    private var navigationHeaderUserTypeView: TextView? = null
    private val navigationItemSelectedListener = NavigationItemSelectedListener()

    private var updateToolbarJob: Job? = null
    private var synchronizeDataRequestSendingJob: Job? = null

    private val usernameObserver = Observer<String> {
        updateNavigationHeaderSubtitleView()
    }

    private val loggedInStateStorageObserver: BindableObserver<LoggedInStateStorage?> = {
        updateToolbarSubtitle()
        updateNavigationHeaderUserTypeView()
        updateSwipeRefreshLayout()
    }

    private val itemViewModelsObserver = Observer<List<ItemViewModel>> { newItemViewModels ->
        Logger.debug("newItemViewModels.size = ${newItemViewModels.size}")

        val adapter = binding?.layoutOverviewContent?.recyclerViewItemList?.adapter as? ItemAdapter
        adapter?.submitList(newItemViewModels)

        val showEmptyScreen = newItemViewModels.isEmpty()
        binding?.layoutOverviewContent?.groupEmptyScreenViews?.visible = showEmptyScreen
    }

    private val webservicesInitializedObserver: BindableObserver<Webservices?> = {
        if (it != null) {
            synchronizeData(userTriggered = false)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        viewModel = ViewModelProvider(this).get(OverviewViewModel::class.java)

        activity?.let {
            val rootViewModel = getRootViewModel(it)
            val loggedInUserViewModel = rootViewModel.loggedInUserViewModel

            Logger.debug("Apply loggedInUserViewModel = $loggedInUserViewModel from rootViewModel = $rootViewModel to viewModel = $viewModel in $this")
            viewModel.loggedInUserViewModel = loggedInUserViewModel
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate<FragmentOverviewBinding>(inflater, R.layout.fragment_overview, container, false).also { binding ->
            binding.lifecycleOwner = viewLifecycleOwner

            setupToolBar(binding)
            setupDrawerLayout(binding)
            setupEntryList(binding)
        }

        viewModel.loggedInUserViewModel?.username?.observe(viewLifecycleOwner, true, usernameObserver)
        viewModel.loggedInUserViewModel?.loggedInStateStorage?.addObserver(viewLifecycleOwner.lifecycleScope, true, loggedInStateStorageObserver)
        viewModel.itemViewModels.observe(viewLifecycleOwner, true, itemViewModelsObserver)

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

        val navigationHeaderView = binding.navigationView.inflateHeaderView(R.layout.main_drawer_header)
        navigationHeaderSubtitleView = navigationHeaderView?.findViewById(R.id.textView_drawer_header_subtitle)
        navigationHeaderUserTypeView = navigationHeaderView?.findViewById(R.id.textView_drawer_header_usertype)
    }

    private fun setupEntryList(binding: FragmentOverviewBinding) {
        binding.layoutOverviewContent.recyclerViewItemList.apply {
            val linearLayoutManager = LinearLayoutManager(context)
            layoutManager = linearLayoutManager
            adapter = ItemAdapter(this@OverviewFragment)
        }

        binding.layoutOverviewContent.floatingActionButtonAddEntry.setOnClickListener {
            showFragment(ItemDetailFragment.newInstance(null))
        }
    }

    override fun onStart() {
        super.onStart()

        viewModel.loggedInUserViewModel?.webservices?.addObserver(viewLifecycleOwner.lifecycleScope, true, webservicesInitializedObserver)

        updateToolbarJob?.cancel()
        updateToolbarJob = launch {
            while (isActive) {
                Logger.debug("Update relative time in toolbar subtitle")

                // Update relative time in toolbar every minute
                updateToolbarSubtitle()
                delay(DateUtils.MINUTE_IN_MILLIS)
            }
        }
    }

    override fun onStop() {
        viewModel.loggedInUserViewModel?.webservices?.removeObserver(webservicesInitializedObserver)
        updateToolbarJob?.cancel()

        super.onStop()
    }

    private fun updateToolbarSubtitle() {
        binding?.toolbar?.apply {
            subtitle = if (viewModel.loggedInUserViewModel?.userType == UserType.REMOTE) {
                val newDate = viewModel.loggedInUserViewModel?.lastSuccessfulSyncDate
                val formattedLastSuccessfulSync = newDate?.relativeDateTime(context) ?: getString(R.string.overview_last_sync_never)
                getString(R.string.overview_last_sync_subtitle, formattedLastSuccessfulSync)
            } else {
                null
            }
        }
    }

    private fun updateNavigationHeaderSubtitleView() {
        navigationHeaderSubtitleView?.text = viewModel.loggedInUserViewModel?.username?.value
    }

    private fun updateNavigationHeaderUserTypeView() {
        navigationHeaderUserTypeView?.apply {
            if (viewModel.loggedInUserViewModel?.userType == UserType.LOCAL) {
                visible = true
                setOnClickListener {
                    closeDrawerDelayed()
                    showFragmentModally(RegisterLocalUserFragment.newInstance())
                }
            } else {
                visible = false
                setOnClickListener(null)
            }
        }
    }

    private fun updateSwipeRefreshLayout() {
        binding?.layoutOverviewContent?.swipeRefreshLayout?.apply {
            if (viewModel.loggedInUserViewModel?.userType == UserType.REMOTE) {
                isEnabled = true
                setOnRefreshListener {
                    if (viewModel.loggedInUserViewModel?.webservices?.value != null) {
                        synchronizeData(userTriggered = true)
                    } else {
                        // Immediately stop refreshing if is not possible
                        isRefreshing = false
                    }
                }
            } else {
                isEnabled = false
                setOnRefreshListener(null)
            }
        }
    }

    private fun synchronizeData(userTriggered: Boolean) {
        val synchronizeDataRequestRunning = synchronizeDataRequestSendingJob?.isActive ?: false

        if (!synchronizeDataRequestRunning) {
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
        } else {
            Logger.debug("The synchronize data request is already running - skip call")
        }
    }

    override fun onDestroyView() {
        binding = null
        navigationHeaderSubtitleView = null
        navigationHeaderUserTypeView = null

        viewModel.loggedInUserViewModel?.loggedInStateStorage?.removeObserver(loggedInStateStorageObserver)

        super.onDestroyView()
    }

    override fun onHandleBackPress(): Boolean {
        return if (binding?.drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
            binding?.drawerLayout?.closeDrawer(GravityCompat.START)
            true
        } else {
            super.onHandleBackPress()
        }
    }

    internal fun logoutUser() {
        launchRequestSending(
            handleFailure = { showError(getString(R.string.overview_logout_failed_title)) },
            isCancellable = false
        ) {
            viewModel.logoutUser()
        }
    }

    /**
     * Closes drawer a little delayed to make show new fragment transaction more pretty
     */
    internal fun closeDrawerDelayed() {
        launch {
            delay(100)
            binding?.drawerLayout?.closeDrawer(GravityCompat.START)
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

        private fun startExternalUriIntent(uriString: String) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.data = Uri.parse(uriString)
            startActivity(intent)
        }
    }

    companion object {
        private const val URL_HOMEPAGE = "https://sicherheitskritisch.de"
        private const val URL_GOOGLE_PLAY = "market://details?id=de.passbutler.app"

        fun newInstance() = OverviewFragment()
    }
}

class ItemAdapter(private val fragmentPresenter: FragmentPresenting) : ListAdapter<ListItemIdentifiable, ItemAdapter.ItemViewHolder>(ListItemIdentifiableDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = DataBindingUtil.inflate<ListItemEntryBinding>(LayoutInflater.from(parent.context), R.layout.list_item_entry, parent, false)
        return ItemViewHolder(binding, fragmentPresenter)
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
        private val fragmentPresenter: FragmentPresenting
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(itemViewModel: ItemViewModel) {
            binding.apply {
                textViewTitle.text = itemViewModel.title.value
                textViewSubtitle.text = itemViewModel.subtitle

                root.setOnClickListener {
                    fragmentPresenter.showFragment(ItemDetailFragment.newInstance(itemViewModel.id))
                }
            }
        }
    }
}