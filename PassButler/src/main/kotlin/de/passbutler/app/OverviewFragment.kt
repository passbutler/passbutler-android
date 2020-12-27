package de.passbutler.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import de.passbutler.app.base.createRelativeDateFormattingTranslations
import de.passbutler.app.databinding.FragmentOverviewBinding
import de.passbutler.app.databinding.ListItemEntryBinding
import de.passbutler.app.ui.BaseFragment
import de.passbutler.app.ui.ListItemIdentifiableDiffCallback
import de.passbutler.app.ui.VisibilityHideMode
import de.passbutler.app.ui.addLifecycleObserver
import de.passbutler.app.ui.showFadeInOutAnimation
import de.passbutler.app.ui.showFragmentModally
import de.passbutler.app.ui.visible
import de.passbutler.common.ItemViewModel
import de.passbutler.common.Webservices
import de.passbutler.common.base.BindableObserver
import de.passbutler.common.base.formattedRelativeDateTime
import de.passbutler.common.database.models.UserType
import de.passbutler.common.ui.ListItemIdentifiable
import de.passbutler.common.ui.RequestSending
import de.passbutler.common.ui.launchRequestSending
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

class OverviewFragment : BaseFragment(), RequestSending {

    private val viewModel by userViewModelUsingViewModels<OverviewViewModel>(userViewModelProvidingViewModel = { userViewModelProvidingViewModel })
    private val rootViewModel by userViewModelUsingActivityViewModels<RootViewModel>(userViewModelProvidingViewModel = { userViewModelProvidingViewModel })
    private val userViewModelProvidingViewModel by activityViewModels<UserViewModelProvidingViewModel>()

    private var binding: FragmentOverviewBinding? = null
    private var navigationHeaderSubtitleView: TextView? = null
    private var navigationHeaderUserTypeView: TextView? = null
    private val navigationItemSelectedListener = NavigationItemSelectedListener()

    private var updateToolbarJob: Job? = null
    private var synchronizeDataRequestSendingJob: Job? = null

    private val itemViewModelsObserver: BindableObserver<List<ItemViewModel>> = { newUnfilteredItemViewModels ->
        // Only show non-deleted items
        val newItemViewModels = newUnfilteredItemViewModels.filter { !it.deleted }
        Logger.debug("newItemViewModels.size = ${newItemViewModels.size}")

        val adapter = binding?.layoutOverviewContent?.recyclerViewItemList?.adapter as? ItemEntryAdapter

        val newItemEntries = newItemViewModels
            .map { ItemEntry(it) }
            .sorted()

        adapter?.submitList(newItemEntries)

        val showEmptyScreen = newItemEntries.isEmpty()
        binding?.layoutOverviewContent?.layoutEmptyScreen?.root?.visible = showEmptyScreen
    }

    private val webservicesInitializedObserver: BindableObserver<Webservices?> = {
        if (it != null) {
            synchronizeData(userTriggered = false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentOverviewBinding.inflate(inflater, container, false).also { binding ->
            setupToolBar(binding)
            setupDrawerLayout(binding)
            setupEntryList(binding)
            setupEmptyScreen(binding)
        }

        val loggedInUserViewModel = viewModel.loggedInUserViewModel
        Logger.debug("loggedInUserViewModel = $loggedInUserViewModel")

        loggedInUserViewModel?.username?.addLifecycleObserver(viewLifecycleOwner, true) {
            navigationHeaderSubtitleView?.text = it
        }

        loggedInUserViewModel?.loggedInStateStorage?.addLifecycleObserver(viewLifecycleOwner, true) {
            updateToolbarSubtitle()
            updateNavigationHeaderUserTypeView()
            updateSwipeRefreshLayout()
        }

        loggedInUserViewModel?.itemViewModels?.addLifecycleObserver(viewLifecycleOwner, true, itemViewModelsObserver)

        return binding?.root
    }

    private fun setupToolBar(binding: FragmentOverviewBinding) {
        binding.toolbar.apply {
            title = getString(R.string.general_app_name)
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
            layoutManager = LinearLayoutManager(context)
            adapter = ItemEntryAdapter { entry ->
                showFragment(ItemDetailFragment.newInstance(entry.itemViewModel.id))
            }
        }

        binding.layoutOverviewContent.floatingActionButtonAddEntry.setOnClickListener {
            showFragment(ItemDetailFragment.newInstance(null))
        }
    }

    private fun setupEmptyScreen(binding: FragmentOverviewBinding) {
        binding.layoutOverviewContent.layoutEmptyScreen.apply {
            imageViewIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.icon_list_24dp, root.context.theme))
            textViewTitle.text = getString(R.string.overview_empty_screen_title)
            textViewDescription.text = getString(R.string.overview_empty_screen_description)
        }
    }

    private fun updateToolbarSubtitle() {
        binding?.toolbar?.apply {
            subtitle = if (viewModel.loggedInUserViewModel?.userType == UserType.REMOTE) {
                val newDate = viewModel.loggedInUserViewModel?.lastSuccessfulSyncDate
                val relativeDateFormattingTranslations = createRelativeDateFormattingTranslations(requireContext())
                val formattedLastSuccessfulSync = newDate?.formattedRelativeDateTime(relativeDateFormattingTranslations) ?: getString(R.string.overview_last_sync_never)
                getString(R.string.overview_last_sync_subtitle, formattedLastSuccessfulSync)
            } else {
                null
            }
        }
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

    override fun onStart() {
        super.onStart()

        viewModel.loggedInUserViewModel?.webservices?.addObserver(viewLifecycleOwner.lifecycleScope, true, webservicesInitializedObserver)

        updateToolbarJob?.cancel()
        updateToolbarJob = launch {
            while (isActive) {
                Logger.debug("Update relative time in toolbar subtitle")

                // Update relative time in toolbar periodically
                updateToolbarSubtitle()
                delay(10_000)
            }
        }
    }

    override fun onStop() {
        viewModel.loggedInUserViewModel?.webservices?.removeObserver(webservicesInitializedObserver)
        updateToolbarJob?.cancel()

        super.onStop()
    }

    override fun onDestroyView() {
        binding = null
        navigationHeaderSubtitleView = null
        navigationHeaderUserTypeView = null

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
            rootViewModel.closeVault()
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

class ItemEntryAdapter(private val entryClickedCallback: (ItemEntry) -> Unit) : ListAdapter<ListItemIdentifiable, ItemEntryAdapter.ItemEntryViewHolder>(ListItemIdentifiableDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemEntryViewHolder {
        val binding = ListItemEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemEntryViewHolder(binding, entryClickedCallback)
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
        private val entryClickedCallback: (ItemEntry) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: ItemEntry) {
            binding.apply {
                textViewTitle.text = entry.itemViewModel.title
                textViewSubtitle.text = entry.itemViewModel.subtitle

                root.setOnClickListener {
                    entryClickedCallback.invoke(entry)
                }
            }
        }
    }
}

class ItemEntry(val itemViewModel: ItemViewModel) : ListItemIdentifiable {
    override val listItemId: String
        get() = itemViewModel.id
}

fun List<ItemEntry>.sorted(): List<ItemEntry> {
    return sortedBy { it.itemViewModel.title }
}