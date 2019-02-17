package de.sicherheitskritisch.passbutler

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.EmptyViewModel
import de.sicherheitskritisch.passbutler.ui.ToolBarFragment
import java.text.SimpleDateFormat
import java.util.*

class AboutFragment : ToolBarFragment<EmptyViewModel>() {

    override val transitionType = AnimatedFragment.TransitionType.SLIDE_VERTICAL

    private val versionName
        get() = BuildConfig.VERSION_NAME

    private val formattedBuildTime: String
        get() {
            val locale = Locale.getDefault()
            val dateTimeFormatPattern = DateFormat.getBestDateTimePattern(locale, "MM/dd/yyyy HH:mm:ss")
            val formatter = SimpleDateFormat(dateTimeFormatPattern, locale)
            return formatter.format(BuildConfig.BUILD_TIME)
        }

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