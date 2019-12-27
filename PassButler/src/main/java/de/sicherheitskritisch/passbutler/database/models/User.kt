package de.sicherheitskritisch.passbutler.database.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import de.sicherheitskritisch.passbutler.base.JSONSerializable
import de.sicherheitskritisch.passbutler.base.JSONSerializableDeserializer
import de.sicherheitskritisch.passbutler.base.getDate
import de.sicherheitskritisch.passbutler.base.getJSONSerializable
import de.sicherheitskritisch.passbutler.base.getJSONSerializableOrNull
import de.sicherheitskritisch.passbutler.base.getStringOrNull
import de.sicherheitskritisch.passbutler.base.putBoolean
import de.sicherheitskritisch.passbutler.base.putDate
import de.sicherheitskritisch.passbutler.base.putJSONSerializable
import de.sicherheitskritisch.passbutler.base.putString
import de.sicherheitskritisch.passbutler.crypto.models.CryptographicKey
import de.sicherheitskritisch.passbutler.crypto.models.KeyDerivationInformation
import de.sicherheitskritisch.passbutler.crypto.models.ProtectedValue
import de.sicherheitskritisch.passbutler.crypto.models.getProtectedValue
import de.sicherheitskritisch.passbutler.crypto.models.getProtectedValueOrNull
import de.sicherheitskritisch.passbutler.crypto.models.putProtectedValue
import de.sicherheitskritisch.passbutler.database.Synchronizable
import org.json.JSONException
import org.json.JSONObject
import java.util.*

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val username: String,
    val masterPasswordAuthenticationHash: String?,
    val masterKeyDerivationInformation: KeyDerivationInformation?,
    val masterEncryptionKey: ProtectedValue<CryptographicKey>?,
    val itemEncryptionPublicKey: CryptographicKey,
    val itemEncryptionSecretKey: ProtectedValue<CryptographicKey>?,
    val settings: ProtectedValue<UserSettings>?,
    override var deleted: Boolean,
    override var modified: Date,
    override val created: Date
) : Synchronizable, JSONSerializable {

    @Ignore
    override val primaryField = username

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putString(SERIALIZATION_KEY_USERNAME, username)
            putString(SERIALIZATION_KEY_MASTER_PASSWORD_AUTHENTICATION_HASH, masterPasswordAuthenticationHash)
            putJSONSerializable(SERIALIZATION_KEY_MASTER_KEY_DERIVATION_INFORMATION, masterKeyDerivationInformation)
            putProtectedValue(SERIALIZATION_KEY_MASTER_ENCRYPTION_KEY, masterEncryptionKey)
            putJSONSerializable(SERIALIZATION_KEY_ITEM_ENCRYPTION_PUBLIC_KEY, itemEncryptionPublicKey)
            putProtectedValue(SERIALIZATION_KEY_ITEM_ENCRYPTION_SECRET_KEY, itemEncryptionSecretKey)
            putProtectedValue(SERIALIZATION_KEY_SETTINGS, settings)
            putBoolean(SERIALIZATION_KEY_DELETED, deleted)
            putDate(SERIALIZATION_KEY_MODIFIED, modified)
            putDate(SERIALIZATION_KEY_CREATED, created)
        }
    }

    /**
     * Deserialize a `User` with all fields (used for own user details).
     */
    object DefaultUserDeserializer : JSONSerializableDeserializer<User>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): User {
            return User(
                username = jsonObject.getString(SERIALIZATION_KEY_USERNAME),
                masterPasswordAuthenticationHash = jsonObject.getString(SERIALIZATION_KEY_MASTER_PASSWORD_AUTHENTICATION_HASH),
                masterKeyDerivationInformation = jsonObject.getJSONSerializable(SERIALIZATION_KEY_MASTER_KEY_DERIVATION_INFORMATION, KeyDerivationInformation.Deserializer),
                masterEncryptionKey = jsonObject.getProtectedValue(SERIALIZATION_KEY_MASTER_ENCRYPTION_KEY),
                itemEncryptionPublicKey = jsonObject.getJSONSerializable(SERIALIZATION_KEY_ITEM_ENCRYPTION_PUBLIC_KEY, CryptographicKey.Deserializer),
                itemEncryptionSecretKey = jsonObject.getProtectedValue(SERIALIZATION_KEY_ITEM_ENCRYPTION_SECRET_KEY),
                settings = jsonObject.getProtectedValue(SERIALIZATION_KEY_SETTINGS),
                deleted = jsonObject.getBoolean(SERIALIZATION_KEY_DELETED),
                modified = jsonObject.getDate(SERIALIZATION_KEY_MODIFIED),
                created = jsonObject.getDate(SERIALIZATION_KEY_CREATED)
            )
        }
    }

    /**
     * Deserialize a `User` without private fields (used for other users).
     */
    object PartialUserDeserializer : JSONSerializableDeserializer<User>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): User {
            return User(
                username = jsonObject.getString(SERIALIZATION_KEY_USERNAME),
                masterPasswordAuthenticationHash = jsonObject.getStringOrNull(SERIALIZATION_KEY_MASTER_PASSWORD_AUTHENTICATION_HASH),
                masterKeyDerivationInformation = jsonObject.getJSONSerializableOrNull(SERIALIZATION_KEY_MASTER_KEY_DERIVATION_INFORMATION, KeyDerivationInformation.Deserializer),
                masterEncryptionKey = jsonObject.getProtectedValueOrNull(SERIALIZATION_KEY_MASTER_ENCRYPTION_KEY),
                itemEncryptionPublicKey = jsonObject.getJSONSerializable(SERIALIZATION_KEY_ITEM_ENCRYPTION_PUBLIC_KEY, CryptographicKey.Deserializer),
                itemEncryptionSecretKey = jsonObject.getProtectedValueOrNull(SERIALIZATION_KEY_ITEM_ENCRYPTION_SECRET_KEY),
                settings = jsonObject.getProtectedValueOrNull(SERIALIZATION_KEY_SETTINGS),
                deleted = jsonObject.getBoolean(SERIALIZATION_KEY_DELETED),
                modified = jsonObject.getDate(SERIALIZATION_KEY_MODIFIED),
                created = jsonObject.getDate(SERIALIZATION_KEY_CREATED)
            )
        }
    }

    companion object {
        private const val SERIALIZATION_KEY_USERNAME = "username"
        private const val SERIALIZATION_KEY_MASTER_PASSWORD_AUTHENTICATION_HASH = "masterPasswordAuthenticationHash"
        private const val SERIALIZATION_KEY_MASTER_KEY_DERIVATION_INFORMATION = "masterKeyDerivationInformation"
        private const val SERIALIZATION_KEY_MASTER_ENCRYPTION_KEY = "masterEncryptionKey"
        private const val SERIALIZATION_KEY_ITEM_ENCRYPTION_PUBLIC_KEY = "itemEncryptionPublicKey"
        private const val SERIALIZATION_KEY_ITEM_ENCRYPTION_SECRET_KEY = "itemEncryptionSecretKey"
        private const val SERIALIZATION_KEY_SETTINGS = "settings"
        private const val SERIALIZATION_KEY_DELETED = "deleted"
        private const val SERIALIZATION_KEY_MODIFIED = "modified"
        private const val SERIALIZATION_KEY_CREATED = "created"
    }
}