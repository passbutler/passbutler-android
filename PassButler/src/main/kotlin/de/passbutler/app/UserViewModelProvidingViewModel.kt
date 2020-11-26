package de.passbutler.app

import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.createViewModelLazy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import de.passbutler.app.base.AbstractPassButlerApplication
import de.passbutler.app.crypto.BiometricsProvider
import de.passbutler.common.UserManager
import de.passbutler.common.UserViewModel
import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import kotlinx.coroutines.runBlocking
import org.tinylog.kotlin.Logger

class UserViewModelProvidingViewModel : ViewModel() {

    var loggedInUserViewModel: UserViewModel? = null
        private set

    private val userManager
        get() = AbstractPassButlerApplication.userManager

    private val biometricsProvider = BiometricsProvider()

    init {
        // Need to restored in-place to provide `loggedInUserViewModel` instance immediately
        runBlocking {
            restoreLoggedInUser()
        }
    }

    private suspend fun restoreLoggedInUser(): Result<Unit> {
        val restoreResult = userManager.restoreLoggedInUser()

        return when (restoreResult) {
            is Success -> {
                val loggedInUserResult = restoreResult.result
                loggedInUserViewModel = UserViewModel(userManager, biometricsProvider, loggedInUserResult.loggedInUser)

                Success(Unit)
            }
            is Failure -> Failure(restoreResult.throwable)
        }
    }

    suspend fun loginUser(serverUrlString: String?, username: String, masterPassword: String): Result<Unit> {
        val loginResult = when (serverUrlString) {
            null -> userManager.loginLocalUser(username, masterPassword)
            else -> userManager.loginRemoteUser(username, masterPassword, serverUrlString)
        }

        return when (loginResult) {
            is Success -> {
                val loggedInUserResult = loginResult.result
                val newLoggedInUserViewModel = UserViewModel(userManager, biometricsProvider, loggedInUserResult.loggedInUser)

                val decryptSensibleDataResult = newLoggedInUserViewModel.decryptSensibleData(masterPassword)

                when (decryptSensibleDataResult) {
                    is Success -> {
                        loggedInUserViewModel = newLoggedInUserViewModel
                        Success(Unit)
                    }
                    is Failure -> {
                        Logger.warn(decryptSensibleDataResult.throwable, "The initial unlock of the resources after login failed!")
                        Failure(decryptSensibleDataResult.throwable)
                    }
                }
            }
            is Failure -> Failure(loginResult.throwable)
        }
    }

    suspend fun logoutUser(): Result<Unit> {
        val logoutResult = userManager.logoutUser(UserManager.LogoutBehaviour.ClearDatabase)

        return when (logoutResult) {
            is Success -> {
                loggedInUserViewModel?.clearSensibleData()
                loggedInUserViewModel?.cancelJobs()
                loggedInUserViewModel = null

                Success(Unit)
            }
            is Failure -> Failure(logoutResult.throwable)
        }
    }
}

abstract class UserViewModelUsingViewModel : ViewModel() {
    lateinit var userViewModelProvidingViewModel: UserViewModelProvidingViewModel

    val loggedInUserViewModel: UserViewModel?
        get() = userViewModelProvidingViewModel.loggedInUserViewModel
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
            (it as UserViewModelUsingViewModel).userViewModelProvidingViewModel = userViewModelProvidingViewModel
        }
    }
}