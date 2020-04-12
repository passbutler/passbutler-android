package de.passbutler.common.crypto.models

import de.passbutler.common.base.JSONSerializable
import de.passbutler.common.base.JSONSerializableDeserializer
import de.passbutler.common.base.getByteArray
import de.passbutler.common.base.putByteArray
import org.json.JSONException
import org.json.JSONObject

interface BaseEncryptedValue {
    val initializationVector: ByteArray
    val encryptedValue: ByteArray
}

/**
 * Wraps a encrypted value with its initialization vector in a `JSONSerializable`.
 */
data class EncryptedValue(
    override val initializationVector: ByteArray,
    override val encryptedValue: ByteArray
) : BaseEncryptedValue, JSONSerializable {

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putByteArray(SERIALIZATION_KEY_INITIALIZATION_VECTOR, initializationVector)
            putByteArray(SERIALIZATION_KEY_ENCRYPTED_VALUE, encryptedValue)
        }
    }

    /**
     * The methods `equals()` and `hashCode()` are implemented
     * to be sure the `ByteArray` field is compared by content and not by reference.
     */

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedValue

        if (!initializationVector.contentEquals(other.initializationVector)) return false
        if (!encryptedValue.contentEquals(other.encryptedValue)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = initializationVector.contentHashCode()
        result = 31 * result + encryptedValue.contentHashCode()
        return result
    }

    object Deserializer : JSONSerializableDeserializer<EncryptedValue>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): EncryptedValue {
            return EncryptedValue(
                jsonObject.getByteArray(SERIALIZATION_KEY_INITIALIZATION_VECTOR),
                jsonObject.getByteArray(SERIALIZATION_KEY_ENCRYPTED_VALUE)
            )
        }
    }

    companion object {
        const val SERIALIZATION_KEY_INITIALIZATION_VECTOR = "initializationVector"
        const val SERIALIZATION_KEY_ENCRYPTED_VALUE = "encryptedValue"
    }
}
