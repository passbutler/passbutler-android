package de.passbutler.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import de.passbutler.app.base.DependentNonNullValueGetterLiveData
import de.passbutler.app.base.DependentOptionalValueGetterLiveData
import de.passbutler.app.base.NonNullDiscardableMutableLiveData
import de.passbutler.app.base.NonNullMutableLiveData
import de.passbutler.app.base.OptionalValueGetterLiveData
import de.passbutler.app.base.viewmodels.EditableViewModel
import de.passbutler.app.base.viewmodels.EditingViewModel
import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.clear
import de.passbutler.common.base.resultOrThrowException
import de.passbutler.common.crypto.EncryptionAlgorithm
import de.passbutler.common.crypto.models.CryptographicKey
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.database.LocalRepository
import de.passbutler.common.database.models.Item
import de.passbutler.common.database.models.ItemAuthorization
import de.passbutler.common.database.models.ItemData
import de.passbutler.common.database.models.UserType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger
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
            val itemAuthorizations = localRepository.findItemAuthorizationForItem(item).map {
                ItemAuthorizationViewModel(it)
            }

            withContext(Dispatchers.Main) {
                _itemAuthorizations.value = itemAuthorizations
            }
        }
    }
}

class ItemAuthorizationViewModel(val itemAuthorization: ItemAuthorization) : Identifiable {
    override val listItemId: String
        get() = itemAuthorization.id
}