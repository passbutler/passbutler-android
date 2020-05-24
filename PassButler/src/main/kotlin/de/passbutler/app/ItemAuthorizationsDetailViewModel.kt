package de.passbutler.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import de.passbutler.app.base.NonNullMutableLiveData
import de.passbutler.app.base.viewmodels.EditingViewModel
import de.passbutler.app.ui.ListItemIdentifiable
import de.passbutler.common.database.LocalRepository
import de.passbutler.common.database.models.ItemAuthorization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class ItemAuthorizationsDetailViewModel(
    private val itemId: String,
    private val loggedInUserViewModel: UserViewModel,
    private val localRepository: LocalRepository
) : ViewModel(), EditingViewModel {

    val itemAuthorizations: LiveData<List<ItemAuthorizationViewModel>>
        get() = _itemAuthorizations

    private val _itemAuthorizations = NonNullMutableLiveData<List<ItemAuthorizationViewModel>>(emptyList())

    suspend fun initializeItemAuthorizations() {
        localRepository.findItem(itemId)?.let { item ->

            val existingItemAuthorizations = localRepository.findItemAuthorizationForItem(item)
                .filter { it.userId != loggedInUserViewModel.id }
                .map {
                    ItemAuthorizationViewModel(ItemAuthorizationViewModel.Model.Existing(it))
                }

            val possibleItemAuthorizations = localRepository.findAllUsers()
                .filter { it.username != loggedInUserViewModel.id }
                .map {
                    ItemAuthorizationViewModel(ItemAuthorizationViewModel.Model.New(it.username))
                }

            val newItemAuthorizations = existingItemAuthorizations + possibleItemAuthorizations

            withContext(Dispatchers.Main) {
                _itemAuthorizations.value = newItemAuthorizations
            }
        }
    }
}

class ItemAuthorizationViewModel(private val model: Model) : ListItemIdentifiable {

    override val listItemId: String
        get() = when (model) {
            is Model.New -> UUID.randomUUID().toString()
            is Model.Existing -> model.itemAuthorization.id
        }

    val username: String
        get() = when (model) {
            is Model.New -> model.username
            is Model.Existing -> model.itemAuthorization.userId
        }

    val isReadAllowed = NonNullMutableLiveData(determineInitialIsReadAllowed())
    val isWriteAllowed = NonNullMutableLiveData(determineInitialIsWriteAllowed())

    private fun determineInitialIsReadAllowed(): Boolean {
        return when (model) {
            is Model.New -> false
            is Model.Existing -> {
                !model.itemAuthorization.deleted
            }
        }
    }

    private fun determineInitialIsWriteAllowed(): Boolean {
        return when (model) {
            is Model.New -> false
            is Model.Existing -> {
                !model.itemAuthorization.deleted && !model.itemAuthorization.readOnly
            }
        }
    }

    sealed class Model {
        class New(val username: String) : Model()
        class Existing(val itemAuthorization: ItemAuthorization) : Model()
    }
}

