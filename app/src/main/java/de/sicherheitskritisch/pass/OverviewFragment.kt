package de.sicherheitskritisch.pass

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
import de.sicherheitskritisch.pass.ui.AnimatedFragment
import de.sicherheitskritisch.pass.ui.BaseFragment

class OverviewFragment : BaseFragment(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.SLIDE_HORIZONTAL

    private var toolBar: Toolbar? = null
    private var drawerLayout: DrawerLayout? = null
    private var navigationView: NavigationView? = null
    private val navigationItemSelectedListener = NavigationItemSelectedListener()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_overview, container, false)

        toolBar = rootView.findViewById(R.id.toolbar)
        toolBar?.title = getString(R.string.app_name)

        setupDrawerLayout(rootView)

        return rootView
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
    }

    override fun onHandleBackPress(): Boolean {
        return if (drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
            drawerLayout?.closeDrawer(GravityCompat.START)
            true
        } else {
            false
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
                    // TODO: Remove replace flag
                    showFragment(AboutFragment.newInstance(), replaceFragment = true)
                    true
                }
                R.id.drawer_menu_item_logout -> {
                    val loginViewModel = LoginViewModel()
                    val loginFragment = LoginFragment.newInstance(loginViewModel)

                    // Replace fragment and do not add to backstack (the login screen will be the first screen)
                    showFragment(loginFragment, replaceFragment = true, addToBackstack = false)
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
                drawerLayout?.closeDrawer(GravityCompat.START)
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
        private const val URL_GOOGLE_PLAY = "market://details?id=de.sicherheitskritisch.pass"

        fun newInstance() = OverviewFragment()
    }
}
