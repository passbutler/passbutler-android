package de.sicherheitskritisch.passbutler.common

import android.arch.persistence.room.TypeConverter
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.security.SecureRandom

object Crypto {

    fun deriveMasterKeyFromPassword(password: String, salt: String): List<Byte> {
        return listOf()
    }

    fun generateSymmetricKey(): List<Byte> {
        return listOf()
    }

    suspend fun generateRandomBytes(count: Int): List<Byte> {
        return withContext(Dispatchers.IO) {
            val blockingSecureRandomInstance = SecureRandom.getInstance("NativePRNGBlocking")

            ByteArray(count).also {
                blockingSecureRandomInstance.nextBytes(it)
            }.asList()
        }
    }
}

class ProtectedValue<T : JSONSerializable> private constructor(
    private var initializationVector: ByteArray,
    private val algorithm: Algorithm,
    private var encryptedValue: ByteArray
) : JSONSerializable {

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            put(SERIALIZATION_KEY_INITIALIZATION_VECTOR, initializationVector)
            put(SERIALIZATION_KEY_ALGORITHM, algorithm.stringRepresentation)
            put(SERIALIZATION_KEY_ENCRYPTED_VALUE, encryptedValue)
        }
    }

    fun decrypt(encryptionKey: ByteArray, instantiationDelegate: (JSONObject) -> T?): T? {
        val decryptedBytes = algorithm.decrypt(initializationVector, encryptionKey, encryptedValue)

        return try {
            decryptedBytes?.let {
                val jsonSerializedString = String(it)
                val jsonObject = JSONObject(jsonSerializedString)
                instantiationDelegate(jsonObject)
            } ?: throw DecryptionFailedException()
        } catch (e: JSONException) {
            L.w("ProtectedValue", "The deserialization of the given value failed!", e)
            null
        } catch (e: DecryptionFailedException) {
            L.w("ProtectedValue", "The decryption of the given value failed!")
            null
        }
    }

    fun update(encryptionKey: ByteArray, updatedValue: T) {
        val newInitializationVector = algorithm.generateInitializationVector()
        val valueAsByteArray = updatedValue.toByteArray()

        algorithm.encrypt(newInitializationVector, encryptionKey, valueAsByteArray)?.let { encryptedValue ->
            initializationVector = newInitializationVector
            this.encryptedValue = encryptedValue
        } ?: run {
            L.w("ProtectedValue", "The value could not be updated because encryption failed!")
        }
    }

    private class DecryptionFailedException : Exception()

    companion object {
        const val SERIALIZATION_KEY_INITIALIZATION_VECTOR = "initializationVector"
        const val SERIALIZATION_KEY_ALGORITHM = "algorithm"
        const val SERIALIZATION_KEY_ENCRYPTED_VALUE = "encryptedValue"

        fun <T : JSONSerializable> deserialize(jsonObject: JSONObject): ProtectedValue<T>? {
            return try {
                ProtectedValue(
                    jsonObject.getString(SERIALIZATION_KEY_INITIALIZATION_VECTOR).toByteArray(),
                    jsonObject.getString(SERIALIZATION_KEY_ALGORITHM)?.let { Algorithm.valueOf(it) } ?: throw JSONException("The algorithm could not be deserialized!"),
                    jsonObject.getString(SERIALIZATION_KEY_ENCRYPTED_VALUE).toByteArray()
                )
            } catch (e: JSONException) {
                L.w("ProtectedValue", "The ProtectedValue object could not be deserialized using the following JSON: $jsonObject", e)
                null
            }
        }

        fun <T : JSONSerializable> create(encryptionKey: ByteArray, initialValue: T): ProtectedValue<T>? {
            val algorithm: Algorithm = Algorithm.AES256GCM

            val newInitializationVector = algorithm.generateInitializationVector()
            val valueAsByteArray = initialValue.toByteArray()

            return algorithm.encrypt(newInitializationVector, encryptionKey, valueAsByteArray)?.let { encryptedValue ->
                ProtectedValue<T>(newInitializationVector, algorithm, encryptedValue)
            } ?: run {
                L.w("ProtectedValue", "The ProtectedValue could not be created because value encryption failed!")
                null
            }
        }
    }
}

private fun <T : JSONSerializable> T.toByteArray(): ByteArray {
    val valueAsJsonSerializedString = this.serialize().toString()
    return valueAsJsonSerializedString.toByteArray()
}

sealed class Algorithm(val stringRepresentation: String) {

    abstract fun generateInitializationVector(): ByteArray
    abstract fun encrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray?
    abstract fun decrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray?

    object AES256GCM : Algorithm("AES-256-GCM") {
        override fun generateInitializationVector(): ByteArray {
            // TODO: Implement
            return ByteArray(0)
        }

        override fun encrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray? {
            // TODO: Implement
            return ByteArray(0)
        }

        override fun decrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray? {
            // TODO: Implement
            return ByteArray(0)
        }
    }

    companion object {
        fun valueOf(stringRepresentation: String): Algorithm? {
            return when (stringRepresentation) {
                Algorithm.AES256GCM.stringRepresentation -> Algorithm.AES256GCM
                else -> null
            }
        }
    }
}

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
 * Clear out a byte array for security reasons (for crypto keys etc.)
 */
fun ByteArray.clear() {
    this.forEachIndexed { index, _ ->
        this[index] = 0
    }
}