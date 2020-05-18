package de.passbutler.app

import androidx.lifecycle.ViewModel
import de.passbutler.app.base.NonNullMutableLiveData
import de.passbutler.app.base.NonNullValueGetterLiveData
import de.passbutler.app.base.OptionalValueGetterLiveData
import de.passbutler.app.base.ValueGetterLiveData
import de.passbutler.app.base.viewmodels.EditableViewModel
import de.passbutler.app.base.viewmodels.EditingViewModel
import de.passbutler.common.UserManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger
import java.util.*

class ItemViewModel(
    val item: Item,
    val itemAuthorization: ItemAuthorization,
    private val userManager: UserManager
) : EditableViewModel<ItemEditingViewModel> {

    val id
        get() = item.id

    val title = OptionalValueGetterLiveData {
        itemData?.title
    }

    val subtitle
        get() = item.id

    val deleted
        get() = item.deleted

    val created
        get() = item.created

    var itemData: ItemData? = null
    private var itemKey: ByteArray? = null

    suspend fun decryptSensibleData(userItemEncryptionSecretKey: ByteArray): Result<Unit> {
        return try {
            val decryptedItemKey = itemAuthorization.itemKey.decrypt(userItemEncryptionSecretKey, CryptographicKey.Deserializer).resultOrThrowException().key
            itemKey = decryptedItemKey

            val decryptedItemData = item.data.decrypt(decryptedItemKey, ItemData.Deserializer).resultOrThrowException()
            itemData = decryptedItemData

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
        val itemData = itemData ?: throw IllegalStateException("The item data is null despite a ItemEditingViewModel is created!")

        // Pass a copy of the item key to `ItemEditingViewModel` to avoid it get cleared via reference
        val itemKeyCopy = itemKey?.copyOf() ?: throw IllegalStateException("The item key is null despite a ItemEditingViewModel is created!")

        val itemModel = ItemEditingViewModel.ItemModel.Existing(item, itemAuthorization, itemData, itemKeyCopy)
        return ItemEditingViewModel(itemModel, userManager.localRepository)
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

class ItemEditingViewModel(
    private var itemModel: ItemModel,
    private val localRepository: LocalRepository
) : ViewModel(), EditingViewModel {

    val isNewEntry = NonNullValueGetterLiveData {
        itemModel is ItemModel.New
    }

    val isModificationAllowed = NonNullValueGetterLiveData {
        (itemModel as? ItemModel.Existing)?.itemAuthorization?.readOnly?.not() ?: true
    }

    val id = ValueGetterLiveData {
        (itemModel as? ItemModel.Existing)?.item?.id
    }

    // TODO: Also bind other data to UI
    val title = NonNullMutableLiveData(itemModel.asExistingOrNull()?.itemData?.title ?: "")
    val password = NonNullMutableLiveData(itemModel.asExistingOrNull()?.itemData?.password ?: "")

    val modified = ValueGetterLiveData {
        (itemModel as? ItemModel.Existing)?.item?.modified
    }

    val created = ValueGetterLiveData {
        (itemModel as? ItemModel.Existing)?.item?.created
    }

    init {
        Logger.debug("Create new ItemEditingViewModel: item = ${itemModel.asExistingOrNull()?.item}, itemAuthorization = ${itemModel.asExistingOrNull()?.itemAuthorization}")
    }

    suspend fun save(): Result<Unit> {
        check(isModificationAllowed.value) { "The item is not allowed to save because it has only a readonly authorization!" }

        val currentItemModel = itemModel

        val saveResult = when (currentItemModel) {
            is ItemModel.New -> saveNewItem(currentItemModel)
            is ItemModel.Existing -> saveExistingItem(currentItemModel)
        }

        return when (saveResult) {
            is Success -> {
                itemModel = saveResult.result

                withContext(Dispatchers.Main) {
                    id.notifyChange()
                    isNewEntry.notifyChange()
                    isModificationAllowed.notifyChange()
                    modified.notifyChange()
                    created.notifyChange()
                }

                Success(Unit)
            }
            is Failure -> {
                Failure(saveResult.throwable)
            }
        }
    }

    private suspend fun saveNewItem(itemModel: ItemModel.New): Result<ItemModel.Existing> {
        val loggedInUserId = itemModel.loggedInUserViewModel.username
        val loggedInUserItemEncryptionPublicKey = itemModel.loggedInUserViewModel.itemEncryptionPublicKey.key

        val itemData = createItemData()

        return try {
            val (item, itemKey) = createNewItemAndKey(loggedInUserId, itemData).resultOrThrowException()
            localRepository.insertItem(item)

            val itemAuthorization = createNewItemAuthorization(loggedInUserId, loggedInUserItemEncryptionPublicKey, item, itemKey).resultOrThrowException()
            localRepository.insertItemAuthorization(itemAuthorization)

            val updatedItemModel = ItemModel.Existing(item, itemAuthorization, itemData, itemKey)
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
        val item = itemModel.item
        val itemKey = itemModel.itemKey

        return try {
            val updatedItem = createUpdatedItem(item, itemKey).resultOrThrowException()
            localRepository.updateItem(updatedItem)

            val updatedItemModel = ItemModel.Existing(updatedItem, itemModel.itemAuthorization, itemModel.itemData, itemModel.itemKey)
            Success(updatedItemModel)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    private suspend fun createUpdatedItem(item: Item, itemKey: ByteArray): Result<Item> {
        val protectedItemData = item.data
        val itemData = createItemData()

        return try {
            protectedItemData.update(itemKey, itemData).resultOrThrowException()

            val currentDate = Date()
            val updatedItem = item.copy(
                data = protectedItemData,
                modified = currentDate
            )

            Success(updatedItem)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    private fun createItemData(): ItemData {
        return ItemData(title.value, password.value)
    }

    suspend fun delete(): Result<Unit> {
        val existingItemModel = (itemModel as? ItemModel.Existing) ?: throw IllegalStateException("Only existing items can be deleted!")
        check(isModificationAllowed.value) { "The item is not allowed to delete because it has only a readonly authorization!" }

        // Only mark item as deleted (item authorization deletion is only managed via item shared screen)
        val deletedItem = existingItemModel.item.copy(
            deleted = true,
            modified = Date()
        )
        localRepository.updateItem(deletedItem)

        return Success(Unit)
    }

    sealed class ItemModel {
        class New(val loggedInUserViewModel: UserViewModel) : ItemModel()
        class Existing(val item: Item, val itemAuthorization: ItemAuthorization, val itemData: ItemData, val itemKey: ByteArray) : ItemModel()
    }

}

internal fun ItemEditingViewModel.ItemModel.asExistingOrNull(): ItemEditingViewModel.ItemModel.Existing? {
    return (this as? ItemEditingViewModel.ItemModel.Existing)
}