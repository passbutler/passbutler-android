package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OverviewFragment : BaseViewModelFragment<OverviewViewModel>(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.SLIDE_HORIZONTAL

    private var toolBar: Toolbar? = null
    private var drawerLayout: DrawerLayout? = null
    private var navigationView: NavigationView? = null
    private var navigationHeaderView: View? = null

    private val navigationItemSelectedListener = NavigationItemSelectedListener()

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        activity?.let {
            viewModel = ViewModelProviders.of(it).get(OverviewViewModel::class.java)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_overview, container, false)

        setupToolBar(rootView)
        setupDrawerLayout(rootView)

        return rootView
    }

    private fun setupToolBar(rootView: View) {
        toolBar = rootView.findViewById(R.id.toolbar)
        toolBar?.title = getString(R.string.app_name)
    }

    private fun setupDrawerLayout(rootView: View) {
        drawerLayout = rootView.findViewById<DrawerLayout>(R.id.drawer_layout).apply {
            val toggle = ActionBarDrawerToggle(
                activity,
                this,
                toolBar,
                R.string.drawer_open_description,
                R.string.drawer_close_description
            )
            addDrawerListener(toggle)
            toggle.syncState()
        }

        navigationView = rootView.findViewById<NavigationView>(R.id.navigationView).apply {
            setNavigationItemSelectedListener(navigationItemSelectedListener)
        }
        navigationHeaderView = navigationView?.inflateHeaderView(R.layout.main_drawer_header)

        viewModel.storedUser.observe(this, Observer {
            navigationHeaderView?.findViewById<TextView>(R.id.textView_drawer_header_subtitle)?.also { subtitleView ->
                subtitleView.text = viewModel.storedUser.value?.username
            }
        })
    }

    override fun onHandleBackPress(): Boolean {
        return if (drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
            drawerLayout?.closeDrawer(GravityCompat.START)
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
         * Closes drawer a little delayed to make show new fragment transaction more pretty.
         */
        private fun closeDrawerDelayed() {
            GlobalScope.launch(Dispatchers.Main) {
                delay(100)
                drawerLayout?.closeDrawer(GravityCompat.START)
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
