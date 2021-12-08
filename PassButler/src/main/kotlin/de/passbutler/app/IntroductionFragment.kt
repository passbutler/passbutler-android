package de.passbutler.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.passbutler.app.databinding.FragmentIntroductionBinding
import de.passbutler.app.ui.BaseFragment
import de.passbutler.app.ui.showFragmentModally
import de.passbutler.common.ui.TransitionType

class IntroductionFragment : BaseFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentIntroductionBinding.inflate(inflater, container, false).apply {
            buttonCreateUser.setOnClickListener {
                showFragment(CreateLocalUserWizardFragment.newInstance(), transitionType = TransitionType.FADE)
            }

            buttonLogin.setOnClickListener {
                showFragment(LoginFragment.newInstance(), transitionType = TransitionType.FADE)
            }

            buttonImprint.setOnClickListener {
                showFragmentModally(AboutFragment.newInstance())
            }
        }

        return binding.root
    }

    companion object {
        fun newInstance() = IntroductionFragment()
    }
}
