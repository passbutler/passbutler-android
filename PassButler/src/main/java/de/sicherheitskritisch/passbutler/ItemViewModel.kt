package de.sicherheitskritisch.passbutler

import androidx.lifecycle.ViewModel
import de.sicherheitskritisch.passbutler.base.Failure
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.NonNullMutableLiveData
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.Success
import de.sicherheitskritisch.passbutler.base.ValueGetterLiveData
import de.sicherheitskritisch.passbutler.base.clear
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
            val itemKeyDecryptionResult = itemAuthorization.itemKey.decryptWithResult(userItemEncryptionSecretKey, CryptographicKey.Deserializer)

            when (itemKeyDecryptionResult) {
                is Success -> {
                    val decryptedItemKey = itemKeyDecryptionResult.result.key
                    val itemDataDecryptionResult = item.data.decryptWithResult(decryptedItemKey, ItemData.Deserializer)

                    when (itemDataDecryptionResult) {
                        is Success -> {
                            val decryptedItemData = itemDataDecryptionResult.result
                            updateSensibleDataFields(decryptedItemKey, decryptedItemData)

                            Success(Unit)
                        }
                        is Failure -> {
                            Failure(itemDataDecryptionResult.throwable)
                        }
                    }
                }
                is Failure -> {
                    Failure(itemKeyDecryptionResult.throwable)
                }
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

        val itemModel = ItemModel.Existing(item, itemData, itemKeyCopy)
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
        L.d("ItemEditingViewModel", "init(): id = ${itemModel.asExisting()?.item?.id}")
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
        val createItemAndKeyResult = createNewItemAndKey(loggedInUserId, itemData)

        return when (createItemAndKeyResult) {
            is Success -> {
                val (item, itemKey) = createItemAndKeyResult.result
                userManager.createItem(item)

                val itemAuthorizationResult = createNewItemAuthorization(loggedInUserId, loggedInUserItemEncryptionPublicKey, item, itemKey)

                when (itemAuthorizationResult) {
                    is Success -> {
                        val itemAuthorization = itemAuthorizationResult.result
                        userManager.createItemAuthorization(itemAuthorization)

                        Success(Unit)
                    }
                    is Failure -> itemAuthorizationResult
                }
            }
            is Failure -> createItemAndKeyResult
        }
    }

    private suspend fun createNewItemAndKey(loggedInUserId: String, itemData: ItemData): Result<Pair<Item, ByteArray>> {
        val symmetricEncryptionAlgorithm = EncryptionAlgorithm.Symmetric.AES256GCM

        // TODO: Handle exception / result
        val itemKey = withContext(Dispatchers.IO) {
            symmetricEncryptionAlgorithm.generateEncryptionKey()
        }

        // TODO: Handle exception / result
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

        // TODO: Proper result
        return Success(Pair(item, itemKey))
    }

    private suspend fun createNewItemAuthorization(loggedInUserId: String, loggedInUserItemEncryptionPublicKey: ByteArray, item: Item, itemKey: ByteArray): Result<ItemAuthorization> {
        val asymmetricEncryptionAlgorithm = EncryptionAlgorithm.Asymmetric.RSA2048OAEP

        // TODO: Handle exception / result
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

        // TODO: Proper result
        return Success(itemAuthorization)
    }

    private suspend fun saveExistingItem(itemModel: ItemModel.Existing): Result<Unit> {
        val item = itemModel.item
        val itemKey = itemModel.itemKey

        val updatedItemResult = createUpdatedItem(item, itemKey)

        return when (updatedItemResult) {
            is Success -> {
                val updatedItem = updatedItemResult.result
                userManager.updateItem(updatedItem)

                Success(Unit)
            }
            is Failure -> updatedItemResult
        }
    }

    private fun createUpdatedItem(item: Item, itemKey: ByteArray): Result<Item> {
        val protectedItemData = item.data

        // TODO: Handle exception / result
        val itemData = createItemData()
        protectedItemData.update(itemKey, itemData)

        val currentDate = Date()
        val updatedItem = item.copy(
            data = protectedItemData,
            modified = currentDate
        )

        // TODO: Proper result
        return Success(updatedItem)
    }

    private fun createItemData(): ItemData {
        return ItemData(title.value, password.value)
    }
}

sealed class ItemModel {
    class New(val loggedInUserViewModel: UserViewModel) : ItemModel()
    class Existing(val item: Item, val itemData: ItemData, val itemKey: ByteArray) : ItemModel()
}

private fun ItemModel.asExisting(): ItemModel.Existing? {
    return (this as? ItemModel.Existing)
}