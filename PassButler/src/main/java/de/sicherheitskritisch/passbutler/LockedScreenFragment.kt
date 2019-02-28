package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.sicherheitskritisch.passbutler.databinding.FragmentLockedScreenBinding
import de.sicherheitskritisch.passbutler.ui.AnimatedFragment
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment

class LockedScreenFragment : BaseViewModelFragment<RootViewModel>(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.FADE

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        activity?.let {
            viewModel = ViewModelProviders.of(it).get(RootViewModel::class.java)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = DataBindingUtil.inflate<FragmentLockedScreenBinding>(inflater, R.layout.fragment_locked_screen, container, false).also { binding ->
            binding.lifecycleOwner = this
            binding.viewModel = viewModel

            binding.imageViewFingerprintIcon.setOnClickListener {
                viewModel.unlockScreen()
            }
        }

        return binding?.root
    }

    override fun onHandleBackPress(): Boolean {
        // TODO: Allow the user to exit the app via backpress nevertheless
        // Do not allow pop fragment via backpress
        return true
    }

    companion object {
        fun newInstance() = LockedScreenFragment()
    }
}