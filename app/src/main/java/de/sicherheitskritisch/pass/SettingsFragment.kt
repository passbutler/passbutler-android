package de.sicherheitskritisch.pass

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.sicherheitskritisch.pass.databinding.FragmentSettingsBinding
import de.sicherheitskritisch.pass.ui.AnimatedFragment
import de.sicherheitskritisch.pass.ui.ToolBarFragment

class SettingsFragment : ToolBarFragment() {

    override val transitionType = AnimatedFragment.TransitionType.SLIDE_VERTICAL

    override fun getToolBarTitle() = getString(R.string.settings_title)

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = DataBindingUtil.inflate<FragmentSettingsBinding>(inflater, R.layout.fragment_settings, container, false)
        return binding.root
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}