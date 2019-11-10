package de.sicherheitskritisch.passbutler

import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.Failure
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
                            updateSensibleDataFields(decryptedItemKey, itemDataDecryptionResult.result)
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
        val itemKey = itemKey ?: throw IllegalStateException("The item key is null despite the the ItemEditingViewModel is created!")
        val itemModel = ItemModel.Existing(item, itemKey)
        return ItemEditingViewModel(itemModel, userManager, itemData)
    }

    /**
     * The methods `equals()` and `hashCode()` only check the `ItemModel` because this makes an item unique.
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
    private val userManager: UserManager,
    itemData: ItemData?
) : ManualCancelledCoroutineScopeViewModel(), EditingViewModel {

    val id = (itemModel as? ItemModel.Existing)?.item?.id ?: "-"

    val title = NonNullMutableLiveData(itemData?.title ?: "Initial Title")
    val password = NonNullMutableLiveData(itemData?.password ?: "Initial password")

    val saveRequestSendingViewModel = DefaultRequestSendingViewModel()

    private var saveCoroutineJob: Job? = null

    fun save() {
        saveCoroutineJob?.cancel()
        saveCoroutineJob = createRequestSendingJob(saveRequestSendingViewModel) {
            val itemModel = itemModel

            val itemData = ItemData(title.value, password.value)
            val currentDate = Date()

            when (itemModel) {
                is ItemModel.Creating -> {
                    val userId = itemModel.userViewModel.id

                    val symmetricEncryptionAlgorithm = EncryptionAlgorithm.Symmetric.AES256GCM
                    val itemKey = symmetricEncryptionAlgorithm.generateEncryptionKey()

                    val protectedItemData = ProtectedValue.create(symmetricEncryptionAlgorithm, itemKey, itemData)

                    val item = Item(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        data = protectedItemData,
                        deleted = false,
                        modified = currentDate,
                        created = currentDate
                    )

                    userManager.createItem(item)

                    val asymmetricEncryptionAlgorithm = EncryptionAlgorithm.Asymmetric.RSA2048OAEP

                    val userItemEncryptionPublicKey = itemModel.userViewModel.itemEncryptionPublicKey.key
                    val protectedItemKey = ProtectedValue.create(asymmetricEncryptionAlgorithm, userItemEncryptionPublicKey, CryptographicKey(itemKey))

                    val itemAuthorization = ItemAuthorization(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        itemId = item.id,
                        itemKey = protectedItemKey,
                        readOnly = false,
                        deleted = false,
                        modified = currentDate,
                        created = currentDate
                    )

                    userManager.createItemAuthorization(itemAuthorization)

                    this.itemModel = ItemModel.Existing(item, itemKey)
                }
                is ItemModel.Existing -> {

                    // TODO: Fix itemKey is null because of reference is cleared

                    // TODO: copy protectedItemData?
                    val protectedItemData = itemModel.item.data.apply {
                        update(itemModel.itemKey, itemData)
                    }

                    val item = itemModel.item.copy(
                        data = protectedItemData,
                        modified = currentDate
                    )

                    userManager.updateItem(item)

                    this.itemModel = ItemModel.Existing(item, itemModel.itemKey)
                }
            }
        }
    }
}

sealed class ItemModel {
    class Creating(val userViewModel: UserViewModel) : ItemModel()
    class Existing(val item: Item, val itemKey: ByteArray) : ItemModel()
}