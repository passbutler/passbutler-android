package de.sicherheitskritisch.passbutler

import androidx.lifecycle.MutableLiveData
import de.sicherheitskritisch.passbutler.base.clear
import de.sicherheitskritisch.passbutler.base.formattedDateTime
import de.sicherheitskritisch.passbutler.base.viewmodels.ModelBasedViewModel
import de.sicherheitskritisch.passbutler.base.viewmodels.SensibleDataViewModel
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.ProtectedValue
import de.sicherheitskritisch.passbutler.database.models.Item
import de.sicherheitskritisch.passbutler.database.models.ItemAuthorization
import de.sicherheitskritisch.passbutler.database.models.ItemData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class ItemViewModel(
    private val item: Item,
    private val itemAuthorization: ItemAuthorization
) : ModelBasedViewModel<Item>, SensibleDataViewModel<ByteArray> {

    private var itemKey: ByteArray? = null
    private var data: ItemData? = null

    val id
        get() = item.id

    val title
        get() = item.id

    val subtitle
        get() = item.modified.formattedDateTime

    val password = MutableLiveData(data?.password)

    val sensibleDataLocked
        get() = itemKey == null || data == null

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    @Throws(ProtectedValue.DecryptFailedException::class)
    override suspend fun decryptSensibleData(userItemEncryptionSecretKey: ByteArray) {
        withContext(Dispatchers.Default) {
            val decryptedItemKey = itemAuthorization.itemKey.decrypt(userItemEncryptionSecretKey, CryptographicKey.Deserializer).key
            val decryptedData = item.data.decrypt(decryptedItemKey, ItemData.Deserializer)

            itemKey = decryptedItemKey
            data = decryptedData
        }
    }

    override suspend fun clearSensibleData() {
        itemKey?.clear()
        itemKey = null

        data = null
    }

    // TODO: EditingVM

    override fun createModel(): Item {
        return item.copy(
            data = data,
            modified = Date()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ItemViewModel

        if (item != other.item) return false

        return true
    }

    override fun hashCode(): Int {
        return item.hashCode()
    }
}