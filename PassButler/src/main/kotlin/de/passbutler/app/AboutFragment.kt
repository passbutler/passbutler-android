package de.passbutler.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.passbutler.app.databinding.FragmentAboutBinding
import de.passbutler.app.ui.ToolBarFragment
import de.passbutler.app.ui.openBrowser
import de.passbutler.app.ui.setTextWithClickablePart
import de.passbutler.common.base.formattedDateTime
import java.time.Instant

class AboutFragment : ToolBarFragment() {

    override fun getToolBarTitle() = getString(R.string.about_title)

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentAboutBinding.inflate(inflater, container, false)

        binding.textViewSubheader.also { subHeader ->
            val versionName = BuildConfig.VERSION_NAME
            val formattedBuildTime = Instant.ofEpochMilli(BuildConfig.BUILD_TIMESTAMP).formattedDateTime()
            val gitShortHash = BuildConfig.BUILD_REVISION_HASH
            val formattedText = getString(R.string.about_subheader, versionName, formattedBuildTime, gitShortHash)

            subHeader.setTextWithClickablePart(formattedText, gitShortHash) {
                openBrowser(GIT_PROJECT_URL.format(gitShortHash))
            }
        }

        return binding.root
    }

    companion object {
        private const val GIT_PROJECT_URL = "https://git.sicherheitskritisch.de/passbutler/passbutler-android/commit/%s"

        fun newInstance() = AboutFragment()
    }
}
