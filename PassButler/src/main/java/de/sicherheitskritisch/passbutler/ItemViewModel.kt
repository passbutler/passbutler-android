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

class ItemViewModel private constructor(
    private val itemModel: ItemModel,
    private val userManager: UserManager
) : EditableViewModel<ItemEditingViewModel> {

    val id: String?
        get() = when (itemModel) {
            is ItemModel.New -> {
                null // TODO: Is `null` okay or better new UUID?
            }
            is ItemModel.Existing -> itemModel.item.id
        }

    val title = ValueGetterLiveData {
        itemData?.title
    }

    val subtitle = ValueGetterLiveData {
        when (itemModel) {
            is ItemModel.New -> null
            is ItemModel.Existing -> itemModel.item.modified.formattedDateTime
        }
    }

    val sensibleDataLocked
        get() = itemData == null

    private var itemData: ItemData? = null

    constructor(userManager: UserManager, itemModel: ItemModel.New) : this(itemModel, userManager)
    constructor(userManager: UserManager, itemModel: ItemModel.Existing) : this(itemModel, userManager)

    suspend fun decryptSensibleData(userItemEncryptionSecretKey: ByteArray): Result<Unit> {
        return withContext(Dispatchers.Default) {

            if (itemModel is ItemModel.Existing) {
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
            } else {
                // TODO: Set itemkey + itemdata? should not be necessary because this VM is re-created automatically if model was saved
                // A new created viewmodel without model does not need to be decrypted
                Success(Unit)
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
     * The methods `equals()` and `hashCode()` are only check the `Item` and `ItemAuthorization` because this makes an item unique.
     * The item key can be changed without causing the list to change.
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

//    private var protectedItemData = item?.data?.copy()

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