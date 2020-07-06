package de.passbutler.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.passbutler.app.base.formattedDateTime
import de.passbutler.app.databinding.FragmentAboutBinding
import de.passbutler.app.ui.ToolBarFragment

class AboutFragment : ToolBarFragment() {

    override fun getToolBarTitle() = getString(R.string.about_title)

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentAboutBinding.inflate(inflater)

        binding.textViewSubheader.also { subHeader ->
            val versionName = BuildConfig.VERSION_NAME
            val formattedBuildTime = BuildConfig.BUILD_TIME.formattedDateTime
            val gitShortHash = BuildConfig.BUILD_REVISION_HASH

            subHeader.text = getString(R.string.about_subheader, versionName, formattedBuildTime, gitShortHash)
        }

        return binding.root
    }

    companion object {
        fun newInstance() = AboutFragment()
    }
}