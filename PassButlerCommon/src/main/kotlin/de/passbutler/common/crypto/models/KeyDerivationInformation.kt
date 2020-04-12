package de.passbutler.common.crypto.models

import de.passbutler.common.base.JSONSerializable
import de.passbutler.common.base.JSONSerializableDeserializer
import de.passbutler.common.base.getByteArray
import de.passbutler.common.base.putByteArray
import de.passbutler.common.base.putInt
import de.passbutler.common.base.toHexString
import org.json.JSONException
import org.json.JSONObject

/**
 * Wraps the meta information of a key derivation from a password in a `JSONSerializable`.
 */
data class KeyDerivationInformation(val salt: ByteArray, val iterationCount: Int) : JSONSerializable {

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putByteArray(SERIALIZATION_KEY_SALT, salt)
            putInt(SERIALIZATION_KEY_ITERATION_COUNT, iterationCount)
        }
    }

    /**
     * The methods `equals()` and `hashCode()` are implemented
     * to be sure the `ByteArray` field is compared by content and not by reference.
     */

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyDerivationInformation

        if (!salt.contentEquals(other.salt)) return false
        if (iterationCount != other.iterationCount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = salt.contentHashCode()
        result = 31 * result + iterationCount
        return result
    }

    override fun toString(): String {
        return "KeyDerivationInformation(salt=${salt.toHexString()}, iterationCount=$iterationCount)"
    }

    object Deserializer : JSONSerializableDeserializer<KeyDerivationInformation>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): KeyDerivationInformation {
            return KeyDerivationInformation(
                jsonObject.getByteArray(SERIALIZATION_KEY_SALT),
                jsonObject.getInt(SERIALIZATION_KEY_ITERATION_COUNT)
            )
        }
    }

    companion object {
        private const val SERIALIZATION_KEY_SALT = "salt"
        private const val SERIALIZATION_KEY_ITERATION_COUNT = "iterationCount"
    }
}
