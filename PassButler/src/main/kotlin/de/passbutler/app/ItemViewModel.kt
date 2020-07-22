package de.passbutler.app

import androidx.lifecycle.ViewModel
import de.passbutler.app.base.viewmodels.EditableViewModel
import de.passbutler.app.base.viewmodels.EditingViewModel
import de.passbutler.common.base.DependentValueGetterBindable
import de.passbutler.common.base.DiscardableMutableBindable
import de.passbutler.common.base.Failure
import de.passbutler.common.base.MutableBindable
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.ValueGetterBindable
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

class ItemViewModel(
    val item: Item,
    val itemAuthorization: ItemAuthorization,
    private val itemOwnerUsername: String,
    private val loggedInUserViewModel: UserViewModel,
    private val localRepository: LocalRepository
) : EditableViewModel<ItemEditingViewModel> {

    val id
        get() = item.id

    // TODO: Bindable needed
    val title = ValueGetterBindable {
        itemData?.title
    }

    val subtitle
        get() = item.id

    val deleted
        get() = item.deleted

    val created
        get() = item.created

    var itemData: ItemData? = null
        private set

    var itemKey: ByteArray? = null
        private set

    suspend fun decryptSensibleData(userItemEncryptionSecretKey: ByteArray): Result<Unit> {
        return try {
            val decryptedItemKey = itemAuthorization.itemKey.decrypt(userItemEncryptionSecretKey, CryptographicKey.Deserializer).resultOrThrowException().key
            itemKey = decryptedItemKey

            val decryptedItemData = item.data.decrypt(decryptedItemKey, ItemData.Deserializer).resultOrThrowException()
            itemData = decryptedItemData

            // TODO: All `withContext(Dispatchers.Main)` needed?
            withContext(Dispatchers.Main) {
                // Notify `itemData` dependent observables
                title.notifyChange()
            }

            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    fun clearSensibleData() {
        itemKey?.clear()
        itemKey = null

        itemData = null
    }

    override fun createEditingViewModel(): ItemEditingViewModel {
        val itemData = unlockedItemData

        // Pass a copy of the item key to `ItemEditingViewModel` to avoid it get cleared via reference on screen lock
        val itemKeyCopy = itemKey?.copyOf() ?: throw IllegalStateException("The item key is null despite a ItemEditingViewModel is created!")

        val itemModel = ItemEditingViewModel.ItemModel.Existing(item, itemAuthorization, itemOwnerUsername, itemData, itemKeyCopy)
        return ItemEditingViewModel(itemModel, loggedInUserViewModel, localRepository)
    }

    /**
     * The methods `equals()` and `hashCode()` only check the `item` and `itemAuthorization` fields because only these makes an item unique.
     * The `itemData` and `itemKey` is just a state of the item that should not cause the item list to change.
     */

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ItemViewModel

        if (item != other.item) return false
        if (itemAuthorization != other.itemAuthorization) return false

        return true
    }

    override fun hashCode(): Int {
        var result = item.hashCode()
        result = 31 * result + itemAuthorization.hashCode()
        return result
    }
}

val ItemViewModel.unlockedItemData: ItemData
    get() = itemData ?: throw IllegalStateException("The item data is null despite it was guaranteed to be unlocked!")

class ItemEditingViewModel private constructor(
    private val itemModel: MutableBindable<ItemModel>,
    private val loggedInUserViewModel: UserViewModel,
    private val localRepository: LocalRepository
) : ViewModel(), EditingViewModel {

    val hidePasswordsEnabled
        get() = loggedInUserViewModel.hidePasswordsEnabled.value == true

    val isItemAuthorizationAvailable: Boolean
        get() {
            // Item authorization feature makes only sense on a server
            return loggedInUserViewModel.userType == UserType.REMOTE
        }

    val isNewItem = DependentValueGetterBindable(itemModel) {
        itemModel.value is ItemModel.New
    }

    val isItemModificationAllowed = DependentValueGetterBindable(itemModel) {
        itemModel.value.asExistingOrNull()?.itemAuthorization?.readOnly?.not() ?: true
    }

    val itemAuthorizationModifiedDate = DependentValueGetterBindable(itemModel) {
        itemModel.value.asExistingOrNull()?.itemAuthorization?.modified
    }

    val isItemAuthorizationAllowed = DependentValueGetterBindable(itemModel) {
        // Checks if the item is owned by logged-in user
        itemModel.value.asExistingOrNull()?.item?.userId == loggedInUserViewModel.id
    }

    val id = DependentValueGetterBindable(itemModel) {
        itemModel.value.asExistingOrNull()?.item?.id
    }

    val title = DiscardableMutableBindable(initialItemData?.title ?: "")
    val username = DiscardableMutableBindable(initialItemData?.username ?: "")
    val password = DiscardableMutableBindable(initialItemData?.password ?: "")
    val url = DiscardableMutableBindable(initialItemData?.url ?: "")
    val notes = DiscardableMutableBindable(initialItemData?.notes ?: "")

    val ownerUsername = DependentValueGetterBindable(itemModel) {
        itemModel.value.asExistingOrNull()?.itemOwnerUsername
    }

    val modified = DependentValueGetterBindable(itemModel) {
        itemModel.value.asExistingOrNull()?.item?.modified
    }

    val created = DependentValueGetterBindable(itemModel) {
        itemModel.value.asExistingOrNull()?.item?.created
    }

    private val initialItemData
        get() = itemModel.value.asExistingOrNull()?.itemData

    constructor(
        initialItemModel: ItemModel,
        loggedInUserViewModel: UserViewModel,
        localRepository: LocalRepository
    ) : this(
        MutableBindable(initialItemModel),
        loggedInUserViewModel,
        localRepository
    )

    init {
        val existingItemModel = itemModel.value.asExistingOrNull()
        Logger.debug("Create new ItemEditingViewModel: item = ${existingItemModel?.item}, itemAuthorization = ${existingItemModel?.itemAuthorization}")
    }

    suspend fun save(): Result<Unit> {
        check(isItemModificationAllowed.value) { "The item is not allowed to save because it has only a readonly item authorization!" }

        val currentItemModel = itemModel.value

        val saveResult = when (currentItemModel) {
            is ItemModel.New -> saveNewItem()
            is ItemModel.Existing -> saveExistingItem(currentItemModel)
        }

        return when (saveResult) {
            is Success -> {
                withContext(Dispatchers.Main) {
                    itemModel.value = saveResult.result
                    commitChangesAsInitialValue()
                }

                Success(Unit)
            }
            is Failure -> {
                Failure(saveResult.throwable)
            }
        }
    }

    private suspend fun saveNewItem(): Result<ItemModel.Existing> {
        val loggedInUserId = loggedInUserViewModel.id
        val loggedInUserItemEncryptionPublicKey = loggedInUserViewModel.itemEncryptionPublicKey.key

        val itemData = createItemData()

        return try {
            val (item, itemKey) = createNewItemAndKey(loggedInUserId, itemData).resultOrThrowException()
            localRepository.insertItem(item)

            val itemAuthorization = createNewItemAuthorization(loggedInUserId, loggedInUserItemEncryptionPublicKey, item, itemKey).resultOrThrowException()
            localRepository.insertItemAuthorization(itemAuthorization)

            val itemOwnerUsername = loggedInUserViewModel.username.value

            val updatedItemModel = ItemModel.Existing(item, itemAuthorization, itemOwnerUsername, itemData, itemKey)
            Success(updatedItemModel)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    private suspend fun createNewItemAndKey(loggedInUserId: String, itemData: ItemData): Result<Pair<Item, ByteArray>> {
        val symmetricEncryptionAlgorithm = EncryptionAlgorithm.Symmetric.AES256GCM

        return try {
            val itemKey = symmetricEncryptionAlgorithm.generateEncryptionKey().resultOrThrowException()
            val protectedItemData = ProtectedValue.create(symmetricEncryptionAlgorithm, itemKey, itemData).resultOrThrowException()

            val currentDate = Date()
            val item = Item(
                id = UUID.randomUUID().toString(),
                userId = loggedInUserId,
                data = protectedItemData,
                deleted = false,
                modified = currentDate,
                created = currentDate
            )

            Success(Pair(item, itemKey))
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    private suspend fun createNewItemAuthorization(loggedInUserId: String, loggedInUserItemEncryptionPublicKey: ByteArray, item: Item, itemKey: ByteArray): Result<ItemAuthorization> {
        val asymmetricEncryptionAlgorithm = EncryptionAlgorithm.Asymmetric.RSA2048OAEP

        return try {
            val protectedItemKey = ProtectedValue.create(asymmetricEncryptionAlgorithm, loggedInUserItemEncryptionPublicKey, CryptographicKey(itemKey)).resultOrThrowException()
            val currentDate = Date()
            val itemAuthorization = ItemAuthorization(
                id = UUID.randomUUID().toString(),
                userId = loggedInUserId,
                itemId = item.id,
                itemKey = protectedItemKey,
                readOnly = false,
                deleted = false,
                modified = currentDate,
                created = currentDate
            )

            Success(itemAuthorization)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    private suspend fun saveExistingItem(itemModel: ItemModel.Existing): Result<ItemModel.Existing> {
        return try {
            val item = itemModel.item
            val itemKey = itemModel.itemKey
            val (updatedItem, updatedItemData) = createUpdatedItem(item, itemKey).resultOrThrowException()
            localRepository.updateItem(updatedItem)

            val updatedItemModel = ItemModel.Existing(updatedItem, itemModel.itemAuthorization, itemModel.itemOwnerUsername, updatedItemData, itemModel.itemKey)
            Success(updatedItemModel)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    private suspend fun createUpdatedItem(item: Item, itemKey: ByteArray): Result<Pair<Item, ItemData>> {
        return try {
            val updatedItemData = createItemData()
            val newProtectedItemData = item.data.update(itemKey, updatedItemData).resultOrThrowException()
            val currentDate = Date()

            val updatedItem = item.copy(
                data = newProtectedItemData,
                modified = currentDate
            )

            Success(Pair(updatedItem, updatedItemData))
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    private fun createItemData(): ItemData {
        return ItemData(title.value, username.value, password.value, url.value, notes.value)
    }

    private fun commitChangesAsInitialValue() {
        listOf(
            title,
            username,
            password,
            url,
            notes
        ).forEach {
            it.commitChangeAsInitialValue()
        }
    }

    suspend fun delete(): Result<Unit> {
        val existingItemModel = (itemModel.value as? ItemModel.Existing) ?: throw IllegalStateException("Only existing items can be deleted!")
        check(isItemModificationAllowed.value) { "The item is not allowed to delete because it has only a readonly item authorization!" }

        // Only mark item as deleted (item authorization deletion is only managed via item authorizations detail screen)
        val deletedItem = existingItemModel.item.copy(
            deleted = true,
            modified = Date()
        )
        localRepository.updateItem(deletedItem)

        return Success(Unit)
    }

    sealed class ItemModel {
        object New : ItemModel()

        class Existing(
            val item: Item,
            val itemAuthorization: ItemAuthorization,
            val itemOwnerUsername: String,
            val itemData: ItemData,
            val itemKey: ByteArray
        ) : ItemModel()
    }
}

internal fun ItemEditingViewModel.ItemModel.asExistingOrNull(): ItemEditingViewModel.ItemModel.Existing? {
    return (this as? ItemEditingViewModel.ItemModel.Existing)
}