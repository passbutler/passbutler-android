package de.sicherheitskritisch.pass.ui

import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.*
import android.widget.FrameLayout
import de.sicherheitskritisch.pass.R
import de.sicherheitskritisch.pass.SettingsFragment

abstract class ToolBarFragment : BaseFragment() {

    private var toolBar: Toolbar? = null
    private var floatingActionButton: FloatingActionButton? = null

    abstract fun getToolBarTitle(): String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_toolbar, container, false)

        toolBar = rootView.findViewById(R.id.toolbar)
        toolBar?.title = getToolBarTitle()
        toolBar?.navigationIcon = resources.getDrawable(R.drawable.ic_menu_camera, null) // TODO: back arrow

        floatingActionButton = rootView.findViewById(R.id.main_fab)

        createContentView(inflater, container, savedInstanceState).let { contentView ->
            val contentContainer = rootView.findViewById<FrameLayout>(R.id.frameLayout_fragment_toolbar_content_container)
            contentContainer.addView(contentView)
        }

        return rootView
    }

    abstract fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
}