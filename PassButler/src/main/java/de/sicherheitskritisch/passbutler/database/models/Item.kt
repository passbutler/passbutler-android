package de.sicherheitskritisch.passbutler.database.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.crypto.models.ProtectedValue
import de.sicherheitskritisch.passbutler.database.Synchronizable
import org.json.JSONObject
import java.util.*

// TODO: Add tests

@Entity(tableName = "items")
data class Item(
    @PrimaryKey
    val uuid: String,
    var data: ProtectedValue<ItemData>,
    override var deleted: Boolean,
    override var modified: Date,
    override val created: Date
) : Synchronizable, JSONSerializable {

    @Ignore
    override val primaryField = uuid

    override fun serialize(): JSONObject {
        // TODO: Implement
        return JSONObject()
    }
}

data class ItemData(
    val password: String
) : JSONSerializable {
    override fun serialize(): JSONObject {
        // TODO: Implement
        return JSONObject()
    }
}
