package de.sicherheitskritisch.passbutler.crypto.models

import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.putInt
import de.sicherheitskritisch.passbutler.base.putJSONObject
import de.sicherheitskritisch.passbutler.base.toHexString
import de.sicherheitskritisch.passbutler.crypto.getByteArray
import de.sicherheitskritisch.passbutler.crypto.putByteArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Wraps the meta information of a key derivation from a password.
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

    companion object {
        private const val SERIALIZATION_KEY_SALT = "salt"
        private const val SERIALIZATION_KEY_ITERATION_COUNT = "iterationCount"

        fun deserialize(jsonObject: JSONObject): KeyDerivationInformation? {
            return try {
                KeyDerivationInformation(
                    jsonObject.getByteArray(SERIALIZATION_KEY_SALT),
                    jsonObject.getInt(SERIALIZATION_KEY_ITERATION_COUNT)
                )
            } catch (e: JSONException) {
                L.w("KeyDerivationInformation", "The KeyDerivationInformation could not be deserialized using the following JSON: $jsonObject", e)
                null
            }
        }
    }
}

/**
 * Convenience method to put a `KeyDerivationInformation` value to `JSONObject`.
 */
@Throws(JSONException::class)
fun JSONObject.putKeyDerivationInformation(name: String, value: KeyDerivationInformation?): JSONObject {
    val serializedKeyDerivationInformation = value?.serialize()
    return putJSONObject(name, serializedKeyDerivationInformation)
}

/**
 * Convenience method to get a `KeyDerivationInformation` value from `JSONObject`.
 */
@Throws(JSONException::class)
fun JSONObject.getKeyDerivationInformation(name: String): KeyDerivationInformation? {
    val serializedKeyDerivationInformation = getJSONObject(name)
    return KeyDerivationInformation.deserialize(serializedKeyDerivationInformation)
}