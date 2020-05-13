package de.passbutler.app

import androidx.lifecycle.Observer
import de.passbutler.app.base.NonNullMutableLiveData
import de.passbutler.app.base.viewmodels.CoroutineScopedViewModel
import de.passbutler.common.base.Result
import kotlinx.coroutines.delay

class OverviewViewModel : CoroutineScopedViewModel() {

    var loggedInUserViewModel: UserViewModel? = null
        set(value) {
            unregisterItemViewModelsObserver(field)
            field = value

            registerItemViewModelsObserver(field)
        }

    val itemViewModels = NonNullMutableLiveData<List<ItemViewModel>>(emptyList())

    private val itemViewModelsChangedObserver = Observer<List<ItemViewModel>> { newUnfilteredItemViewModels ->
        // Only show non-deleted item viewmodels in overview list
        itemViewModels.value = newUnfilteredItemViewModels.filter { !it.deleted }
    }

    override fun onCleared() {
        unregisterItemViewModelsObserver(loggedInUserViewModel)
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

    private fun registerItemViewModelsObserver(loggedInUserViewModel: UserViewModel?) {
        loggedInUserViewModel?.itemViewModels?.observeForever(itemViewModelsChangedObserver)
    }

    private fun unregisterItemViewModelsObserver(loggedInUserViewModel: UserViewModel?) {
        loggedInUserViewModel?.itemViewModels?.removeObserver(itemViewModelsChangedObserver)
    }
}
