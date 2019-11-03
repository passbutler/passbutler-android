package de.sicherheitskritisch.passbutler.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.PrimaryKey
import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.ProtectedValue
import de.sicherheitskritisch.passbutler.database.Synchronizable
import org.json.JSONObject
import java.util.*

// TODO: Add tests

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
    var readOnly: Boolean,
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
