package de.passbutler.app

import android.app.Application
import androidx.lifecycle.Observer
import de.passbutler.app.base.NonNullMutableLiveData
import de.passbutler.app.base.viewmodels.CoroutineScopeAndroidViewModel
import de.passbutler.common.base.Result
import kotlinx.coroutines.delay

class OverviewViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    var loggedInUserViewModel: UserViewModel? = null
        set(value) {
            field = value

            unregisterItemViewModelsObserver()
            registerItemViewModelsObserver()
        }

    val itemViewModels = NonNullMutableLiveData<List<ItemViewModel>>(emptyList())

    private val itemViewModelsChangedObserver = Observer<List<ItemViewModel>> { newUnfilteredItemViewModels ->
        // Only show non-deleted item viewmodels in overview list
        itemViewModels.value = newUnfilteredItemViewModels.filter { !it.deleted }
    }

    override fun onCleared() {
        unregisterItemViewModelsObserver()
        super.onCleared()
    }

    suspend fun synchronizeData(): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        return loggedInUserViewModel.synchronizeData()
    }

    suspend fun logoutUser(): Result<Unit> {
        // Some artificial delay to look flow more natural
        delay(500)

        val loggedInUserViewModel = loggedInUserViewModel ?: throw LoggedInUserViewModelUninitializedException
        return loggedInUserViewModel.logout()
    }

    private fun registerItemViewModelsObserver() {
        loggedInUserViewModel?.itemViewModels?.observeForever(itemViewModelsChangedObserver)
    }

    private fun unregisterItemViewModelsObserver() {
        loggedInUserViewModel?.itemViewModels?.removeObserver(itemViewModelsChangedObserver)
    }
}
