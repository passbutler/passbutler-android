package de.sicherheitskritisch.pass

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.sicherheitskritisch.pass.databinding.FragmentAboutBinding
import de.sicherheitskritisch.pass.ui.ToolBarFragment
import java.text.SimpleDateFormat
import java.util.*

class AboutFragment : ToolBarFragment() {

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
        val binding = DataBindingUtil.inflate<FragmentAboutBinding>(inflater, R.layout.fragment_about, container, false)

        binding.textViewSubheader.text = getString(R.string.about_subheader, versionName, formattedBuildTime)

        return binding.root
    }

    companion object {
        fun newInstance() = AboutFragment()
    }
}