package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import de.sicherheitskritisch.passbutler.common.L
import de.sicherheitskritisch.passbutler.databinding.FragmentSettingsBinding
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.SimpleOnSeekBarChangeListener
import de.sicherheitskritisch.passbutler.ui.ToolBarFragment

class SettingsFragment : ToolBarFragment<SettingsViewModel>() {

    override val transitionType = AnimatedFragment.TransitionType.SLIDE_VERTICAL

    override fun getToolBarTitle() = getString(R.string.settings_title)

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        activity?.let {
            viewModel = ViewModelProviders.of(it).get(SettingsViewModel::class.java)

            val rootViewModel = ViewModelProviders.of(it).get(RootViewModel::class.java)
            viewModel.rootViewModel = rootViewModel
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = DataBindingUtil.inflate<FragmentSettingsBinding>(inflater, R.layout.fragment_settings, container, false)
        binding.userViewModel = viewModel.userViewModel

        binding.seekBarSettingLocktimeout.apply {
            max = 5
            progress = viewModel.userViewModel?.lockTimeout?.value ?: 0

            setOnSeekBarChangeListener(object : SimpleOnSeekBarChangeListener() {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    // Manually update value (in this callback, the value is not written to the viewmodel
                    binding.textViewSettingLocktimeoutValue.text = progress.toString()
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.progress?.let { newProgress ->
                        L.d("SettingsFragment", "onStopTrackingTouch(): progress = $newProgress")
                        viewModel.userViewModel?.lockTimeout?.value = newProgress
                    }
                }
            })
        }

        return binding.root
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}