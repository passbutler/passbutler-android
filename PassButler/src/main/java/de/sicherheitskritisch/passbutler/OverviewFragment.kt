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
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import de.sicherheitskritisch.passbutler.base.RequestSendingViewHandler
import de.sicherheitskritisch.passbutler.base.RequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.createMainDispatcher
import de.sicherheitskritisch.passbutler.base.signal
import de.sicherheitskritisch.passbutler.databinding.FragmentOverviewBinding
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.VisibilityHideMode
import de.sicherheitskritisch.passbutler.ui.applyTint
import de.sicherheitskritisch.passbutler.ui.showFadeInOutAnimation
import de.sicherheitskritisch.passbutler.ui.showFragmentAsFirstScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext

class OverviewFragment : BaseViewModelFragment<OverviewViewModel>(), AnimatedFragment, CoroutineScope {

    override val transitionType = AnimatedFragment.TransitionType.SLIDE

    override val coroutineContext: CoroutineContext
        get() = createMainDispatcher() + coroutineJob

    private val coroutineJob = SupervisorJob()

    private var loggedInUserViewModel: UserViewModel? = null

    private var synchronizeDataRequestSendingViewHandler: SynchronizeDataRequestSendingViewHandler? = null
    private var logoutRequestSendingViewHandler: LogoutRequestSendingViewHandler? = null

    private var binding: FragmentOverviewBinding? = null
    private var navigationHeaderView: View? = null
    private val navigationItemSelectedListener = NavigationItemSelectedListener()

    private val toolbarMenuIconSync
        get() = binding?.toolbar?.menu?.findItem(R.id.overview_menu_item_sync)

    private val unlockedFinishedSignal = signal {
        if (viewModel.userType is UserType.Server) {
            // Explicitly dispatch to IO to be independent which dispatcher calls the signal
            launch(Dispatchers.IO) {
                delay(500)
                viewModel.synchronizeData()
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        viewModel = ViewModelProviders.of(this).get(OverviewViewModel::class.java)

        activity?.let {
            val rootViewModel = getRootViewModel(it)
            loggedInUserViewModel = rootViewModel.loggedInUserViewModel
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        synchronizeDataRequestSendingViewHandler = SynchronizeDataRequestSendingViewHandler(viewModel.synchronizeDataRequestSendingViewModel, WeakReference(this)).apply {
            registerObservers()
        }

        logoutRequestSendingViewHandler = LogoutRequestSendingViewHandler(viewModel.logoutRequestSendingViewModel, WeakReference(this)).apply {
            registerObservers()
        }

        loggedInUserViewModel?.unlockFinished?.addSignal(unlockedFinishedSignal)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = DataBindingUtil.inflate<FragmentOverviewBinding>(inflater, R.layout.fragment_overview, container, false).also { binding ->
            binding.lifecycleOwner = this

            setupToolBar(binding)
            setupDrawerLayout(binding)

            this.binding = binding
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
                    viewModel.synchronizeData()
                    true
                }
                else -> false
            }
        }

        toolbar.menu.findItem(R.id.overview_menu_item_sync).apply {
            val menuIconColor = resources.getColor(R.color.white, null)
            icon.applyTint(menuIconColor)
            isVisible = viewModel.userType is UserType.Server
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
            subtitleView.text = loggedInUserViewModel?.username
        }
    }

    override fun onHandleBackPress(): Boolean {
        return if (binding?.drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
            binding?.drawerLayout?.closeDrawer(GravityCompat.START)
            true
        } else {
            super.onHandleBackPress()
        }
    }

    override fun onDestroy() {
        synchronizeDataRequestSendingViewHandler?.unregisterObservers()
        logoutRequestSendingViewHandler?.unregisterObservers()
        loggedInUserViewModel?.unlockFinished?.removeSignal(unlockedFinishedSignal)
        coroutineJob.cancel()
        super.onDestroy()
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
                    viewModel.logoutUser()
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

    private class SynchronizeDataRequestSendingViewHandler(
        requestSendingViewModel: RequestSendingViewModel,
        private val fragmentWeakReference: WeakReference<OverviewFragment>
    ) : RequestSendingViewHandler(requestSendingViewModel) {

        private val fragment
            get() = fragmentWeakReference.get()

        private val binding
            get() = fragment?.binding

        private val resources
            get() = fragment?.resources

        override fun onIsLoadingChanged(isLoading: Boolean) {
            fragment?.toolbarMenuIconSync?.apply {
                isEnabled = !isLoading

                val menuIconTintColor = when (isLoading) {
                    true -> resources?.getColor(R.color.whiteDisabled, null)
                    false -> resources?.getColor(R.color.white, null)
                }
                menuIconTintColor?.let {
                    icon?.applyTint(it)
                }
            }

            binding?.layoutOverviewContent?.progressBarRefreshing?.showFadeInOutAnimation(isLoading, VisibilityHideMode.INVISIBLE)
        }

        override fun onRequestErrorChanged(requestError: Throwable) {
            binding?.layoutOverviewContent?.let {
                resources?.getString(R.string.overview_sync_failed_message)?.let { snackbarMessage ->
                    Snackbar.make(it.root, snackbarMessage, Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        override fun onRequestFinishedSuccessfully() {
            binding?.layoutOverviewContent?.let {
                resources?.getString(R.string.overview_sync_successful_message)?.let { snackbarMessage ->
                    Snackbar.make(it.root, snackbarMessage, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private class LogoutRequestSendingViewHandler(
        requestSendingViewModel: RequestSendingViewModel,
        private val fragmentWeakReference: WeakReference<OverviewFragment>
    ) : RequestSendingViewHandler(requestSendingViewModel) {

        private val fragment
            get() = fragmentWeakReference.get()

        private val binding
            get() = fragment?.binding

        private val resources
            get() = fragment?.resources

        override fun onIsLoadingChanged(isLoading: Boolean) {
            if (isLoading) {
                fragment?.showProgress()
            } else {
                fragment?.hideProgress()
            }
        }

        override fun onRequestErrorChanged(requestError: Throwable) {
            binding?.layoutOverviewContent?.root?.let {
                resources?.getString(R.string.overview_logout_failed_title)?.let { snackbarMessage ->
                    Snackbar.make(it, snackbarMessage, Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        override fun onRequestFinishedSuccessfully() {
            val loginFragment = LoginFragment.newInstance()
            fragment?.showFragmentAsFirstScreen(loginFragment)
        }
    }

    companion object {
        private const val URL_HOMEPAGE = "https://sicherheitskritisch.de"
        private const val URL_GOOGLE_PLAY = "market://details?id=de.sicherheitskritisch.passbutler"

        fun newInstance() = OverviewFragment()
    }
}
