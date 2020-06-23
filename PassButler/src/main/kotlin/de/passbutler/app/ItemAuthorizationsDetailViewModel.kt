package de.passbutler.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import de.passbutler.app.base.NonNullDiscardableMutableLiveData
import de.passbutler.app.base.NonNullMutableLiveData
import de.passbutler.app.base.NonNullValueGetterLiveData
import de.passbutler.app.base.viewmodels.EditingViewModel
import de.passbutler.app.ui.ListItemIdentifiable
import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.contains
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

    val anyItemAuthorizationWasModified = NonNullValueGetterLiveData {
        _itemAuthorizationViewModels.value.any { it.isReadAllowed.isModified || it.isWriteAllowed.isModified }
    }

    private val itemAuthorizationViewModelsModifiedObserver = Observer<Boolean> {
        anyItemAuthorizationWasModified.notifyChange()
    }

    suspend fun initializeItemAuthorizationViewModels() {
        val itemViewModel = loggedInUserViewModel.itemViewModels.value.find { it.id == itemId }
        val item = itemViewModel?.item ?: throw IllegalStateException("The item is null despite the ItemAuthorizationViewModel is created!")

        // Pass a copy of the item key to avoid it get cleared via reference on screen lock
        val itemKeyCopy = itemViewModel.itemKey?.copyOf() ?: throw IllegalStateException("The item key is null despite the ItemAuthorizationViewModel is created!")

        val existingItemAuthorizationViewModels = createExistingItemAuthorizationViewModels(item)
        val provisionalItemAuthorizationViewModels = createProvisionalItemAuthorizationViewModels(existingItemAuthorizationViewModels, item, itemKeyCopy)

        val newItemAuthorizations = existingItemAuthorizationViewModels + provisionalItemAuthorizationViewModels

        withContext(Dispatchers.Main) {
            _itemAuthorizationViewModels.value.forEach {
                it.isReadAllowed.removeObserver(itemAuthorizationViewModelsModifiedObserver)
                it.isWriteAllowed.removeObserver(itemAuthorizationViewModelsModifiedObserver)
            }

            _itemAuthorizationViewModels.value = newItemAuthorizations

            _itemAuthorizationViewModels.value.forEach {
                it.isReadAllowed.observeForever(itemAuthorizationViewModelsModifiedObserver)
                it.isWriteAllowed.observeForever(itemAuthorizationViewModelsModifiedObserver)
            }
        }
    }

    private suspend fun createExistingItemAuthorizationViewModels(item: Item): List<ItemAuthorizationViewModel> {
        val users = localRepository.findAllUsers()
        val existingItemAuthorizationViewModels = localRepository.findItemAuthorizationForItem(item)
            .filter { itemAuthorization ->
                // Do not show item authorization of logged-in user
                itemAuthorization.userId != loggedInUserViewModel.id
            }
            .map { itemAuthorization ->
                val user = users.find { it.id == itemAuthorization.userId } ?: throw IllegalStateException("The user of the item authorization was not found!")
                ItemAuthorizationViewModel(ItemAuthorizationViewModel.ItemAuthorizationModel.Existing(user.username, itemAuthorization))
            }

        Logger.debug("existingItemAuthorizationViewModels = $existingItemAuthorizationViewModels")
        return existingItemAuthorizationViewModels
    }

    private suspend fun createProvisionalItemAuthorizationViewModels(
        existingItemAuthorizationViewModels: List<ItemAuthorizationViewModel>,
        item: Item,
        itemKey: ByteArray
    ): List<ItemAuthorizationViewModel> {
        val provisionalItemAuthorizationViewModels = localRepository.findAllUsers()
            .filter { user ->
                val userId = user.id

                // Do not show item authorization of logged-in user
                val itemAuthorizationOfLoggedInUser = userId == loggedInUserViewModel.id

                // Do not create provisional item authorization for existing item authorizations
                val itemAuthorizationAlreadyExists = existingItemAuthorizationViewModels.contains {
                    val itemAuthorizationUserId = (it.itemAuthorizationModel as ItemAuthorizationViewModel.ItemAuthorizationModel.Existing).itemAuthorization.userId
                    userId == itemAuthorizationUserId
                }

                !itemAuthorizationOfLoggedInUser && !itemAuthorizationAlreadyExists
            }
            .map { user ->
                val itemAuthorizationId = UUID.randomUUID().toString()
                ItemAuthorizationViewModel(
                    ItemAuthorizationViewModel.ItemAuthorizationModel.Provisional(
                        user.id,
                        user.username,
                        user.itemEncryptionPublicKey.key,
                        item,
                        itemKey,
                        itemAuthorizationId
                    )
                )
            }

        Logger.debug("provisionalItemAuthorizationViewModels = $provisionalItemAuthorizationViewModels")
        return provisionalItemAuthorizationViewModels
    }

    suspend fun save(): Result<Unit> {
        val currentItemAuthorizationViewModels = _itemAuthorizationViewModels.value

        val saveResults = listOf(
            saveExistingItemAuthorizations(currentItemAuthorizationViewModels),
            saveProvisionalItemAuthorizations(currentItemAuthorizationViewModels)
        )

        val firstFailure = saveResults.filterIsInstance(Failure::class.java).firstOrNull()

        return if (firstFailure != null) {
            Failure(firstFailure.throwable)
        } else {
            // Reinitialize list of `ItemAuthorizationViewModel` to be sure, the states are applied
            initializeItemAuthorizationViewModels()

            Success(Unit)
        }
    }

    private suspend fun saveExistingItemAuthorizations(currentItemAuthorizationViewModels: List<ItemAuthorizationViewModel>): Result<Unit> {
        val changedExistingItemAuthorizationViewModels = currentItemAuthorizationViewModels
            .filter { it.isReadAllowed.isModified || it.isWriteAllowed.isModified }

        var failedResultException: Throwable? = null
        val changedExistingItemAuthorizations = changedExistingItemAuthorizationViewModels.mapNotNull { itemAuthorizationViewModel ->
            (itemAuthorizationViewModel.itemAuthorizationModel as? ItemAuthorizationViewModel.ItemAuthorizationModel.Existing)?.let { itemAuthorizationModel ->
                val isReadAllowed = itemAuthorizationViewModel.isReadAllowed.value
                val isWriteAllowed = itemAuthorizationViewModel.isWriteAllowed.value
                val updateItemAuthorizationResult = itemAuthorizationModel.createItemAuthorization(isReadAllowed, isWriteAllowed)

                when (updateItemAuthorizationResult) {
                    is Success -> updateItemAuthorizationResult.result
                    is Failure -> {
                        failedResultException = updateItemAuthorizationResult.throwable
                        null
                    }
                }
            }
        }

        return failedResultException?.let {
            Failure(it)
        } ?: run {
            Logger.debug("changedExistingItemAuthorizations = $changedExistingItemAuthorizations")
            localRepository.updateItemAuthorization(*changedExistingItemAuthorizations.toTypedArray())

            Success(Unit)
        }
    }

    private suspend fun saveProvisionalItemAuthorizations(currentItemAuthorizationViewModels: List<ItemAuthorizationViewModel>): Result<Unit> {
        val changedProvisionalItemAuthorizationViewModels = currentItemAuthorizationViewModels
            .filter { it.isReadAllowed.isModified || it.isWriteAllowed.isModified }

        var failedResultException: Throwable? = null
        val changedProvisionalItemAuthorizations = changedProvisionalItemAuthorizationViewModels.mapNotNull { itemAuthorizationViewModel ->
            (itemAuthorizationViewModel.itemAuthorizationModel as? ItemAuthorizationViewModel.ItemAuthorizationModel.Provisional)?.let { itemAuthorizationModel ->
                val isReadAllowed = itemAuthorizationViewModel.isReadAllowed.value
                val isWriteAllowed = itemAuthorizationViewModel.isWriteAllowed.value
                val createItemAuthorizationResult = itemAuthorizationModel.createItemAuthorization(isReadAllowed, isWriteAllowed)

                when (createItemAuthorizationResult) {
                    is Success -> createItemAuthorizationResult.result
                    is Failure -> {
                        failedResultException = createItemAuthorizationResult.throwable
                        null
                    }
                }
            }
        }

        return failedResultException?.let {
            Failure(it)
        } ?: run {
            Logger.debug("changedProvisionalItemAuthorizations = $changedProvisionalItemAuthorizations")
            localRepository.insertItemAuthorization(*changedProvisionalItemAuthorizations.toTypedArray())

            Success(Unit)
        }
    }
}

