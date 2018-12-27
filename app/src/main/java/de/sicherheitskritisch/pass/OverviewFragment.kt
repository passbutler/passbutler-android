package de.sicherheitskritisch.pass

import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.Toolbar
import android.view.*
import de.sicherheitskritisch.pass.ui.BaseFragment
import de.sicherheitskritisch.pass.ui.ToolBarFragment

class OverviewFragment : BaseFragment() {

    private var toolBar: Toolbar? = null

    private var drawerLayout: DrawerLayout? = null
    private val navigationItemSelectedListener = NavigationItemSelectedListener()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_overview, container, false)

        toolBar = rootView.findViewById(R.id.toolbar)
        toolBar?.title = getString(R.string.app_name)

        setupDrawerLayout(rootView)
//        setHasOptionsMenu(true)

        return rootView
    }

    private fun setupDrawerLayout(rootView: View) {
        drawerLayout = rootView.findViewById<DrawerLayout>(R.id.drawer_layout).apply {
            val toggle = ActionBarDrawerToggle(
                activity,
                this,
                toolBar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
            )
            addDrawerListener(toggle)
            toggle.syncState()
        }

        rootView.findViewById<NavigationView>(R.id.navigationView).apply {
            setNavigationItemSelectedListener(navigationItemSelectedListener)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater?.inflate(R.menu.main_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
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
            when (item.itemId) {
                R.id.nav_camera -> {
                    showFragment(SettingsFragment.newInstance())
                }
            }

            drawerLayout?.closeDrawer(GravityCompat.START)
            return true
        }
    }

    companion object {
        fun newInstance() = OverviewFragment()
    }
}