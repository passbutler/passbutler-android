package de.passbutler.common.crypto.models

import de.passbutler.common.base.Failure
import de.passbutler.common.base.JSONSerializable
import de.passbutler.common.base.JSONSerializableDeserializer
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.getByteArray
import de.passbutler.common.base.putByteArray
import de.passbutler.common.base.putJSONSerializable
import de.passbutler.common.base.putString
import de.passbutler.common.base.resultOrThrowException
import de.passbutler.common.base.toHexString
import de.passbutler.common.base.toUTF8String
import de.passbutler.common.crypto.EncryptionAlgorithm
import de.passbutler.common.crypto.models.EncryptedValue.Companion.SERIALIZATION_KEY_ENCRYPTED_VALUE
import de.passbutler.common.crypto.models.EncryptedValue.Companion.SERIALIZATION_KEY_INITIALIZATION_VECTOR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.tinylog.kotlin.Logger

/**
 * Wraps a `JSONSerializable` object to store it encrypted as a `JSONSerializable`.
 */
class ProtectedValue<T : JSONSerializable> private constructor(
    initializationVector: ByteArray,
    encryptedValue: ByteArray,
    encryptionAlgorithm: EncryptionAlgorithm
) : BaseEncryptedValue, JSONSerializable {

    override var initializationVector = initializationVector
        private set

    override var encryptedValue = encryptedValue
        private set

    val encryptionAlgorithm = encryptionAlgorithm

    fun copy(): ProtectedValue<T> {
        return ProtectedValue(
            initializationVector.clone(),
            encryptedValue.clone(),
            encryptionAlgorithm
        )
    }

    @Throws(IllegalArgumentException::class)
    suspend fun decrypt(encryptionKey: ByteArray, deserializer: JSONSerializableDeserializer<T>): Result<T> {
        require(!encryptionKey.all { it.toInt() == 0 }) { "The given encryption key can't be used because it is cleared!" }

        return try {
            val decryptedBytes = when (encryptionAlgorithm) {
                is EncryptionAlgorithm.Symmetric -> {
                    encryptionAlgorithm.decrypt(initializationVector, encryptionKey, encryptedValue).resultOrThrowException()
                }
                is EncryptionAlgorithm.Asymmetric -> {
                    encryptionAlgorithm.decrypt(encryptionKey, encryptedValue).resultOrThrowException()
                }
            }

            val deserializedResultValue = withContext(Dispatchers.Default) {
                val jsonSerializedString = decryptedBytes.toUTF8String()
                deserializer.deserialize(jsonSerializedString)
            }

            Success(deserializedResultValue)
        } catch (exception: JSONException) {
            Failure(Exception("The value could not be deserialized!", exception))
        } catch (exception: Exception) {
            Failure(Exception("The value could not be decrypted!", exception))
        }
    }

    @Throws(IllegalArgumentException::class)
    suspend fun update(encryptionKey: ByteArray, updatedValue: T): Result<Unit> {
        require(!encryptionKey.all { it.toInt() == 0 }) { "The given encryption key can't be used because it is cleared!" }

        return try {
            when (encryptionAlgorithm) {
                is EncryptionAlgorithm.Symmetric -> {
                    val newInitializationVector = encryptionAlgorithm.generateInitializationVector().resultOrThrowException()
                    val newEncryptedValue = encryptionAlgorithm.encrypt(newInitializationVector, encryptionKey, updatedValue.toByteArray()).resultOrThrowException()

                    // Update values only if encryption was successful
                    initializationVector = newInitializationVector
                    encryptedValue = newEncryptedValue
                }
                is EncryptionAlgorithm.Asymmetric -> {
                    val newEncryptedValue = encryptionAlgorithm.encrypt(encryptionKey, updatedValue.toByteArray()).resultOrThrowException()

                    // Update values only if encryption was successful
                    initializationVector = ByteArray(0)
                    encryptedValue = newEncryptedValue
                }
            }

            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
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
        if (!encryptedValue.contentEquals(other.encryptedValue)) return false
        if (encryptionAlgorithm != other.encryptionAlgorithm) return false

        return true
    }

    override fun hashCode(): Int {
        var result = initializationVector.contentHashCode()
        result = 31 * result + encryptedValue.contentHashCode()
        result = 31 * result + encryptionAlgorithm.hashCode()
        return result
    }

    override fun toString(): String {
        return "ProtectedValue(initializationVector=${initializationVector.toHexString()}, encryptionAlgorithm=$encryptionAlgorithm, encryptedValue=${encryptedValue.toHexString()})"
    }

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putByteArray(SERIALIZATION_KEY_INITIALIZATION_VECTOR, initializationVector)
            putByteArray(SERIALIZATION_KEY_ENCRYPTED_VALUE, encryptedValue)
            putEncryptionAlgorithm(SERIALIZATION_KEY_ENCRYPTION_ALGORITHM, encryptionAlgorithm)
        }
    }

    class Deserializer<T : JSONSerializable> : JSONSerializableDeserializer<ProtectedValue<T>>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): ProtectedValue<T> {
            return ProtectedValue(
                jsonObject.getByteArray(SERIALIZATION_KEY_INITIALIZATION_VECTOR),
                jsonObject.getByteArray(SERIALIZATION_KEY_ENCRYPTED_VALUE),
                jsonObject.getEncryptionAlgorithm(SERIALIZATION_KEY_ENCRYPTION_ALGORITHM)
            )
        }
    }

    companion object {
        const val SERIALIZATION_KEY_ENCRYPTION_ALGORITHM = "encryptionAlgorithm"

        @Throws(IllegalArgumentException::class)
        suspend fun <T : JSONSerializable> create(encryptionAlgorithm: EncryptionAlgorithm, encryptionKey: ByteArray, initialValue: T): Result<ProtectedValue<T>> {
            require(!encryptionKey.all { it.toInt() == 0 }) { "The given encryption key can't be used because it is cleared!" }

            return try {
                val createdProtectedValue: ProtectedValue<T> = when (encryptionAlgorithm) {
                    is EncryptionAlgorithm.Symmetric -> {
                        val newInitializationVector = encryptionAlgorithm.generateInitializationVector().resultOrThrowException()
                        val encryptedValue = encryptionAlgorithm.encrypt(newInitializationVector, encryptionKey, initialValue.toByteArray()).resultOrThrowException()
                        ProtectedValue(newInitializationVector, encryptedValue, encryptionAlgorithm)
                    }
                    is EncryptionAlgorithm.Asymmetric -> {
                        val newInitializationVector = ByteArray(0)
                        val encryptedValue = encryptionAlgorithm.encrypt(encryptionKey, initialValue.toByteArray()).resultOrThrowException()
                        ProtectedValue(newInitializationVector, encryptedValue, encryptionAlgorithm)
                    }
                }

                Success(createdProtectedValue)
            } catch (exception: Exception) {
                Failure(exception)
            }
        }

        fun <T : JSONSerializable> createInstanceForTesting(initializationVector: ByteArray, encryptedValue: ByteArray, encryptionAlgorithm: EncryptionAlgorithm.Symmetric): ProtectedValue<T> {
            return ProtectedValue(initializationVector, encryptedValue, encryptionAlgorithm)
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
    } catch (exception: JSONException) {
        Logger.debug("The optional ProtectedValue with key '$name' could not be deserialized using the following JSON: $this (${exception.message})")
        null
    }
}

fun <T : JSONSerializable> JSONObject.putProtectedValue(name: String, value: T?): JSONObject {
    return putJSONSerializable(name, value)
}

/**
 * Convenience method to put a `EncryptionAlgorithm` value to `JSONObject`.
 */
@Throws(JSONException::class)
fun JSONObject.putEncryptionAlgorithm(name: String, value: EncryptionAlgorithm): JSONObject {
    val algorithmStringRepresentation = value.stringRepresentation
    return putString(name, algorithmStringRepresentation)
}

/**
 * Convenience method to get a `EncryptionAlgorithm` value from `JSONObject`.
 */
@Throws(JSONException::class)
fun JSONObject.getEncryptionAlgorithm(name: String): EncryptionAlgorithm {
    return when (val algorithmStringRepresentation = getString(name)) {
        EncryptionAlgorithm.Symmetric.AES256GCM.stringRepresentation -> EncryptionAlgorithm.Symmetric.AES256GCM
        EncryptionAlgorithm.Asymmetric.RSA2048OAEP.stringRepresentation -> EncryptionAlgorithm.Asymmetric.RSA2048OAEP
        else -> throw JSONException("The EncryptionAlgorithm string representation '$algorithmStringRepresentation' could not be found!")
    }
}
