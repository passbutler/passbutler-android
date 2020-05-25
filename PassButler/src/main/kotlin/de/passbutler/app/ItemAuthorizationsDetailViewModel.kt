package de.passbutler.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import de.passbutler.app.base.NonNullDiscardableMutableLiveData
import de.passbutler.app.base.NonNullMutableLiveData
import de.passbutler.app.base.viewmodels.EditingViewModel
import de.passbutler.app.ui.ListItemIdentifiable
import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.containsNot
import de.passbutler.common.base.resultOrThrowException
import de.passbutler.common.crypto.EncryptionAlgorithm
import de.passbutler.common.crypto.models.CryptographicKey
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.database.LocalRepository
import de.passbutler.common.database.models.Item
import de.passbutler.common.database.models.ItemAuthorization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger
import java.util.*

class ItemAuthorizationsDetailViewModel(
    private val itemId: String,
    private val loggedInUserViewModel: UserViewModel,
    private val localRepository: LocalRepository
) : ViewModel(), EditingViewModel {

    val itemAuthorizationViewModels: LiveData<List<ItemAuthorizationViewModel>>
        get() = _itemAuthorizationViewModels

    private val _itemAuthorizationViewModels = NonNullMutableLiveData<List<ItemAuthorizationViewModel>>(emptyList())

    suspend fun initializeItemAuthorizationViewModels() {
        val itemViewModel = loggedInUserViewModel.itemViewModels.value.find { it.id == itemId }
        val item = itemViewModel?.item ?: throw IllegalStateException("The item is null despite the ItemAuthorizationViewModel are created!")

        // Pass a copy of the item key to avoid it get cleared via reference
        val itemKeyCopy = itemViewModel.itemKey?.copyOf() ?: throw IllegalStateException("The item key is null despite the ItemAuthorizationViewModel are created!")

        val existingItemAuthorizationViewModels = createExistingItemAuthorizationViewModels(item)
        val provisionalItemAuthorizationViewModels = createProvisionalItemAuthorizationViewModels(existingItemAuthorizationViewModels, item, itemKeyCopy)

        val newItemAuthorizations = existingItemAuthorizationViewModels + provisionalItemAuthorizationViewModels

        withContext(Dispatchers.Main) {
            _itemAuthorizationViewModels.value = newItemAuthorizations
        }
    }

    private suspend fun createExistingItemAuthorizationViewModels(item: Item): List<ItemAuthorizationViewModel> {
        val existingItemAuthorizationViewModels = localRepository.findItemAuthorizationForItem(item)
            .filter { itemAuthorization ->
                // Do not show item authorization of logged-in user
                itemAuthorization.userId != loggedInUserViewModel.id
            }
            .map {
                ItemAuthorizationViewModel(ItemAuthorizationViewModel.ItemAuthorizationModel.Existing(it))
            }

        Logger.debug("existingItemAuthorizationViewModels = $existingItemAuthorizationViewModels")
        return existingItemAuthorizationViewModels
    }

    private suspend fun createProvisionalItemAuthorizationViewModels(
        existingItemAuthorizationViewModels: List<ItemAuthorizationViewModel>,
        item: Item,
        itemKeyCopy: ByteArray
    ): List<ItemAuthorizationViewModel> {
        val provisionalItemAuthorizationViewModels = localRepository.findAllUsers()
            .filter { user ->
                // Do not show item authorization of logged-in user
                user.username != loggedInUserViewModel.id
            }
            .filter { user ->
                val userId = user.username

                // Do not create provisional item authorizations for existing item authorizations
                existingItemAuthorizationViewModels.containsNot {
                    val itemAuthorizationUserId = (it.itemAuthorizationModel as ItemAuthorizationViewModel.ItemAuthorizationModel.Existing).itemAuthorization.userId
                    userId == itemAuthorizationUserId
                }
            }
            .map {
                val provisionalItemAuthorizationId = UUID.randomUUID().toString()
                ItemAuthorizationViewModel(
                    ItemAuthorizationViewModel.ItemAuthorizationModel.Provisional(
                        it.username,
                        it.itemEncryptionPublicKey.key,
                        item,
                        itemKeyCopy,
                        provisionalItemAuthorizationId
                    )
                )
            }

        Logger.debug("provisionalItemAuthorizationViewModels = $provisionalItemAuthorizationViewModels")
        return provisionalItemAuthorizationViewModels
    }

    suspend fun save(): Result<Unit> {
        val currentItemAuthorizationViewModels = _itemAuthorizationViewModels.value

        saveExistingItemAuthorizations(currentItemAuthorizationViewModels)
        saveProvisionalItemAuthorizations(currentItemAuthorizationViewModels)

        // Reinitialize list of `ItemAuthorizationViewModel` to be sure, the states are applied
        initializeItemAuthorizationViewModels()

        return Success(Unit)
    }

    private suspend fun saveExistingItemAuthorizations(currentItemAuthorizationViewModels: List<ItemAuthorizationViewModel>) {
        val changedExistingItemAuthorizationViewModels = currentItemAuthorizationViewModels
            .filter { it.isReadAllowed.isModified || it.isWriteAllowed.isModified }

        val changedExistingItemAuthorizations = changedExistingItemAuthorizationViewModels.mapNotNull { itemAuthorizationViewModel ->
            (itemAuthorizationViewModel.itemAuthorizationModel as? ItemAuthorizationViewModel.ItemAuthorizationModel.Existing)?.let { itemAuthorizationModel ->
                val isReadAllowed = itemAuthorizationViewModel.isReadAllowed.value
                val isWriteAllowed = itemAuthorizationViewModel.isWriteAllowed.value
                val updateItemAuthorizationResult = itemAuthorizationModel.createItemAuthorization(isReadAllowed, isWriteAllowed)

                when (updateItemAuthorizationResult) {
                    is Success -> updateItemAuthorizationResult.result
                    is Failure -> {
                        // TODO: Log failure?
                        null
                    }
                }
            }
        }

        Logger.debug("changedExistingItemAuthorizations = $changedExistingItemAuthorizations")
        localRepository.updateItemAuthorization(*changedExistingItemAuthorizations.toTypedArray())
    }

    private suspend fun saveProvisionalItemAuthorizations(currentItemAuthorizationViewModels: List<ItemAuthorizationViewModel>) {
        val changedProvisionalItemAuthorizationViewModels = currentItemAuthorizationViewModels
            .filter { it.isReadAllowed.isModified || it.isWriteAllowed.isModified }

        val changedProvisionalItemAuthorizations = changedProvisionalItemAuthorizationViewModels.mapNotNull { itemAuthorizationViewModel ->
            (itemAuthorizationViewModel.itemAuthorizationModel as? ItemAuthorizationViewModel.ItemAuthorizationModel.Provisional)?.let { itemAuthorizationModel ->
                val isReadAllowed = itemAuthorizationViewModel.isReadAllowed.value
                val isWriteAllowed = itemAuthorizationViewModel.isWriteAllowed.value
                val createItemAuthorizationResult = itemAuthorizationModel.createItemAuthorization(isReadAllowed, isWriteAllowed)

                when (createItemAuthorizationResult) {
                    is Success -> createItemAuthorizationResult.result
                    is Failure -> {
                        // TODO: Log failure?
                        null
                    }
                }
            }
        }

        Logger.debug("changedProvisionalItemAuthorizations = $changedProvisionalItemAuthorizations")
        localRepository.insertItemAuthorization(*changedProvisionalItemAuthorizations.toTypedArray())
    }
}

