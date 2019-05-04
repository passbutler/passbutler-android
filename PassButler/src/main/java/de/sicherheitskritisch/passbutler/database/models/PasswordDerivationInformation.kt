package de.sicherheitskritisch.passbutler.database.models

import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.putInt
import de.sicherheitskritisch.passbutler.crypto.getByteArray
import de.sicherheitskritisch.passbutler.crypto.putByteArray
import org.json.JSONException
import org.json.JSONObject

// TODO: Add unit tests for serialization/deserialization
data class PasswordDerivationInformation(val salt: ByteArray, val iterationCount: Int) : JSONSerializable {

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putByteArray(SERIALIZATION_KEY_SALT, salt)
            putInt(SERIALIZATION_KEY_ITERATION_COUNT, iterationCount)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PasswordDerivationInformation

        if (!salt.contentEquals(other.salt)) return false
        if (iterationCount != other.iterationCount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = salt.contentHashCode()
        result = 31 * result + iterationCount
        return result
    }

    companion object {
        private const val SERIALIZATION_KEY_SALT = "salt"
        private const val SERIALIZATION_KEY_ITERATION_COUNT = "iterationCount"

        fun deserialize(jsonObject: JSONObject): PasswordDerivationInformation? {
            return try {
                PasswordDerivationInformation(
                    jsonObject.getByteArray(SERIALIZATION_KEY_SALT),
                    jsonObject.getInt(SERIALIZATION_KEY_ITERATION_COUNT)
                )
            } catch (e: JSONException) {
                L.w("PasswordDerivationInformation", "The PasswordDerivationInformation could not be deserialized using the following JSON: $jsonObject", e)
                null
            }
        }
    }

}
