package de.sicherheitskritisch.passbutler

import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.Failure
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.NonNullMutableLiveData
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.Success
import de.sicherheitskritisch.passbutler.base.ValueGetterLiveData
import de.sicherheitskritisch.passbutler.base.clear
import de.sicherheitskritisch.passbutler.base.createRequestSendingJob
import de.sicherheitskritisch.passbutler.base.viewmodels.EditableViewModel
import de.sicherheitskritisch.passbutler.base.viewmodels.EditingViewModel
import de.sicherheitskritisch.passbutler.base.viewmodels.ManualCancelledCoroutineScopeViewModel
import de.sicherheitskritisch.passbutler.crypto.EncryptionAlgorithm
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.ProtectedValue
import de.sicherheitskritisch.passbutler.database.models.Item
import de.sicherheitskritisch.passbutler.database.models.ItemAuthorization
import de.sicherheitskritisch.passbutler.database.models.ItemData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        itemData = null

        itemKey?.clear()
        itemKey = null
    }

    override fun createEditingViewModel(): ItemEditingViewModel {
        val itemData = itemData ?: throw IllegalStateException("The item data is null despite a ItemEditingViewModel is created!")
        val itemKey = itemKey ?: throw IllegalStateException("The item key is null despite a ItemEditingViewModel is created!")

        val itemModel = ItemModel.Existing(item, itemData, itemKey)
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
) : ManualCancelledCoroutineScopeViewModel(), EditingViewModel {

    val title = NonNullMutableLiveData(itemModel.asExisting()?.itemData?.title ?: "")
    val password = NonNullMutableLiveData(itemModel.asExisting()?.itemData?.password ?: "")

    val saveRequestSendingViewModel = DefaultRequestSendingViewModel()

    private var saveCoroutineJob: Job? = null

    init {
        L.d("ItemEditingViewModel", "init(): id = ${itemModel.asExisting()?.item?.id}")
    }

    fun save() {
        saveCoroutineJob?.cancel()
        saveCoroutineJob = createRequestSendingJob(saveRequestSendingViewModel) {
            val itemModel = itemModel

            when (itemModel) {
                is ItemModel.Creating -> saveNewItem(itemModel)
                is ItemModel.Existing -> saveExistingItem(itemModel)
            }
        }
    }

    private suspend fun saveNewItem(creatingItemModel1: ItemModel.Creating) {
        val loggedInUserId = creatingItemModel.loggedInUserViewModel.username
        val loggedInUserItemEncryptionPublicKey = creatingItemModel.loggedInUserViewModel.itemEncryptionPublicKey.key

        val itemData = createItemData()

        val (item, itemKey) = createNewItemAndKey(loggedInUserId, itemData)
        userManager.createItem(item)

        val itemAuthorization = createNewItemAuthorization(loggedInUserId, loggedInUserItemEncryptionPublicKey, item, itemKey)
        userManager.createItemAuthorization(itemAuthorization)
    }

    private fun createNewItemAndKey(loggedInUserId: String, itemData: ItemData): Pair<Item, ByteArray> {
        val symmetricEncryptionAlgorithm = EncryptionAlgorithm.Symmetric.AES256GCM

        // TODO: Handle exception / result
        val itemKey = symmetricEncryptionAlgorithm.generateEncryptionKey()

        // TODO: Handle exception / result
        val protectedItemData = ProtectedValue.create(symmetricEncryptionAlgorithm, itemKey, itemData)

        val currentDate = Date()

        val item = Item(
            id = UUID.randomUUID().toString(),
            userId = loggedInUserId,
            data = protectedItemData,
            deleted = false,
            modified = currentDate,
            created = currentDate
        )

        return Pair(item, itemKey)
    }

    private fun createNewItemAuthorization(loggedInUserId: String, loggedInUserItemEncryptionPublicKey: ByteArray, item: Item, itemKey: ByteArray): ItemAuthorization {
        val asymmetricEncryptionAlgorithm = EncryptionAlgorithm.Asymmetric.RSA2048OAEP

        // TODO: Handle exception / result
        val protectedItemKey = ProtectedValue.create(asymmetricEncryptionAlgorithm, loggedInUserItemEncryptionPublicKey, CryptographicKey(itemKey))

        val currentDate = Date()

        return ItemAuthorization(
            id = UUID.randomUUID().toString(),
            userId = loggedInUserId,
            itemId = item.id,
            itemKey = protectedItemKey,
            readOnly = false,
            deleted = false,
            modified = currentDate,
            created = currentDate
        )
    }

    private suspend fun saveExistingItem(existingItemModel1: ItemModel.Existing) {

        val itemData = createItemData()


        // TODO: Fix itemKey is null because of reference is cleared
        // TODO: copy protectedItemData?
        val protectedItemData = existingItemModel.item.data.apply {
            //update(existingItemModel.itemKey, itemData)
        }

        val currentDate = Date()

        val item = existingItemModel.item.copy(
            data = protectedItemData,
            modified = currentDate
        )

        userManager.updateItem(item)
    }

    private fun createItemData(): ItemData {
        return ItemData(title.value, password.value)
    }
}

sealed class ItemModel {
    class Creating(val loggedInUserViewModel: UserViewModel) : ItemModel()
    class Existing(val item: Item, val itemData: ItemData, val itemKey: ByteArray) : ItemModel()
}

private fun ItemModel.asExisting(): ItemModel.Existing? {
    return (this as? ItemModel.Existing)
}