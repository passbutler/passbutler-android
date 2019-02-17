package de.sicherheitskritisch.passbutler.ui

import android.arch.lifecycle.ViewModel
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import de.sicherheitskritisch.passbutler.R

abstract class ToolBarFragment<ViewModelType: ViewModel> : BaseViewModelFragment<ViewModelType>(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.SLIDE_HORIZONTAL

    private var toolBar: Toolbar? = null
    private var floatingActionButton: FloatingActionButton? = null

    abstract fun getToolBarTitle(): String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_toolbar, container, false)

        toolBar = rootView.findViewById<Toolbar>(R.id.toolbar)?.apply {
            title = getToolBarTitle()
            navigationIcon = resources.getDrawable(R.drawable.icon_arrow_back_24dp, null)?.apply {
                setTint(resources.getColor(R.color.white, null))
            }

            setNavigationOnClickListener {
                popBackstack()
            }
        }

        floatingActionButton = rootView.findViewById(R.id.main_fab)

        createContentView(inflater, container, savedInstanceState).let { contentView ->
            val contentContainer = rootView.findViewById<FrameLayout>(R.id.frameLayout_fragment_toolbar_content_container)
            contentContainer.addView(contentView)
        }

        return rootView
    }

    abstract fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
}