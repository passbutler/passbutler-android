package de.sicherheitskritisch.passbutler.crypto

import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.JSONSerializableDeserializer
import de.sicherheitskritisch.passbutler.base.L
import de.sicherheitskritisch.passbutler.base.getByteArray
import de.sicherheitskritisch.passbutler.base.putByteArray
import de.sicherheitskritisch.passbutler.base.putJSONSerializable
import de.sicherheitskritisch.passbutler.base.putString
import de.sicherheitskritisch.passbutler.base.toHexString
import de.sicherheitskritisch.passbutler.base.toUTF8String
import org.json.JSONException
import org.json.JSONObject

/**
 * Wraps a `JSONSerializable` object to store it encrypted.
 */
class ProtectedValue<T : JSONSerializable>(
    initializationVector: ByteArray,
    encryptionAlgorithm: EncryptionAlgorithm.Symmetric,
    encryptedValue: ByteArray
) : JSONSerializable {

    var initializationVector = initializationVector
        private set

    val encryptionAlgorithm = encryptionAlgorithm

    var encryptedValue = encryptedValue
        private set

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putByteArray(SERIALIZATION_KEY_INITIALIZATION_VECTOR, initializationVector)
            putSymmetricEncryptionAlgorithm(SERIALIZATION_KEY_ENCRYPTION_ALGORITHM, encryptionAlgorithm)
            putByteArray(SERIALIZATION_KEY_ENCRYPTED_VALUE, encryptedValue)
        }
    }

    fun decrypt(encryptionKey: ByteArray, deserializer: JSONSerializableDeserializer<T>): T? {
        return try {
            encryptionAlgorithm.decrypt(initializationVector, encryptionKey, encryptedValue).let { decryptedBytes ->
                val jsonSerializedString = decryptedBytes.toUTF8String()
                deserializer.deserialize(jsonSerializedString)
            }
        } catch (e: JSONException) {
            L.w("ProtectedValue", "decrypt(): The value could not be deserialized!", e)
            null
        } catch (e: EncryptionAlgorithm.DecryptionFailedException) {
            L.w("ProtectedValue", "decrypt(): The value could not be decrypted!", e)
            null
        }
    }

    fun update(encryptionKey: ByteArray, updatedValue: T) {
        val newInitializationVector = encryptionAlgorithm.generateInitializationVector()

        try {
            val encryptedValue = encryptionAlgorithm.encrypt(newInitializationVector, encryptionKey, updatedValue.toByteArray())

            // Update values only if encryption was successful
            this.initializationVector = newInitializationVector
            this.encryptedValue = encryptedValue
        } catch (e: EncryptionAlgorithm.EncryptionFailedException) {
            L.w("ProtectedValue", "update(): The value could not be updated because encryption failed!", e)
        }
    }

    /**
     * The methods `equals()` and `hashCode()` are implemented
     * to be sure the `ByteArray` field is compared by content and not by reference.
     */

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProtectedValue<*>

        if (!initializationVector.contentEquals(other.initializationVector)) return false
        if (encryptionAlgorithm != other.encryptionAlgorithm) return false
        if (!encryptedValue.contentEquals(other.encryptedValue)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = initializationVector.contentHashCode()
        result = 31 * result + encryptionAlgorithm.hashCode()
        result = 31 * result + encryptedValue.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "ProtectedValue(initializationVector=${initializationVector.toHexString()}, encryptionAlgorithm=$encryptionAlgorithm, encryptedValue=${encryptedValue.toHexString()})"
    }

    class Deserializer<T : JSONSerializable> : JSONSerializableDeserializer<ProtectedValue<T>>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): ProtectedValue<T> {
            return ProtectedValue(
                jsonObject.getByteArray(SERIALIZATION_KEY_INITIALIZATION_VECTOR),
                jsonObject.getSymmetricEncryptionAlgorithm(SERIALIZATION_KEY_ENCRYPTION_ALGORITHM),
                jsonObject.getByteArray(SERIALIZATION_KEY_ENCRYPTED_VALUE)
            )
        }
    }

    companion object {
        const val SERIALIZATION_KEY_INITIALIZATION_VECTOR = "initializationVector"
        const val SERIALIZATION_KEY_ENCRYPTION_ALGORITHM = "encryptionAlgorithm"
        const val SERIALIZATION_KEY_ENCRYPTED_VALUE = "encryptedValue"

        fun <T : JSONSerializable> create(encryptionAlgorithm: EncryptionAlgorithm.Symmetric, encryptionKey: ByteArray, initialValue: T): ProtectedValue<T>? {
            val newInitializationVector = encryptionAlgorithm.generateInitializationVector()

            return try {
                val encryptedValue = encryptionAlgorithm.encrypt(newInitializationVector, encryptionKey, initialValue.toByteArray())
                ProtectedValue(newInitializationVector, encryptionAlgorithm, encryptedValue)
            } catch (e: EncryptionAlgorithm.EncryptionFailedException) {
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
    return valueAsJsonSerializedString.toByteArray(Charsets.UTF_8)
}

/**
 * Extensions to serialize/deserialize a `ProtectedValue`.
 */

@Throws(JSONException::class)
fun <T : JSONSerializable> JSONObject.getProtectedValue(name: String): ProtectedValue<T> {
    val serialized = getJSONObject(name)
    return ProtectedValue.Deserializer<T>().deserialize(serialized)
}

fun <T : JSONSerializable> JSONObject.getProtectedValueOrNull(name: String): ProtectedValue<T>? {
    return try {
        val serialized = getJSONObject(name)
        ProtectedValue.Deserializer<T>().deserializeOrNull(serialized)
    } catch (e: JSONException) {
        L.d("JSON", "getProtectedValueOrNull(): The optional ProtectedValue with key '$name' could not be deserialized using the following JSON: $this (${e.message})")
        null
    }
}

fun <T : JSONSerializable> JSONObject.putProtectedValue(name: String, value: T?): JSONObject {
    return putJSONSerializable(name, value)
}

/**
 * Convenience method to put a `EncryptionAlgorithm.Symmetric` value to `JSONObject`.
 */
@Throws(JSONException::class)
fun JSONObject.putSymmetricEncryptionAlgorithm(name: String, value: EncryptionAlgorithm.Symmetric): JSONObject {
    val algorithmStringRepresentation = value.stringRepresentation
    return putString(name, algorithmStringRepresentation)
}

/**
 * Convenience method to get a `EncryptionAlgorithm.Symmetric` value from `JSONObject`.
 */
@Throws(JSONException::class)
fun JSONObject.getSymmetricEncryptionAlgorithm(name: String): EncryptionAlgorithm.Symmetric {
    return when (val algorithmStringRepresentation = getString(name)) {
        EncryptionAlgorithm.Symmetric.AES256GCM.stringRepresentation -> EncryptionAlgorithm.Symmetric.AES256GCM
        else -> throw JSONException("The EncryptionAlgorithm.Symmetric string representation '$algorithmStringRepresentation' could not be found!")
    }
}
