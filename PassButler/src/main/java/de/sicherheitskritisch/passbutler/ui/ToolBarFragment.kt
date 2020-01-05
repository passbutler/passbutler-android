package de.sicherheitskritisch.passbutler.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import de.sicherheitskritisch.passbutler.R

abstract class ToolBarFragment<ViewModelType : ViewModel> : BaseViewModelFragment<ViewModelType>() {

    private var toolBar: Toolbar? = null
    private var floatingActionButton: FloatingActionButton? = null

    abstract fun getToolBarTitle(): String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_toolbar, container, false)

        toolBar = rootView.findViewById<Toolbar>(R.id.toolbar)?.apply {
            val toolBarIconDrawableId = when (transitionType) {
                TransitionType.MODAL -> R.drawable.icon_clear_24dp
                TransitionType.SLIDE,
                TransitionType.FADE -> R.drawable.icon_arrow_back_24dp
                TransitionType.NONE -> null
            }

            navigationIcon = toolBarIconDrawableId?.let {
                resources.getDrawable(it, null)?.apply {
                    setTint(resources.getColor(R.color.white, null))
                }
            }

            setNavigationOnClickListener {
                popBackstack()
            }

            setupToolbarMenu(this)
        }

        updateToolbarTitle()

        floatingActionButton = rootView.findViewById(R.id.main_fab)

        createContentView(inflater, container, savedInstanceState)?.let { contentView ->
            val contentContainer = rootView.findViewById<FrameLayout>(R.id.frameLayout_fragment_toolbar_content_container)
            contentContainer.addView(contentView)
        }

        return rootView
    }

    protected open fun setupToolbarMenu(toolbar: Toolbar) {
        // Implement if needed
    }

    protected fun updateToolbarTitle() {
        toolBar?.title = getToolBarTitle()
    }

    abstract fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
}