class ItemAuthorizationViewModel(val itemAuthorizationModel: ItemAuthorizationModel) : ListItemIdentifiable {

    override val listItemId: String
        get() = when (itemAuthorizationModel) {
            is ItemAuthorizationModel.Provisional -> itemAuthorizationModel.provisionalItemAuthorizationId
            is ItemAuthorizationModel.Existing -> itemAuthorizationModel.itemAuthorization.id
        }

    val username: String
        get() = when (itemAuthorizationModel) {
            is ItemAuthorizationModel.Provisional -> itemAuthorizationModel.userId
            is ItemAuthorizationModel.Existing -> itemAuthorizationModel.itemAuthorization.userId
        }

    val isReadAllowed = NonNullDiscardableMutableLiveData(determineInitialIsReadAllowed())
    val isWriteAllowed = NonNullDiscardableMutableLiveData(determineInitialIsWriteAllowed())

    private fun determineInitialIsReadAllowed(): Boolean {
        return when (itemAuthorizationModel) {
            is ItemAuthorizationModel.Provisional -> false
            is ItemAuthorizationModel.Existing -> {
                !itemAuthorizationModel.itemAuthorization.deleted
            }
        }
    }

    private fun determineInitialIsWriteAllowed(): Boolean {
        return when (itemAuthorizationModel) {
            is ItemAuthorizationModel.Provisional -> false
            is ItemAuthorizationModel.Existing -> {
                !itemAuthorizationModel.itemAuthorization.deleted && !itemAuthorizationModel.itemAuthorization.readOnly
            }
        }
    }

    sealed class ItemAuthorizationModel {
        abstract suspend fun createItemAuthorization(isReadAllowed: Boolean, isWriteAllowed: Boolean): Result<ItemAuthorization>

        class Provisional(
            val userId: String,
            val userItemEncryptionPublicKey: ByteArray,
            val item: Item,
            val itemKey: ByteArray,
            val provisionalItemAuthorizationId: String
        ) : ItemAuthorizationModel() {
            override suspend fun createItemAuthorization(isReadAllowed: Boolean, isWriteAllowed: Boolean): Result<ItemAuthorization> {
                val asymmetricEncryptionAlgorithm = EncryptionAlgorithm.Asymmetric.RSA2048OAEP

                return try {
                    val protectedItemKey = ProtectedValue.create(asymmetricEncryptionAlgorithm, userItemEncryptionPublicKey, CryptographicKey(itemKey)).resultOrThrowException()
                    val currentDate = Date()
                    val createdItemAuthorization = ItemAuthorization(
                        id = provisionalItemAuthorizationId,
                        userId = userId,
                        itemId = item.id,
                        itemKey = protectedItemKey,
                        readOnly = !isWriteAllowed,
                        deleted = !isReadAllowed,
                        modified = currentDate,
                        created = currentDate
                    )

                    Success(createdItemAuthorization)
                } catch (exception: Exception) {
                    Failure(exception)
                }
            }
        }

        class Existing(val itemAuthorization: ItemAuthorization) : ItemAuthorizationModel() {
            override suspend fun createItemAuthorization(isReadAllowed: Boolean, isWriteAllowed: Boolean): Result<ItemAuthorization> {
                val currentDate = Date()
                val updatedItemAuthorization = itemAuthorization.copy(
                    readOnly = !isWriteAllowed,
                    deleted = !isReadAllowed,
                    modified = currentDate
                )

                return Success(updatedItemAuthorization)
            }
        }
    }
}