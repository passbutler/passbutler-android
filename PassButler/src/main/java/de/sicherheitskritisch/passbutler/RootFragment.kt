package de.sicherheitskritisch.passbutler

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.arch.persistence.room.Room
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.sicherheitskritisch.passbutler.common.L
import de.sicherheitskritisch.passbutler.common.PassDatabase
import de.sicherheitskritisch.passbutler.common.User
import de.sicherheitskritisch.passbutler.common.observe
import de.sicherheitskritisch.passbutler.ui.BaseViewModelFragment
import de.sicherheitskritisch.passbutler.ui.FragmentPresentingDelegate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.time.Instant
import java.util.*

class RootFragment : BaseViewModelFragment<RootViewModel>() {

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        activity?.let {
            viewModel = ViewModelProviders.of(it).get(RootViewModel::class.java)

            val contentContainerResourceId = R.id.frameLayout_fragment_root_content_container
            fragmentPresentingDelegate = FragmentPresentingDelegate(
                WeakReference(it),
                WeakReference(this),
                contentContainerResourceId
            )
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_root, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        L.d("RootFragment", "onViewCreated(): savedInstanceState = $savedInstanceState")

        // Do not notify on register on configuration changes because shown fragment will be restored automatically
        val notifyOnRegister = (savedInstanceState == null)

        viewModel.rootScreenState.observe(this, notifyOnRegister, Observer {
            updateRootScreen()
        })


        val database = Room.databaseBuilder(activity?.applicationContext!!, PassDatabase::class.java, "passDatabase")
            .allowMainThreadQueries ()
            .fallbackToDestructiveMigration()
            .build()

        GlobalScope.launch {

            val now = Date.from(Instant.now())
            database.userDao().insert(User("testuser1", now, now))

            val testuser1 = database.userDao().findByUsername("testuser1")
            val testuser2 = database.userDao().findByUsername("testuser2")

            L.d("RootFragment", "onViewCreated(): testuser1 = ${testuser1}, testuser2 = ${testuser2}")

        }


    }

    private fun updateRootScreen() {
        val rootScreenState = viewModel.rootScreenState.value
        L.d("RootFragment", "updateRootScreen(): was called rootScreenState = $rootScreenState")

        when (rootScreenState) {
            is RootViewModel.RootScreenState.LoggedIn -> {
                // TODO: show lock screen if locked
                // val isUnlocked = rootScreenState.isUnlocked

                if (!isFragmentShown(OverviewFragment::class.java)) {
                    val overviewFragment = OverviewFragment.newInstance()
                    showFragmentAsFirstScreen(overviewFragment)
                }
            }
            is RootViewModel.RootScreenState.LoggedOut -> {
                if (!isFragmentShown(LoginFragment::class.java)) {
                    val loginFragment = LoginFragment.newInstance()
                    showFragmentAsFirstScreen(loginFragment)
                }
            }
        }
    }

    companion object {
        fun newInstance() = RootFragment()
    }
}