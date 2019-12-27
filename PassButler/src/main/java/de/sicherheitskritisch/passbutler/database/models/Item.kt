package de.sicherheitskritisch.passbutler.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.PrimaryKey
import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.JSONSerializableDeserializer
import de.sicherheitskritisch.passbutler.base.putString
import de.sicherheitskritisch.passbutler.crypto.models.ProtectedValue
import de.sicherheitskritisch.passbutler.database.Synchronizable
import org.json.JSONException
import org.json.JSONObject
import java.util.*

// TODO: Add tests

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = arrayOf("username"),
            childColumns = arrayOf("userId"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Item(
    @PrimaryKey
    val id: String,
    @ColumnInfo(index = true)
    val userId: String,
    var data: ProtectedValue<ItemData>,
    override var deleted: Boolean,
    override var modified: Date,
    override val created: Date
) : Synchronizable, JSONSerializable {

    @Ignore
    override val primaryField = id

    override fun serialize(): JSONObject {
        // TODO: Implement
        return JSONObject()
    }
}

// TODO: Add tests

data class ItemData(
    val title: String,
    val password: String
) : JSONSerializable {

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putString(SERIALIZATION_KEY_TITLE, title)
            putString(SERIALIZATION_KEY_PASSWORD, password)
        }
    }

    object Deserializer : JSONSerializableDeserializer<ItemData>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): ItemData {
            return ItemData(
                title = jsonObject.getString(SERIALIZATION_KEY_TITLE),
                password = jsonObject.getString(SERIALIZATION_KEY_PASSWORD)
            )
        }
    }

    companion object {
        private const val SERIALIZATION_KEY_TITLE = "title"
        private const val SERIALIZATION_KEY_PASSWORD = "password"
    }
}
