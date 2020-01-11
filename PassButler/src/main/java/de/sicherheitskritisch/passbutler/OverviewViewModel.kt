package de.sicherheitskritisch.passbutler

import android.app.Application
import androidx.lifecycle.Observer
import de.sicherheitskritisch.passbutler.base.DefaultValueGetterLiveData
import de.sicherheitskritisch.passbutler.base.NonNullMutableLiveData
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.viewmodels.CoroutineScopeAndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class OverviewViewModel(application: Application) : CoroutineScopeAndroidViewModel(application) {

    var loggedInUserViewModel: UserViewModel? = null
        set(value) {
            field = value

            unregisterItemViewModelsObserver()
            registerItemViewModelsObserver()
        }

    val itemViewModels = NonNullMutableLiveData<List<ItemViewModel>>(emptyList())

    val lastSynchronizationDate = DefaultValueGetterLiveData {
        loggedInUserViewModel?.userType?.asRemote()?.lastSuccessfulSync
    }

    private val itemViewModelsChangedObserver = Observer<List<ItemViewModel>> { newUnfilteredItemViewModels ->
        // Only show non-deleted item viewmodels in overview list
        itemViewModels.value = newUnfilteredItemViewModels.filter { !it.deleted }
    }

    override fun onCleared() {
        unregisterItemViewModelsObserver()
        super.onCleared()
    }

    suspend fun synchronizeData(): Result<Unit> {
        val loggedInUserViewModel = loggedInUserViewModel ?: throw IllegalStateException("The logged-in user viewmodel is null!")
        val synchronizeDataResult = loggedInUserViewModel.synchronizeData()

        // Trigger notification for every result to be sure, the view is updating its relative formatted date
        withContext(Dispatchers.Main) {
            lastSynchronizationDate.notifyChange()
        }

        return synchronizeDataResult
    }

    suspend fun logoutUser(): Result<Unit> {
        // Some artificial delay to look flow more natural
        delay(500)

        val loggedInUserViewModel = loggedInUserViewModel ?: throw IllegalStateException("The logged-in user viewmodel is null!")
        return loggedInUserViewModel.logout()
    }

    private fun registerItemViewModelsObserver() {
        loggedInUserViewModel?.itemViewModels?.observeForever(itemViewModelsChangedObserver)
    }

    private fun unregisterItemViewModelsObserver() {
        loggedInUserViewModel?.itemViewModels?.removeObserver(itemViewModelsChangedObserver)
    }
}
