package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import de.sicherheitskritisch.passbutler.databinding.FragmentSettingsBinding
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.SimpleOnSeekBarChangeListener
import de.sicherheitskritisch.passbutler.ui.ToolBarFragment

class SettingsFragment : ToolBarFragment<SettingsViewModel>() {

    override val transitionType = AnimatedFragment.TransitionType.MODAL

    override fun getToolBarTitle() = getString(R.string.settings_title)

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        viewModel = ViewModelProviders.of(this).get(SettingsViewModel::class.java)

        activity?.let {
            val rootViewModel = getRootViewModel(it)
            viewModel.loggedInUserViewModel = rootViewModel.loggedInUserViewModel
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = DataBindingUtil.inflate<FragmentSettingsBinding>(inflater, R.layout.fragment_settings, container, false)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        setupLockTimeoutSeekBar(binding)

        return binding.root
    }

    private fun setupLockTimeoutSeekBar(binding: FragmentSettingsBinding) {
        binding.seekBarSettingLocktimeout.apply {
            max = 5
            progress = viewModel.lockTimeout?.value ?: 0

            setOnSeekBarChangeListener(object : SimpleOnSeekBarChangeListener() {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    // Manually update value (in this callback, the value is not written to the viewmodel)
                    binding.textViewSettingLocktimeoutValue.text = progress.toString()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.progress?.let { newProgress ->
                        viewModel.lockTimeout?.value = newProgress
                    }
                }
            })
        }
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}
