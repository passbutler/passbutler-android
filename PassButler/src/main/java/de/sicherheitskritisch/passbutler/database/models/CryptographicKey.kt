package de.sicherheitskritisch.passbutler.database.models

import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.crypto.getByteArray
import de.sicherheitskritisch.passbutler.crypto.putByteArray
import org.json.JSONException
import org.json.JSONObject

// TODO: Add unit tests for serialization/deserialization + docu
data class CryptographicKey(val key: ByteArray) : JSONSerializable {

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putByteArray(SERIALIZATION_KEY_KEY, key)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CryptographicKey

        if (!key.contentEquals(other.key)) return false

        return true
    }

    override fun hashCode(): Int {
        return key.contentHashCode()
    }

    companion object {
        private const val SERIALIZATION_KEY_KEY = "key"

        fun deserialize(jsonObject: JSONObject): CryptographicKey? {
            return try {
                CryptographicKey(
                    jsonObject.getByteArray(SERIALIZATION_KEY_KEY)
                )
            } catch (e: JSONException) {
                L.w("CryptographicKey", "The CryptographicKey could not be deserialized using the following JSON: $jsonObject", e)
                null
            }
        }
    }
}
