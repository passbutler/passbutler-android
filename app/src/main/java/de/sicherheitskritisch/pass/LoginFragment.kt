package de.sicherheitskritisch.pass

import android.arch.lifecycle.Observer
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.sicherheitskritisch.pass.common.showFadeAnimation
import de.sicherheitskritisch.pass.common.signal
import de.sicherheitskritisch.pass.databinding.FragmentLoginBinding
import de.sicherheitskritisch.pass.ui.AnimatedFragment
import de.sicherheitskritisch.pass.ui.BaseViewModelFragment

class LoginFragment : BaseViewModelFragment<LoginViewModel>(), AnimatedFragment {

    override val transitionType = AnimatedFragment.TransitionType.SLIDE_HORIZONTAL

    private val requestFinishedSuccessfullySignal = signal {
        showFragmentAsFirstScreen(OverviewFragment.newInstance())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel?.requestFinishedSuccessfully?.addSignal(requestFinishedSuccessfullySignal)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = DataBindingUtil.inflate<FragmentLoginBinding>(inflater, R.layout.fragment_login, container, false)
        binding.setLifecycleOwner(this)
        binding.viewModel = viewModel

        binding.buttonLogin.setOnClickListener {
            // TODO: Validate values
            val serverUrl = binding.editTextServerurl.text.toString()
            val username = binding.editTextUsername.text.toString()
            val password = binding.editTextPassword.text.toString()
            viewModel?.login(username, password)
        }

        viewModel?.isLoading?.observe(this, Observer<Boolean> {
            it?.let { shouldShowProgress ->
                binding.frameLayoutProgressContainer.showFadeAnimation(shouldShowProgress)
            }
        })

        return binding.root
    }

    override fun onDestroy() {
        viewModel?.requestFinishedSuccessfully?.removeSignal(requestFinishedSuccessfullySignal)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LoginFragment"

        fun newInstance(viewModel: LoginViewModel) = LoginFragment().also { it.viewModel = viewModel }
    }
}
