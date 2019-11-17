package de.sicherheitskritisch.passbutler

import androidx.lifecycle.ViewModel
import de.sicherheitskritisch.passbutler.base.Failure
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.NonNullMutableLiveData
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.Success
import de.sicherheitskritisch.passbutler.base.ValueGetterLiveData
import de.sicherheitskritisch.passbutler.base.clear
import de.sicherheitskritisch.passbutler.base.resultOrThrowException
import de.sicherheitskritisch.passbutler.base.viewmodels.EditableViewModel
import de.sicherheitskritisch.passbutler.base.viewmodels.EditingViewModel
import de.sicherheitskritisch.passbutler.crypto.EncryptionAlgorithm
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.ProtectedValue
import de.sicherheitskritisch.passbutler.database.models.Item
import de.sicherheitskritisch.passbutler.database.models.ItemAuthorization
import de.sicherheitskritisch.passbutler.database.models.ItemData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class ItemViewModel(
    val item: Item,
    val itemAuthorization: ItemAuthorization,
    private val userManager: UserManager
) : EditableViewModel<ItemEditingViewModel> {

    val id
        get() = item.id

    val title = ValueGetterLiveData {
        itemData?.title
    }

    val subtitle
        get() = item.id

    val created
        get() = item.created

    var itemData: ItemData? = null
        private set

    private var itemKey: ByteArray? = null

    suspend fun decryptSensibleData(userItemEncryptionSecretKey: ByteArray): Result<Unit> {
        return withContext(Dispatchers.Default) {
            try {
                val decryptedItemKey = itemAuthorization.itemKey.decrypt(userItemEncryptionSecretKey, CryptographicKey.Deserializer).key
                val decryptedItemData = item.data.decrypt(decryptedItemKey, ItemData.Deserializer)
                updateSensibleDataFields(decryptedItemKey, decryptedItemData)

                Success(Unit)
            } catch (exception: Exception) {
                Failure(exception)
            }
        }
    }

    private fun updateSensibleDataFields(itemKey: ByteArray, itemData: ItemData) {
        this.itemKey = itemKey
        this.itemData = itemData

        title.notifyChange()
    }

    fun clearSensibleData() {
        itemKey?.clear()
        itemKey = null

        itemData = null

        title.notifyChange()
    }

    override fun createEditingViewModel(): ItemEditingViewModel {
        val itemData = itemData ?: throw IllegalStateException("The item data is null despite a ItemEditingViewModel is created!")

        // Pass a copy of the item key to `ItemEditingViewModel` to avoid it get cleared via reference
        val itemKeyCopy = itemKey?.copyOf() ?: throw IllegalStateException("The item key is null despite a ItemEditingViewModel is created!")

        val itemModel = ItemModel.Existing(item, itemAuthorization, itemData, itemKeyCopy)
        return ItemEditingViewModel(itemModel, userManager)
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
    private val itemModel: ItemModel,
    private val userManager: UserManager
) : ViewModel(), EditingViewModel {

    val isNewEntry = itemModel is ItemModel.New

    val title = NonNullMutableLiveData(itemModel.asExisting()?.itemData?.title ?: "")
    val password = NonNullMutableLiveData(itemModel.asExisting()?.itemData?.password ?: "")

    init {
        L.d("ItemEditingViewModel", "init(): item = ${itemModel.asExisting()?.item}, itemAuthorization = ${itemModel.asExisting()?.itemAuthorization}")
    }

    suspend fun save(): Result<Unit> {
        val itemModel = itemModel

        // TODO: How to be sure, `save` is only called once, because `itemModel` won't change
        return when (itemModel) {
            is ItemModel.New -> saveNewItem(itemModel)
            is ItemModel.Existing -> saveExistingItem(itemModel)
        }
    }

    private suspend fun saveNewItem(itemModel: ItemModel.New): Result<Unit> {
        val loggedInUserId = itemModel.loggedInUserViewModel.username
        val loggedInUserItemEncryptionPublicKey = itemModel.loggedInUserViewModel.itemEncryptionPublicKey.key

        val itemData = createItemData()

        return try {
            val (item, itemKey) = createNewItemAndKey(loggedInUserId, itemData).resultOrThrowException()
            userManager.createItem(item)

            val itemAuthorization = createNewItemAuthorization(loggedInUserId, loggedInUserItemEncryptionPublicKey, item, itemKey).resultOrThrowException()
            userManager.createItemAuthorization(itemAuthorization)

            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    private suspend fun createNewItemAndKey(loggedInUserId: String, itemData: ItemData): Result<Pair<Item, ByteArray>> {
        val symmetricEncryptionAlgorithm = EncryptionAlgorithm.Symmetric.AES256GCM

        return try {
            val itemKey = withContext(Dispatchers.IO) {
                symmetricEncryptionAlgorithm.generateEncryptionKey()
            }

            val protectedItemData = withContext(Dispatchers.Default) {
                ProtectedValue.create(symmetricEncryptionAlgorithm, itemKey, itemData)
            }

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
            val protectedItemKey = withContext(Dispatchers.Default) {
                ProtectedValue.create(asymmetricEncryptionAlgorithm, loggedInUserItemEncryptionPublicKey, CryptographicKey(itemKey))
            }

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

    private suspend fun saveExistingItem(itemModel: ItemModel.Existing): Result<Unit> {
        val item = itemModel.item
        val itemKey = itemModel.itemKey

        return try {
            val updatedItem = createUpdatedItem(item, itemKey).resultOrThrowException()
            userManager.updateItem(updatedItem)

            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    private fun createUpdatedItem(item: Item, itemKey: ByteArray): Result<Item> {
        val protectedItemData = item.data
        val itemData = createItemData()

        return try {
            protectedItemData.update(itemKey, itemData)

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
        val itemModel = itemModel

        // TODO: Check if is readonly

        return if (itemModel is ItemModel.Existing) {
            val currentData = Date()

            val deletedItem = itemModel.item.copy(
                deleted = true,
                modified = currentData
            )
            userManager.updateItem(deletedItem)

            val deletedItemAuthorization = itemModel.itemAuthorization.copy(
                deleted = true,
                modified = currentData
            )
            userManager.updateItemAuthorization(deletedItemAuthorization)

            Success(Unit)
        } else {
            throw IllegalStateException("A new item can't be deleted!")
        }
    }
}

sealed class ItemModel {
    class New(val loggedInUserViewModel: UserViewModel) : ItemModel()
    class Existing(val item: Item, val itemAuthorization: ItemAuthorization, val itemData: ItemData, val itemKey: ByteArray) : ItemModel()
}

private fun ItemModel.asExisting(): ItemModel.Existing? {
    return (this as? ItemModel.Existing)
}