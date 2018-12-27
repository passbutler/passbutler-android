package de.sicherheitskritisch.pass

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.*
import de.sicherheitskritisch.pass.databinding.FragmentOverviewBinding
import de.sicherheitskritisch.pass.databinding.FragmentSettingsBinding
import de.sicherheitskritisch.pass.ui.ToolBarFragment

class SettingsFragment : ToolBarFragment() {

    override fun getToolBarTitle() = getString(R.string.app_name)

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = DataBindingUtil.inflate<FragmentSettingsBinding>(inflater, R.layout.fragment_settings, container, false)
        return binding.root
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}