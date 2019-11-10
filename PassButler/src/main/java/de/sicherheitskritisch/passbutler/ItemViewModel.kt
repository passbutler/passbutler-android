package de.sicherheitskritisch.passbutler

import de.sicherheitskritisch.passbutler.base.DefaultRequestSendingViewModel
import de.sicherheitskritisch.passbutler.base.Failure
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.NonNullMutableLiveData
import de.sicherheitskritisch.passbutler.base.Result
import de.sicherheitskritisch.passbutler.base.Success
import de.sicherheitskritisch.passbutler.base.ValueGetterLiveData
import de.sicherheitskritisch.passbutler.base.createRequestSendingJob
import de.sicherheitskritisch.passbutler.base.formattedDateTime
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
import kotlinx.coroutines.withContext
import java.util.*

class ItemViewModel(
    private val itemModel: ItemModel.Existing,
    private val userManager: UserManager
) : EditableViewModel<ItemEditingViewModel> {

    val id: String?
        get() = itemModel.item.id

    val title = ValueGetterLiveData {
        itemData?.title
    }

    val subtitle = ValueGetterLiveData {
        itemModel.item.modified.formattedDateTime
    }

    var itemData: ItemData? = null
        private set

    suspend fun decryptSensibleData(userItemEncryptionSecretKey: ByteArray): Result<Unit> {
        return withContext(Dispatchers.Default) {
            val itemKeyDecryptionResult = itemModel.itemAuthorization.itemKey.decryptWithResult(userItemEncryptionSecretKey, CryptographicKey.Deserializer)

            when (itemKeyDecryptionResult) {
                is Success -> {
                    val decryptedItemKey = itemKeyDecryptionResult.result.key
                    val itemDataDecryptionResult = itemModel.item.data.decryptWithResult(decryptedItemKey, ItemData.Deserializer)

                    when (itemDataDecryptionResult) {
                        is Success -> {
                            itemData = itemDataDecryptionResult.result

                            title.notifyChange()
                            subtitle.notifyChange()

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

    fun clearSensibleData() {
        itemData = null
    }

    override fun createEditingViewModel(): ItemEditingViewModel {
        return ItemEditingViewModel(itemModel, userManager, itemData)
    }

    /**
     * The methods `equals()` and `hashCode()` only check the `ItemModel` because this makes an item unique.
     * The `itemData` is just a state of the item that should not cause the item list to change.
     */

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ItemViewModel

        if (itemModel != other.itemModel) return false

        return true
    }

    override fun hashCode(): Int {
        return itemModel.hashCode()
    }
}

class ItemEditingViewModel(
    private var itemModel: ItemModel,
    private val userManager: UserManager,
    itemData: ItemData?
) : ManualCancelledCoroutineScopeViewModel(), EditingViewModel {

    val title = NonNullMutableLiveData(itemData?.title ?: "Initial Title")
    val password = NonNullMutableLiveData(itemData?.password ?: "Initial password")

    val saveRequestSendingViewModel = DefaultRequestSendingViewModel()

    // TODO: Save job?

    fun save() {
        createRequestSendingJob(saveRequestSendingViewModel) {
            val currentDate = Date()
            val itemModel = itemModel

            when (itemModel) {
                is ItemModel.New -> {
                    val userId = itemModel.userId

                    val symmetricEncryptionAlgorithm = EncryptionAlgorithm.Symmetric.AES256GCM
                    val itemKey = symmetricEncryptionAlgorithm.generateEncryptionKey()

                    val itemData = ItemData(title.value, password.value)
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

                    val userItemEncryptionPublicKey = itemModel.userItemEncryptionPublicKey
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

                    this.itemModel = ItemModel.Existing(item, itemAuthorization)
                }
                is ItemModel.Existing -> {
//                    val itemData = ItemData(title, password)
//                    protectedItemData.update(itemKey, itemData)
                    L.d("ItemViewModel", "Handling of existing item not supported yet")
                }
            }
        }
    }
}

sealed class ItemModel {
    class New(val userId: String, val userItemEncryptionPublicKey: ByteArray) : ItemModel()
    class Existing(val item: Item, val itemAuthorization: ItemAuthorization) : ItemModel()
}