package de.sicherheitskritisch.passbutler.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.PrimaryKey
import de.passbutler.common.base.JSONSerializable
import de.passbutler.common.base.JSONSerializableDeserializer
import de.passbutler.common.base.getDate
import de.passbutler.common.base.putBoolean
import de.passbutler.common.base.putDate
import de.passbutler.common.base.putString
import de.passbutler.common.crypto.models.CryptographicKey
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.crypto.models.getProtectedValue
import de.passbutler.common.crypto.models.putProtectedValue
import de.passbutler.common.database.Synchronizable
import org.json.JSONException
import org.json.JSONObject
import java.util.*

// TODO: Fix item available foreign key problem when syncing
@Entity(
    tableName = "item_authorizations",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = arrayOf("username"),
            childColumns = arrayOf("userId"),
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Item::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("itemId"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ItemAuthorization(
    @PrimaryKey
    val id: String,
    @ColumnInfo(index = true)
    val userId: String,
    @ColumnInfo(index = true)
    val itemId: String,
    val itemKey: ProtectedValue<CryptographicKey>,
    val readOnly: Boolean,
    override val deleted: Boolean,
    override val modified: Date,
    override val created: Date
) : Synchronizable, JSONSerializable {

    @Ignore
    override val primaryField = id

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putString(SERIALIZATION_KEY_ID, id)
            putString(SERIALIZATION_KEY_USER_ID, userId)
            putString(SERIALIZATION_KEY_ITEM_ID, itemId)
            putProtectedValue(SERIALIZATION_KEY_ITEM_KEY, itemKey)
            putBoolean(SERIALIZATION_KEY_READONLY, readOnly)
            putBoolean(SERIALIZATION_KEY_DELETED, deleted)
            putDate(SERIALIZATION_KEY_MODIFIED, modified)
            putDate(SERIALIZATION_KEY_CREATED, created)
        }
    }

    object Deserializer : JSONSerializableDeserializer<ItemAuthorization>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): ItemAuthorization {
            return ItemAuthorization(
                id = jsonObject.getString(SERIALIZATION_KEY_ID),
                userId = jsonObject.getString(SERIALIZATION_KEY_USER_ID),
                itemId = jsonObject.getString(SERIALIZATION_KEY_ITEM_ID),
                itemKey = jsonObject.getProtectedValue(SERIALIZATION_KEY_ITEM_KEY),
                readOnly = jsonObject.getBoolean(SERIALIZATION_KEY_READONLY),
                deleted = jsonObject.getBoolean(SERIALIZATION_KEY_DELETED),
                modified = jsonObject.getDate(SERIALIZATION_KEY_MODIFIED),
                created = jsonObject.getDate(SERIALIZATION_KEY_CREATED)
            )
        }
    }

    companion object {
        private const val SERIALIZATION_KEY_ID = "id"
        private const val SERIALIZATION_KEY_USER_ID = "userId"
        private const val SERIALIZATION_KEY_ITEM_ID = "itemId"
        private const val SERIALIZATION_KEY_ITEM_KEY = "itemKey"
        private const val SERIALIZATION_KEY_READONLY = "readOnly"
        private const val SERIALIZATION_KEY_DELETED = "deleted"
        private const val SERIALIZATION_KEY_MODIFIED = "modified"
        private const val SERIALIZATION_KEY_CREATED = "created"
    }
}
