package de.sicherheitskritisch.passbutler.crypto

import android.arch.persistence.room.TypeConverter
import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.putJSONObject
import de.sicherheitskritisch.passbutler.base.putString
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import org.json.JSONException
import org.json.JSONObject

// TODO: Add unit tests for this
// TODO: Better store `encryptedValue` Base64 encoded?
/**
 * Wraps a `JSONSerializable` object to store it encrypted.
 */
class ProtectedValue<T : JSONSerializable> private constructor(
    private var initializationVector: ByteArray,
    private val algorithm: Algorithm,
    private var encryptedValue: ByteArray
) : JSONSerializable {

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putString(SERIALIZATION_KEY_INITIALIZATION_VECTOR, initializationVector.toUTF8String())
            putString(SERIALIZATION_KEY_ALGORITHM, algorithm.stringRepresentation)
            putString(SERIALIZATION_KEY_ENCRYPTED_VALUE, encryptedValue.toUTF8String())
        }
    }

    fun decrypt(encryptionKey: ByteArray, instantiationDelegate: (JSONObject) -> T?): T? {
        return try {
            algorithm.decrypt(initializationVector, encryptionKey, encryptedValue).let { decryptedBytes ->
                val jsonSerializedString = decryptedBytes.toUTF8String()
                val jsonObject = JSONObject(jsonSerializedString)
                instantiationDelegate(jsonObject)
            }
        } catch (e: JSONException) {
            L.w("ProtectedValue", "decrypt(): The value could not be deserialized!", e)
            null
        } catch (e: Algorithm.DecryptionFailedException) {
            L.w("ProtectedValue", "decrypt(): The value could not be decrypted!", e)
            null
        }
    }

    fun update(encryptionKey: ByteArray, updatedValue: T) {
        val newInitializationVector = algorithm.generateInitializationVector()

        try {
            val encryptedValue = algorithm.encrypt(newInitializationVector, encryptionKey, updatedValue.toByteArray())

            // Update values only if encryption was successful
            this.initializationVector = newInitializationVector
            this.encryptedValue = encryptedValue
        } catch (e: Algorithm.EncryptionFailedException) {
            L.w("ProtectedValue", "update(): The value could not be updated because encryption failed!", e)
        }
    }

    companion object {
        const val SERIALIZATION_KEY_INITIALIZATION_VECTOR = "initializationVector"
        const val SERIALIZATION_KEY_ALGORITHM = "algorithm"
        const val SERIALIZATION_KEY_ENCRYPTED_VALUE = "encryptedValue"

        fun <T : JSONSerializable> deserialize(jsonObject: JSONObject): ProtectedValue<T>? {
            return try {
                ProtectedValue(
                    jsonObject.getString(SERIALIZATION_KEY_INITIALIZATION_VECTOR).toByteArray(),
                    jsonObject.getString(SERIALIZATION_KEY_ALGORITHM).toAlgorithm() ?: throw JSONException("The algorithm could not be deserialized!"),
                    jsonObject.getString(SERIALIZATION_KEY_ENCRYPTED_VALUE).toByteArray()
                )
            } catch (e: JSONException) {
                L.w("ProtectedValue", "deserialize(): The ProtectedValue object could not be deserialized using the following JSON: $jsonObject", e)
                null
            }
        }

        fun <T : JSONSerializable> create(encryptionKey: ByteArray, initialValue: T): ProtectedValue<T>? {
            val symmetricAlgorithm: Algorithm = Algorithm.AES256GCM
            val newInitializationVector = symmetricAlgorithm.generateInitializationVector()

            return try {
                val encryptedValue = symmetricAlgorithm.encrypt(newInitializationVector, encryptionKey, initialValue.toByteArray())
                ProtectedValue(newInitializationVector, symmetricAlgorithm, encryptedValue)
            } catch (e: Algorithm.EncryptionFailedException) {
                L.w("ProtectedValue", "create(): The value could not be created because encryption failed!", e)
                null
            }
        }
    }
}

/**
 * Converts a `JSONSerializable` to a `ByteArray`.
 */
private fun <T : JSONSerializable> T.toByteArray(): ByteArray {
    val valueAsJsonSerializedString = this.serialize().toString()
    return valueAsJsonSerializedString.toByteArray()
}

/**
 * Converts the `ByteArray` to `String` with UTF-8 charset (basically what the `String()` constructor does but in explicit way).
 */
private fun ByteArray.toUTF8String(): String {
    return toString(Charsets.UTF_8)
}

/**
 * Type converter for `ProtectedValue` used for `RoomDatabase`.
 */
class ProtectedValueConverters {
    @TypeConverter
    fun protectedValueToString(protectedValue: ProtectedValue<*>?): String? {
        return protectedValue?.serialize()?.toString()
    }

    @TypeConverter
    fun stringToProtectedValueWithUserSettings(serializedProtectedValue: String?): ProtectedValue<UserSettings>? {
        return serializedProtectedValue?.let {
            ProtectedValue.deserialize(JSONObject(it))
        }
    }
}

/**
 * Convenience method to put a `ProtectedValue` value to `JSONObject`.
 */
@Throws(JSONException::class)
fun JSONObject.putProtectedValue(name: String, value: ProtectedValue<*>): JSONObject {
    return putJSONObject(name, value.serialize())
}

/**
 * Convenience method to get a `ProtectedValue` value from `JSONObject`-
 */
fun <T : JSONSerializable> JSONObject.getProtectedValue(name: String): ProtectedValue<T>? {
    return ProtectedValue.deserialize(getJSONObject(name))
}