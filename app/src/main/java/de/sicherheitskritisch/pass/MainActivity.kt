package de.sicherheitskritisch.pass

import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.NavigationView
import android.support.design.widget.Snackbar
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem

class MainActivity : AppCompatActivity(){

    private val navigationItemSelectedListener = NavigationItemSelectedListener()

    private var toolBar: Toolbar? = null
    private var fabButton: FloatingActionButton? = null
    private var drawerLayout: DrawerLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupToolbar()
        setupFloatingActionMenu()
        setupDrawerLayout()
    }

    private fun setupToolbar() {
        toolBar = findViewById(R.id.toolbar)
        setSupportActionBar(toolBar)
    }

    private fun setupFloatingActionMenu() {
        fabButton = findViewById<FloatingActionButton>(R.id.main_fab).apply {
            setOnClickListener { view ->
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG).setAction("Action", null).show()
            }
        }
    }

    private fun setupDrawerLayout() {
        drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout).apply {
            val toggle = ActionBarDrawerToggle(
                this@MainActivity,
                this,
                toolBar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
            )
            addDrawerListener(toggle)
            toggle.syncState()
        }

        val navigationView = findViewById<NavigationView>(R.id.nav_view).apply {
            setNavigationItemSelectedListener(navigationItemSelectedListener)
        }
    }

    override fun onBackPressed() {
        drawerLayout?.takeIf { it.isDrawerOpen(GravityCompat.START) }?.closeDrawer(GravityCompat.START) ?: run {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    private inner class NavigationItemSelectedListener : NavigationView.OnNavigationItemSelectedListener {
        override fun onNavigationItemSelected(item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.nav_camera -> {

                }
                R.id.nav_gallery -> {

                }
                R.id.nav_slideshow -> {

                }
                R.id.nav_manage -> {

                }
                R.id.nav_share -> {

                }
                R.id.nav_send -> {

                }
            }

            drawerLayout?.closeDrawer(GravityCompat.START)
            return true
        }
    }
}
