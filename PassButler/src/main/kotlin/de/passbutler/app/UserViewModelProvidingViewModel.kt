package de.passbutler.app

import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.createViewModelLazy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import de.passbutler.app.base.AbstractPassButlerApplication
import de.passbutler.common.LoggedInUserResult
import de.passbutler.common.UserManager
import de.passbutler.common.base.BindableObserver
import kotlinx.coroutines.launch

class UserViewModelProvidingViewModel : ViewModel() {

    var loggedInUserViewModel: UserViewModel? = null
        private set

    val userManager
        get() = AbstractPassButlerApplication.userManager

    private val loggedInUserResultObserver = LoggedInUserResultObserver()

    init {
        registerLoggedInUserResultObserver()
    }

    override fun onCleared() {
        unregisterLoggedInUserResultObserver()
        super.onCleared()
    }

    private fun registerLoggedInUserResultObserver() {
        // Initially notify observer to be sure, the `loggedInUserViewModel` is restored immediately
        userManager.loggedInUserResult.addObserver(viewModelScope, true, loggedInUserResultObserver)
    }

    private fun unregisterLoggedInUserResultObserver() {
        userManager.loggedInUserResult.removeObserver(loggedInUserResultObserver)
    }

    private inner class LoggedInUserResultObserver : BindableObserver<LoggedInUserResult?> {
        override fun invoke(loggedInUserResult: LoggedInUserResult?) {
            when (loggedInUserResult) {
                is LoggedInUserResult.LoggedIn.PerformedLogin -> {
                    loggedInUserViewModel = UserViewModel(userManager, loggedInUserResult.loggedInUser, loggedInUserResult.masterPassword)
                }
                is LoggedInUserResult.LoggedIn.RestoredLogin -> {
                    loggedInUserViewModel = UserViewModel(userManager, loggedInUserResult.loggedInUser, null)
                }
                is LoggedInUserResult.LoggedOut -> {
                    // Finally clear crypto resources and reset related jobs
                    viewModelScope.launch {
                        loggedInUserViewModel?.clearSensibleData()
                        loggedInUserViewModel?.cancelJobs()
                        loggedInUserViewModel = null
                    }
                }
            }
        }
    }
}

object UserViewModelProvidingViewModelUninitializedException : IllegalStateException("The UserViewModelProvidingViewModel is null!")
object LoggedInUserViewModelUninitializedException : IllegalStateException("The logged-in UserViewModel is null!")

abstract class UserViewModelUsingViewModel : ViewModel() {
    val loggedInUserViewModel: UserViewModel?
        get() {
            val userViewModelProvidingViewModel = userViewModelProvidingViewModel ?: throw UserViewModelProvidingViewModelUninitializedException
            return userViewModelProvidingViewModel.loggedInUserViewModel
        }

    val userManager: UserManager
        get() {
            val userViewModelProvidingViewModel = userViewModelProvidingViewModel ?: throw UserViewModelProvidingViewModelUninitializedException
            return userViewModelProvidingViewModel.userManager
        }

    // TODO: lateinit
    private var userViewModelProvidingViewModel: UserViewModelProvidingViewModel? = null

    fun setUserViewModelProvidingViewModel(newUserViewModelProvidingViewModel: UserViewModelProvidingViewModel) {
        userViewModelProvidingViewModel = newUserViewModelProvidingViewModel
    }
}

@MainThread
inline fun <reified VM : UserViewModelUsingViewModel> Fragment.userViewModelUsingViewModels(
    noinline userViewModelProvidingViewModel: () -> UserViewModelProvidingViewModel,
    noinline ownerProducer: () -> ViewModelStoreOwner = { this }
): Lazy<VM> {
    return createViewModelLazy(VM::class, { ownerProducer().viewModelStore }) {
        UserViewModelUsingViewModelFactory(userViewModelProvidingViewModel.invoke())
    }
}

@MainThread
inline fun <reified VM : UserViewModelUsingViewModel> Fragment.userViewModelUsingActivityViewModels(
    noinline userViewModelProvidingViewModel: () -> UserViewModelProvidingViewModel
): Lazy<VM> {
    return createViewModelLazy(VM::class, { requireActivity().viewModelStore }) {
        UserViewModelUsingViewModelFactory(userViewModelProvidingViewModel.invoke())
    }
}

class UserViewModelUsingViewModelFactory(private val userViewModelProvidingViewModel: UserViewModelProvidingViewModel) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return super.create(modelClass).also {
            (it as UserViewModelUsingViewModel).setUserViewModelProvidingViewModel(userViewModelProvidingViewModel)
        }
    }
}