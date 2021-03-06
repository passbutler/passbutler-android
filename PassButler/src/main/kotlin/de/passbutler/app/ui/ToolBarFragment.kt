package de.passbutler.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import de.passbutler.app.R
import de.passbutler.common.ui.TransitionType

abstract class ToolBarFragment : BaseFragment() {

    protected var toolBar: Toolbar? = null
        private set

    abstract fun getToolBarTitle(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore fragment transition type after configuration change
        savedInstanceState?.getInt(BUNDLE_TRANSITION_TYPE)?.let { transitionTypeOrdinal ->
            TransitionType.values().getOrNull(transitionTypeOrdinal)?.let {
                transitionType = it
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_toolbar, container, false)

        toolBar = rootView.findViewById<Toolbar>(R.id.toolbar)?.also { toolBar ->
            val toolBarIconDrawableId = when (transitionType) {
                TransitionType.MODAL,
                TransitionType.FADE -> R.drawable.icon_clear_24dp
                TransitionType.SLIDE -> R.drawable.icon_arrow_back_24dp
                TransitionType.NONE -> null
            }

            toolBar.navigationIcon = toolBarIconDrawableId?.let {
                ResourcesCompat.getDrawable(resources, it, toolBar.context.theme)
            }

            toolBar.setNavigationOnClickListener {
                onBackIconClicked()
            }

            setupToolbarMenu(toolBar)
        }

        updateToolbarTitle()

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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(BUNDLE_TRANSITION_TYPE, transitionType.ordinal)

        super.onSaveInstanceState(outState)
    }

    protected open fun onBackIconClicked() {
        popBackstack()
    }

    companion object {
        private const val BUNDLE_TRANSITION_TYPE = "TRANSITION_TYPE"
    }
}
