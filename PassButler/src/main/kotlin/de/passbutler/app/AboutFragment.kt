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

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentAboutBinding.inflate(inflater, container, false).apply {
            setupVersionInformation()
            setupImprintButton()
            setupPrivacyPolicyButton()
        }

        return binding.root
    }

    private fun FragmentAboutBinding.setupVersionInformation() {
        textViewVersionInformation.apply {
            val versionName = BuildConfig.VERSION_NAME
            val formattedBuildTime = Instant.ofEpochMilli(BuildConfig.BUILD_TIMESTAMP).formattedDateTime()
            val gitShortHash = BuildConfig.BUILD_REVISION_HASH
            val formattedText = getString(R.string.about_subheader, versionName, formattedBuildTime, gitShortHash)

            setTextWithClickablePart(formattedText, gitShortHash) {
                openBrowser(GIT_PROJECT_URL.format(gitShortHash))
            }
        }
    }

    private fun FragmentAboutBinding.setupImprintButton() {
        buttonOpenImprint.setOnClickListener {
            openBrowser(getString(R.string.about_imprint_url))
        }
    }

    private fun FragmentAboutBinding.setupPrivacyPolicyButton() {
        buttonOpenPrivacyPolicy.setOnClickListener {
            openBrowser(getString(R.string.about_privacy_policy_url))
        }
    }

    companion object {
        private const val GIT_PROJECT_URL = "https://github.com/passbutler/passbutler-android/commit/%s"

        fun newInstance() = AboutFragment()
    }
}
