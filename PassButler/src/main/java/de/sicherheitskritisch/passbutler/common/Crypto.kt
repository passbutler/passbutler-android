package de.sicherheitskritisch.passbutler.common

import android.arch.persistence.room.TypeConverter
import de.sicherheitskritisch.passbutler.models.UserSettings
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

class ProtectedData<T : JSONSerializable>(
    val initializationVector: Array<Byte>,
    val algorithmInformation: String,
    val encryptedData: List<Byte>
): JSONSerializable {

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            put(SERIALIZATION_KEY_INITIALIZATION_VECTOR, initializationVector)
            put(SERIALIZATION_KEY_ALGORITHM_INFORMATION, algorithmInformation)
            put(SERIALIZATION_KEY_ENCRYPTED_DATA, encryptedData)
        }
    }

    fun decryptValue(key: Array<Byte>, instantiationDelegate: (JSONObject) -> T?): T? {
        val decryptedJsonSerializedString = decrypt(key, encryptedData)
        return decryptedJsonSerializedString?.let { instantiationDelegate(it) }
    }

    fun cloneWithUpdatedValue(key: Array<Byte>, newValue: T): ProtectedData<T> {
        val newInitializationVector = generateNewInitializationVector()
        val jsonSerializedString = newValue.serialize()
        val newEncryptedData = encrypt(newInitializationVector, key, jsonSerializedString)

        return ProtectedData(
            initializationVector = newInitializationVector,
            algorithmInformation = algorithmInformation,
            encryptedData = newEncryptedData
        )
    }

    private fun encrypt(newInitializationVector: Array<Byte>, key: Array<Byte>, jsonSerializedString: JSONObject): List<Byte> {
        // TODO: Implement
        return emptyList()
    }

    private fun decrypt(key: Array<Byte>, encryptedData: List<Byte>): JSONObject? {
        // TODO: Implement
        return null
    }

    private fun generateNewInitializationVector(): Array<Byte> {
        // TODO: Implement
        return emptyArray()
    }

    companion object {
        const val SERIALIZATION_KEY_INITIALIZATION_VECTOR = "initializationVector"
        const val SERIALIZATION_KEY_ALGORITHM_INFORMATION = "algorithmInformation"
        const val SERIALIZATION_KEY_ENCRYPTED_DATA = "encryptedData"

        fun <T : JSONSerializable> deserialize(jsonObject: JSONObject): ProtectedData<T>? {
            return try {
                ProtectedData(
                    initializationVector = jsonObject.getString(SERIALIZATION_KEY_INITIALIZATION_VECTOR).toByteArray().toTypedArray(),
                    algorithmInformation = jsonObject.getString(SERIALIZATION_KEY_ALGORITHM_INFORMATION),
                    encryptedData = jsonObject.getString(SERIALIZATION_KEY_ENCRYPTED_DATA).toByteArray().toList()
                )
            } catch (e: JSONException) {
                L.w("ProtectedData", "The ProtectedData object could not be deserialized using the following JSON: $jsonObject", e)
                null
            }
        }
    }
}

class ProtectedDataConverters {
    @TypeConverter
    fun protectedDataToString(protectedData: ProtectedData<*>?): String? {
        return protectedData?.serialize()?.toString()
    }

    @TypeConverter
    fun stringToProtectedDataWithUserSettings(serializedProtectedData: String?): ProtectedData<UserSettings>? {
        return serializedProtectedData?.let {
            ProtectedData.deserialize(JSONObject(it))
        }
    }
}