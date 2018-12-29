package de.sicherheitskritisch.pass

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.sicherheitskritisch.pass.databinding.FragmentLoginBinding
import de.sicherheitskritisch.pass.ui.AnimatedFragment
import de.sicherheitskritisch.pass.ui.BaseViewModelFragment

class LoginFragment : BaseViewModelFragment<LoginViewModel>(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.SLIDE_HORIZONTAL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = DataBindingUtil.inflate<FragmentLoginBinding>(inflater, R.layout.fragment_login, container, false)
        binding.setLifecycleOwner(this)
        binding.viewModel = viewModel

        binding.buttonLogin.setOnClickListener {
            // TODO: Validate values
            val username = binding.editTextUsername.text.toString()
            val password = binding.editTextPassword.text.toString()
            viewModel?.login(username, password)

            // Replace fragment and do not add to backstack (it is first screen)
            // showFragment(OverviewFragment.newInstance(), replaceFragment = true, addToBackstack = false)
        }

        return binding.root
    }

    companion object {
        private const val TAG = "LoginFragment"

        fun newInstance(viewModel: LoginViewModel) = LoginFragment().also { it.viewModel = viewModel }
    }
}
