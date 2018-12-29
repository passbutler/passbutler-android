package de.sicherheitskritisch.pass

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.sicherheitskritisch.pass.databinding.FragmentLoginBinding
import de.sicherheitskritisch.pass.ui.AnimatedFragment
import de.sicherheitskritisch.pass.ui.BaseFragment

class LoginFragment : BaseFragment(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.SLIDE_HORIZONTAL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = DataBindingUtil.inflate<FragmentLoginBinding>(inflater, R.layout.fragment_login, container, false)

        binding.buttonLogin.setOnClickListener {
            // Replace fragment and do not add to backstack (it is first screen)
            showFragment(OverviewFragment.newInstance(), replaceFragment = true, addToBackstack = false)
        }

        return binding.root
    }

    companion object {
        fun newInstance() = LoginFragment()
    }
}