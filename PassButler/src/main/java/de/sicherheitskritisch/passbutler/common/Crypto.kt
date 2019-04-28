package de.sicherheitskritisch.passbutler.common

import android.arch.persistence.room.TypeConverter
import de.sicherheitskritisch.passbutler.database.models.UserSettings
import org.json.JSONException
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

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
        val decryptedBytes = algorithm.decrypt(initializationVector, encryptionKey, encryptedValue)

        return try {
            decryptedBytes?.let {
                val jsonSerializedString = it.toUTF8String()
                val jsonObject = JSONObject(jsonSerializedString)
                instantiationDelegate(jsonObject)
            } ?: throw DecryptionFailedException()
        } catch (e: JSONException) {
            L.w("ProtectedValue", "decrypt(): The value could not be deserialized!", e)
            null
        } catch (e: DecryptionFailedException) {
            L.w("ProtectedValue", "decrypt(): The value could not be decrypted!")
            null
        }
    }

    fun update(encryptionKey: ByteArray, updatedValue: T) {
        val newInitializationVector = algorithm.generateInitializationVector()
        val valueAsByteArray = updatedValue.toByteArray()

        algorithm.encrypt(newInitializationVector, encryptionKey, valueAsByteArray)?.let { encryptedValue ->
            // Update values only if encryption was successful
            this.initializationVector = newInitializationVector
            this.encryptedValue = encryptedValue
        } ?: run {
            L.w("ProtectedValue", "update(): The value could not be updated because encryption failed!")
        }
    }

    // TODO: Put in `Algorithm` and let throw in `Algorithm` child classes
    private class DecryptionFailedException : Exception()

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
            val algorithm: Algorithm = Algorithm.AES256GCM

            val newInitializationVector = algorithm.generateInitializationVector()
            val valueAsByteArray = initialValue.toByteArray()

            return algorithm.encrypt(newInitializationVector, encryptionKey, valueAsByteArray)?.let { encryptedValue ->
                ProtectedValue<T>(newInitializationVector, algorithm, encryptedValue)
            } ?: run {
                L.w("ProtectedValue", "create(): The value could not be created because encryption failed!")
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

        private const val AES_KEY_LENGTH = 256
        private const val GCM_INITIALIZATION_VECTOR_LENGTH = 96
        private const val GCM_AUTHENTICATION_TAG_LENGTH = 128

        override fun generateInitializationVector(): ByteArray {
            /*
            return withContext(Dispatchers.IO) {
                val blockingSecureRandomInstance = SecureRandom.getInstanceStrong()
                val bytesCount = GCM_INITIALIZATION_VECTOR_LENGTH / 8

                ByteArray(bytesCount).also {
                    blockingSecureRandomInstance.nextBytes(it)
                }
            }
            */

            // TODO: Implement
            return ByteArray(0)
        }

        override fun encrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray? {
            return try {
                if (initializationVector.size * 8 != GCM_INITIALIZATION_VECTOR_LENGTH) {
                    throw IllegalArgumentException("The initialization vector must be 96 bits long!")
                }

                if (encryptionKey.size * 8 != AES_KEY_LENGTH) {
                    throw IllegalArgumentException("The encryption key must be 256 bits long!")
                }

                val secretKey = SecretKeySpec(encryptionKey, "AES")
                val gcmParameterSpec = GCMParameterSpec(GCM_AUTHENTICATION_TAG_LENGTH, initializationVector)

                // The GCM is no classic block mode and thus has no padding
                val encryptCipherInstance = Cipher.getInstance("AES/GCM/NoPadding")
                encryptCipherInstance.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec)

                val encryptedData = encryptCipherInstance.doFinal(data)

                encryptedData
            } catch (e: Exception) {
                L.w("Algorithm.AES256GCM", "encrypt(): The value could not be encrypted!", e)
                null
            }
        }

        override fun decrypt(initializationVector: ByteArray, encryptionKey: ByteArray, data: ByteArray): ByteArray? {
            // TODO: Implement
            return ByteArray(0)
        }
    }
}

private fun String.toAlgorithm(): Algorithm? {
    return when (this) {
        Algorithm.AES256GCM.stringRepresentation -> Algorithm.AES256GCM
        else -> null
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

@Throws(JSONException::class)
fun JSONObject.putProtectedValue(name: String, value: ProtectedValue<*>): JSONObject {
    return putJSONObject(name, value.serialize())
}

fun <T : JSONSerializable> JSONObject.getProtectedValue(name: String): ProtectedValue<T>? {
    return ProtectedValue.deserialize(getJSONObject(name))
}


/**
 * Converts the `ByteArray` to `String` with UTF-8 charset (basically what the `String()` constructor does but in explicit way)
 */
fun ByteArray.toUTF8String(): String {
    return toString(Charsets.UTF_8)
}

/**
 * Clear out a byte array for security reasons (for crypto keys etc.)
 */
fun ByteArray.clear() {
    this.forEachIndexed { index, _ ->
        this[index] = 0
    }
}