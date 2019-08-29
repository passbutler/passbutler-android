package de.sicherheitskritisch.passbutler.crypto.models

import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.JSONSerializableDeserializer
import de.sicherheitskritisch.passbutler.base.getByteArray
import de.sicherheitskritisch.passbutler.base.putByteArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Wraps a binary cryptographic key in a `JSONSerializable`.
 */
data class CryptographicKey(val key: ByteArray) : JSONSerializable {

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putByteArray(SERIALIZATION_KEY_KEY, key)
        }
    }

    /**
     * The methods `equals()` and `hashCode()` are implemented
     * to be sure the `ByteArray` field is compared by content and not by reference.
     */

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

    object Deserializer : JSONSerializableDeserializer<CryptographicKey>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): CryptographicKey {
            return CryptographicKey(
                jsonObject.getByteArray(SERIALIZATION_KEY_KEY)
            )
        }
    }

    companion object {
        private const val SERIALIZATION_KEY_KEY = "key"
    }
}
