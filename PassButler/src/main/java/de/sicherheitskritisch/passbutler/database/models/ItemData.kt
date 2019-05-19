package de.sicherheitskritisch.passbutler.database.models

import de.sicherheitskritisch.passbutler.base.JSONSerializable
import org.json.JSONObject

data class ItemData(
    val password: String
) : JSONSerializable {
    override fun serialize(): JSONObject {
        // TODO: Implement
        return JSONObject()
    }
}
