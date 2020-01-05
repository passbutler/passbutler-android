package de.sicherheitskritisch.passbutler

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.sicherheitskritisch.passbutler.base.formattedDateTime
import de.sicherheitskritisch.passbutler.base.viewmodels.EmptyViewModel
import de.sicherheitskritisch.passbutler.ui.ToolBarFragment

class AboutFragment : ToolBarFragment<EmptyViewModel>() {

    private val versionName
        get() = BuildConfig.VERSION_NAME

    private val formattedBuildTime: String
        get() = BuildConfig.BUILD_TIME.formattedDateTime

    override fun getToolBarTitle() = getString(R.string.about_title)

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = inflater.inflate(R.layout.fragment_about, container, false)

        rootView.findViewById<TextView>(R.id.textView_subheader).also { subHeader ->
            subHeader.text = getString(R.string.about_subheader, versionName, formattedBuildTime)
        }

        return rootView
    }

    companion object {
        fun newInstance() = AboutFragment()
    }
}