class ItemAuthorizationViewModel(val itemAuthorizationModel: ItemAuthorizationModel) : ListItemIdentifiable {

    override val listItemId: String
        get() = when (itemAuthorizationModel) {
            is ItemAuthorizationModel.Provisional -> itemAuthorizationModel.itemAuthorizationId
            is ItemAuthorizationModel.Existing -> itemAuthorizationModel.itemAuthorization.id
        }

    val username: String
        get() = when (itemAuthorizationModel) {
            is ItemAuthorizationModel.Provisional -> itemAuthorizationModel.username
            is ItemAuthorizationModel.Existing -> itemAuthorizationModel.username
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
            val username: String,
            val userItemEncryptionPublicKey: ByteArray,
            val item: Item,
            val itemKey: ByteArray,
            val itemAuthorizationId: String
        ) : ItemAuthorizationModel() {
            override suspend fun createItemAuthorization(isReadAllowed: Boolean, isWriteAllowed: Boolean): Result<ItemAuthorization> {
                val asymmetricEncryptionAlgorithm = EncryptionAlgorithm.Asymmetric.RSA2048OAEP

                return try {
                    val protectedItemKey = ProtectedValue.create(asymmetricEncryptionAlgorithm, userItemEncryptionPublicKey, CryptographicKey(itemKey)).resultOrThrowException()
                    val currentDate = Date()
                    val createdItemAuthorization = ItemAuthorization(
                        id = itemAuthorizationId,
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

        class Existing(
            val username: String,
            val itemAuthorization: ItemAuthorization
        ) : ItemAuthorizationModel() {
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